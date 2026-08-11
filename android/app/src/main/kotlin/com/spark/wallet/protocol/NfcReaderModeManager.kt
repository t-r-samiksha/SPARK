package com.spark.wallet.protocol

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.CertificateValidationResult
import com.spark.wallet.engine.LocalTransactionEngine
import com.spark.wallet.security.KeyStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

sealed class TapPaymentResult {
    data class Success(val transaction: SparkTransaction, val transactionHash: String) : TapPaymentResult()
    data class Failure(val reason: String) : TapPaymentResult()
}

/**
 * Manages NFC Reader Mode on the Payer device to execute the 4-step APDU handshake with the Payee.
 */
class NfcReaderModeManager(
    private val activity: Activity,
    private val certificateStore: CertificateStore,
    private val keyStoreManager: KeyStoreManager,
    private val transactionEngine: LocalTransactionEngine,
    private val keyAlias: String = "spark_device_signing_key"
) : NfcAdapter.ReaderCallback {

    companion object {
        private const val TAG = "NfcReaderModeManager"
        const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    private val _paymentState = MutableStateFlow<TapHandshakeState>(TapHandshakeState.Idle)
    val paymentState: StateFlow<TapHandshakeState> = _paymentState.asStateFlow()

    @Volatile
    private var targetAmountPaise: Long = 0L

    fun startTapToPay(amountPaise: Long) {
        targetAmountPaise = amountPaise
        _paymentState.value = TapHandshakeState.Scanning
        nfcAdapter?.enableReaderMode(activity, this, READER_FLAGS, null)
    }

    fun stopTapToPay() {
        nfcAdapter?.disableReaderMode(activity)
        _paymentState.value = TapHandshakeState.Idle
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return
        val isoDep = IsoDep.get(tag) ?: run {
            Log.e(TAG, "Discovered tag does not support IsoDep")
            return
        }

        scope.launch {
            try {
                _paymentState.value = TapHandshakeState.Connected("NFC_PEER")
                isoDep.connect()
                isoDep.timeout = 5000

                val result = executeApduHandshake(isoDep, targetAmountPaise)
                when (result) {
                    is TapPaymentResult.Success -> {
                        _paymentState.value = TapHandshakeState.PaymentTransferred(result.transaction.txId)
                    }
                    is TapPaymentResult.Failure -> {
                        _paymentState.value = TapHandshakeState.Error(result.reason)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "NFC Handshake exception", e)
                _paymentState.value = TapHandshakeState.Error(e.message ?: "NFC Transceive Error")
            } finally {
                try {
                    isoDep.close()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Executes the complete 4-step APDU handshake over an active IsoDep transceiver.
     */
    suspend fun executeApduHandshake(isoDep: IsoDep, amountPaise: Long): TapPaymentResult {
        return executeApduHandshakeTransceiver(
            transceiver = { apdu -> isoDep.transceive(apdu) },
            amountPaise = amountPaise
        )
    }

    /**
     * Transport-agnostic APDU transceiver runner (useful for both IsoDep and simulated tests).
     */
    suspend fun executeApduHandshakeTransceiver(
        transceiver: suspend (ByteArray) -> ByteArray,
        amountPaise: Long
    ): TapPaymentResult {
        // STEP 1: SELECT AID -> Receive Payee Cert, Ephemeral PubKey, Challenge
        val selectApdu = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), 0x07.toByte()) +
                SparkApduProtocol.SPARK_AID + byteArrayOf(0x00.toByte())
        val selectRespBytes = transceiver(selectApdu)

        if (selectRespBytes.size < 2 || !isStatusOk(selectRespBytes)) {
            return TapPaymentResult.Failure("SELECT AID failed: ${toHex(selectRespBytes)}")
        }

        val selectPayload = selectRespBytes.copyOfRange(0, selectRespBytes.size - 2)
        val selectResp = SparkApduProtocol.json.decodeFromString(
            SparkApduProtocol.SelectAidResponse.serializer(),
            String(selectPayload, Charsets.UTF_8)
        )

        // Validate Payee Certificate offline against Bank Root CA
        val payeeCertValidation = certificateStore.verifyCertificateOffline(selectResp.certPem)
        val payeeCert = when (payeeCertValidation) {
            is CertificateValidationResult.Valid -> payeeCertValidation.certificate
            is CertificateValidationResult.Invalid -> return TapPaymentResult.Failure("Payee cert invalid: ${payeeCertValidation.reason}")
        }

        // STEP 2: Mutual Auth & X25519 ECDH Session Key Derivation
        val myEphemeralKeyPair = SessionCrypto.generateEphemeralKeyPair()
        val payeeRawX25519Pub = Base64.getUrlDecoder().decode(selectResp.ephemeralX25519PubBase64Url)
        val derivedAesKey = SessionCrypto.deriveAesSessionKey(
            myPrivateKey = myEphemeralKeyPair.keyPair.private,
            peerRawPublicKey = payeeRawX25519Pub
        )

        // Sign Payee Challenge with Payer's StrongBox Ed25519 key
        val payeeChallengeBytes = Base64.getUrlDecoder().decode(selectResp.challengeBase64Url)
        val payerSigBytes = keyStoreManager.sign(keyAlias, payeeChallengeBytes)
        val payerSigBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(payerSigBytes)

        val payerChallenge = ByteArray(32)
        SecureRandom().nextBytes(payerChallenge)
        val payerChallengeBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(payerChallenge)

        val myCertPem = certificateStore.getDeviceCertificatePem()
            ?: return TapPaymentResult.Failure("Payer device certificate not found")

        val authReq = SparkApduProtocol.AuthExchangeRequest(
            certPem = myCertPem,
            ephemeralX25519PubBase64Url = myEphemeralKeyPair.publicKeyBase64Url,
            challengeSignatureBase64Url = payerSigBase64Url,
            challengeBase64Url = payerChallengeBase64Url
        )
        val authReqJson = SparkApduProtocol.json.encodeToString(
            SparkApduProtocol.AuthExchangeRequest.serializer(),
            authReq
        ).toByteArray(Charsets.UTF_8)

        val authApdu = SparkApduProtocol.buildApdu(SparkApduProtocol.INS_EXCHANGE_AUTH, authReqJson)
        val authRespBytes = transceiver(authApdu)

        if (authRespBytes.size < 2 || !isStatusOk(authRespBytes)) {
            return TapPaymentResult.Failure("Mutual auth failed: ${toHex(authRespBytes)}")
        }

        val authRespPayload = authRespBytes.copyOfRange(0, authRespBytes.size - 2)
        val authResp = SparkApduProtocol.json.decodeFromString(
            SparkApduProtocol.AuthExchangeResponse.serializer(),
            String(authRespPayload, Charsets.UTF_8)
        )

        // Verify Payee's signature over Payer's challenge
        val payeeSigBytes = Base64.getUrlDecoder().decode(authResp.challengeSignatureBase64Url)
        val isPayeeSigValid = verifyEd25519(
            publicKeyBase64Url = payeeCert.devicePublicKey,
            data = payerChallenge,
            signatureBytes = payeeSigBytes
        )
        if (!isPayeeSigValid) {
            return TapPaymentResult.Failure("Payee mutual auth signature invalid")
        }

        // STEP 3: Build, Sign, and AES-256-GCM Encrypt Payment Transaction
        val payeeParty = Party(
            deviceId = payeeCert.deviceId,
            accountId = payeeCert.accountId,
            cert = selectResp.certPem
        )

        val buildResult = transactionEngine.buildTransaction(
            amountPaise = amountPaise,
            payee = payeeParty
        )
        if (buildResult.isFailure) {
            return TapPaymentResult.Failure("Failed to build transaction: ${buildResult.exceptionOrNull()?.message}")
        }
        val signedTx = buildResult.getOrThrow()
        val txJson = TransactionBuilder.serialize(signedTx)

        val encryptedPayload = SessionCrypto.encryptAesGcm(derivedAesKey, txJson.toByteArray(Charsets.UTF_8))
        val encryptedBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(encryptedPayload)

        val transferReq = SparkApduProtocol.EncryptedTransactionPayload(encryptedBlobBase64Url = encryptedBase64Url)
        val transferReqJson = SparkApduProtocol.json.encodeToString(
            SparkApduProtocol.EncryptedTransactionPayload.serializer(),
            transferReq
        ).toByteArray(Charsets.UTF_8)

        val transferApdu = SparkApduProtocol.buildApdu(SparkApduProtocol.INS_TRANSFER_TX, transferReqJson)
        val transferRespBytes = transceiver(transferApdu)

        if (transferRespBytes.size < 2 || !isStatusOk(transferRespBytes)) {
            return TapPaymentResult.Failure("Transaction transfer failed: ${toHex(transferRespBytes)}")
        }

        val transferRespPayload = transferRespBytes.copyOfRange(0, transferRespBytes.size - 2)
        val transferResp = SparkApduProtocol.json.decodeFromString(
            SparkApduProtocol.TransferResponse.serializer(),
            String(transferRespPayload, Charsets.UTF_8)
        )

        return if (transferResp.status == "ACCEPTED") {
            TapPaymentResult.Success(signedTx, transferResp.txHash ?: CanonicalSerializer.computeTransactionHash(signedTx))
        } else {
            TapPaymentResult.Failure("Transaction rejected by payee: ${transferResp.error}")
        }
    }

    private fun isStatusOk(resp: ByteArray): Boolean {
        val sw1 = resp[resp.size - 2]
        val sw2 = resp[resp.size - 1]
        return sw1 == 0x90.toByte() && sw2 == 0x00.toByte()
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }

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
