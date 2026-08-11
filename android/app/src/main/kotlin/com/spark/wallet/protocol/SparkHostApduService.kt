package com.spark.wallet.protocol

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.spark.wallet.data.AppDatabase
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.CertificateValidationResult
import com.spark.wallet.engine.LocalTransactionEngine
import com.spark.wallet.engine.ReceiveOutcome
import com.spark.wallet.security.KeyStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

/**
 * HostApduService subclass executing the Payee (Card Emulation) side of the SPARK Tap Handshake.
 */
class SparkHostApduService : HostApduService() {

    companion object {
        private const val TAG = "SparkHostApduService"

        private val _paymentReceivedEvents = MutableSharedFlow<ReceiveOutcome>(extraBufferCapacity = 10)
        val paymentReceivedEvents: SharedFlow<ReceiveOutcome> = _paymentReceivedEvents.asSharedFlow()

        // Injectable components for testing and production runtime
        var customCertificateStore: CertificateStore? = null
        var customTransactionEngine: LocalTransactionEngine? = null
        var customKeyStoreManager: KeyStoreManager? = null
        var customKeyAlias: String = "spark_device_signing_key"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Handshake Session State
    private var sessionEphemeralKeyPair: EphemeralX25519KeyPair? = null
    private var sessionPayeeChallenge: ByteArray? = null
    private var sessionDerivedAesKey: SecretKeySpec? = null
    private var sessionPayerDeviceId: String? = null

    private val certificateStore: CertificateStore by lazy {
        customCertificateStore ?: CertificateStore(applicationContext)
    }

    private val keyStoreManager: KeyStoreManager by lazy {
        customKeyStoreManager ?: KeyStoreManager(applicationContext)
    }

    private val transactionEngine: LocalTransactionEngine by lazy {
        customTransactionEngine ?: run {
            val db = AppDatabase.getDatabase(applicationContext)
            LocalTransactionEngine(
                database = db,
                certificateStore = certificateStore,
                keyStoreManager = keyStoreManager,
                keyAlias = customKeyAlias
            )
        }
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "Received APDU length: ${commandApdu.size}")
        if (commandApdu.isEmpty()) return SparkApduProtocol.SW_UNKNOWN_ERROR

        return try {
            if (SparkApduProtocol.isSelectSparkAid(commandApdu)) {
                handleSelectAid()
            } else {
                when (commandApdu[1]) {
                    SparkApduProtocol.INS_EXCHANGE_AUTH -> handleExchangeAuth(commandApdu)
                    SparkApduProtocol.INS_TRANSFER_TX -> handleTransferTx(commandApdu)
                    else -> SparkApduProtocol.SW_INVALID_INS
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing APDU", e)
            SparkApduProtocol.SW_UNKNOWN_ERROR
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "APDU session deactivated. Reason: $reason")
        resetSession()
    }

    private fun handleSelectAid(): ByteArray {
        val myCertPem = certificateStore.getDeviceCertificatePem()
            ?: return SparkApduProtocol.SW_APP_NOT_FOUND

        // 1. Generate ephemeral X25519 keypair and random challenge
        val ephemeralKeyPair = SessionCrypto.generateEphemeralKeyPair()
        sessionEphemeralKeyPair = ephemeralKeyPair

        val challenge = ByteArray(32)
        SecureRandom().nextBytes(challenge)
        sessionPayeeChallenge = challenge

        val responseDto = SparkApduProtocol.SelectAidResponse(
            certPem = myCertPem,
            ephemeralX25519PubBase64Url = ephemeralKeyPair.publicKeyBase64Url,
            challengeBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
        )

        val jsonString = SparkApduProtocol.json.encodeToString(
            SparkApduProtocol.SelectAidResponse.serializer(),
            responseDto
        )
        return SparkApduProtocol.wrapResponse(jsonString.toByteArray(Charsets.UTF_8))
    }

    private fun handleExchangeAuth(apdu: ByteArray): ByteArray {
        val currentEphemeral = sessionEphemeralKeyPair ?: return SparkApduProtocol.SW_AUTH_FAILED
        val myChallenge = sessionPayeeChallenge ?: return SparkApduProtocol.SW_AUTH_FAILED

        // Extract payload JSON
        val payloadBytes = SparkApduProtocol.extractPayload(apdu)
        val jsonString = String(payloadBytes, Charsets.UTF_8)
        val request = SparkApduProtocol.json.decodeFromString(
            SparkApduProtocol.AuthExchangeRequest.serializer(),
            jsonString
        )

        // 1. Verify Payer Certificate offline against Bank Root CA
        val certVerification = certificateStore.verifyCertificateOffline(request.certPem)
        val payerCert = when (certVerification) {
            is CertificateValidationResult.Valid -> certVerification.certificate
            is CertificateValidationResult.Invalid -> {
                Log.e(TAG, "Payer certificate validation failed: ${certVerification.reason}")
                return SparkApduProtocol.SW_AUTH_FAILED
            }
        }
        sessionPayerDeviceId = payerCert.deviceId

        // 2. Verify Payer's Ed25519 signature over Payee's challenge
        val payerSigBytes = Base64.getUrlDecoder().decode(request.challengeSignatureBase64Url)
        val isPayerSigValid = verifyEd25519(
            publicKeyBase64Url = payerCert.devicePublicKey,
            data = myChallenge,
            signatureBytes = payerSigBytes
        )
        if (!isPayerSigValid) {
            Log.e(TAG, "Payer signature over challenge verification failed")
            return SparkApduProtocol.SW_AUTH_FAILED
        }

        // 3. X25519 ECDH Shared Secret & AES-256 Key Derivation
        val payerRawX25519Pub = Base64.getUrlDecoder().decode(request.ephemeralX25519PubBase64Url)
        val derivedAesKey = SessionCrypto.deriveAesSessionKey(
            myPrivateKey = currentEphemeral.keyPair.private,
            peerRawPublicKey = payerRawX25519Pub
        )
        sessionDerivedAesKey = derivedAesKey

        // 4. Sign Payer's challenge with Payee's StrongBox Ed25519 key
        val payerChallengeBytes = Base64.getUrlDecoder().decode(request.challengeBase64Url)
        val payeeSigBytes = keyStoreManager.sign(customKeyAlias, payerChallengeBytes)
        val payeeSigBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(payeeSigBytes)

        val responseDto = SparkApduProtocol.AuthExchangeResponse(
            challengeSignatureBase64Url = payeeSigBase64Url,
            status = "AUTHENTICATED"
        )
        val respJson = SparkApduProtocol.json.encodeToString(
            SparkApduProtocol.AuthExchangeResponse.serializer(),
            responseDto
        )
        return SparkApduProtocol.wrapResponse(respJson.toByteArray(Charsets.UTF_8))
    }

    private fun handleTransferTx(apdu: ByteArray): ByteArray {
        val aesKey = sessionDerivedAesKey ?: return SparkApduProtocol.SW_AUTH_FAILED

        val payloadBytes = SparkApduProtocol.extractPayload(apdu)
        val jsonString = String(payloadBytes, Charsets.UTF_8)
        val request = SparkApduProtocol.json.decodeFromString(
            SparkApduProtocol.EncryptedTransactionPayload.serializer(),
            jsonString
        )

        // 1. Decrypt transaction payload with AES-256-GCM
        val encryptedBytes = Base64.getUrlDecoder().decode(request.encryptedBlobBase64Url)
        val decryptedBytes = SessionCrypto.decryptAesGcm(aesKey, encryptedBytes)
        val txJsonString = String(decryptedBytes, Charsets.UTF_8)

        val transaction = TransactionBuilder.deserialize(txJsonString)

        // 2. Validate and record payment in local transaction engine
        val receiveResult = runBlocking {
            transactionEngine.receiveTransaction(transaction)
        }

        return if (receiveResult.isSuccess) {
            val outcome = receiveResult.getOrThrow()
            serviceScope.launch {
                _paymentReceivedEvents.emit(outcome)
            }
            val responseDto = SparkApduProtocol.TransferResponse(
                status = "ACCEPTED",
                txHash = outcome.transactionHash
            )
            val respJson = SparkApduProtocol.json.encodeToString(
                SparkApduProtocol.TransferResponse.serializer(),
                responseDto
            )
            SparkApduProtocol.wrapResponse(respJson.toByteArray(Charsets.UTF_8))
        } else {
            val errorMsg = receiveResult.exceptionOrNull()?.message ?: "Transaction rejected"
            val responseDto = SparkApduProtocol.TransferResponse(
                status = "REJECTED",
                error = errorMsg
            )
            val respJson = SparkApduProtocol.json.encodeToString(
                SparkApduProtocol.TransferResponse.serializer(),
                responseDto
            )
            SparkApduProtocol.wrapResponse(respJson.toByteArray(Charsets.UTF_8), SparkApduProtocol.SW_AUTH_FAILED)
        }
    }

    private fun resetSession() {
        sessionEphemeralKeyPair = null
        sessionPayeeChallenge = null
        sessionDerivedAesKey = null
        sessionPayerDeviceId = null
    }

    private fun verifyEd25519(publicKeyBase64Url: String, data: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val rawPubKeyBytes = Base64.getUrlDecoder().decode(publicKeyBase64Url)
            val derHeader = byteArrayOf(
                0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
            )
            val spec = X509EncodedKeySpec(derHeader + rawPubKeyBytes)
            val kf = try { KeyFactory.getInstance("Ed25519") } catch (_: Exception) { KeyFactory.getInstance("EdDSA") }
            val pub = kf.generatePublic(spec)
            val verifier = try { Signature.getInstance("Ed25519") } catch (_: Exception) { Signature.getInstance("EdDSA") }
            verifier.initVerify(pub)
            verifier.update(data)
            verifier.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }
}
