package com.spark.wallet.protocol

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.CertificateValidationResult
import com.spark.wallet.engine.LocalTransactionEngine
import com.spark.wallet.security.KeyStoreManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Serializable
data class QrPayeeInvoice(
    @SerialName("type") val type: String = "SPARK_QR_INVOICE",
    @SerialName("cert") val certPem: String,
    @SerialName("ephemeral_pub") val ephemeralX25519PubBase64Url: String,
    @SerialName("challenge") val challengeBase64Url: String,
    @SerialName("amount") val amountPaise: String
)

@Serializable
data class QrPayerPayload(
    @SerialName("type") val type: String = "SPARK_QR_PAYLOAD",
    @SerialName("cert") val certPem: String,
    @SerialName("ephemeral_pub") val ephemeralX25519PubBase64Url: String,
    @SerialName("challenge_sig") val challengeSignatureBase64Url: String,
    @SerialName("encrypted_tx") val encryptedTxBase64Url: String
)

/**
 * QR Fallback Transport utilizing ZXing for optical offline payment exchange.
 */
object QrTransportManager {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Generates an invoice QR code for the Payee requesting a specific amount.
     */
    fun createPayeeInvoice(
        myCertPem: String,
        amountPaise: Long,
        ephemeralKeyPair: EphemeralX25519KeyPair,
        challenge: ByteArray
    ): String {
        val invoice = QrPayeeInvoice(
            certPem = myCertPem,
            ephemeralX25519PubBase64Url = ephemeralKeyPair.publicKeyBase64Url,
            challengeBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
            amountPaise = amountPaise.toString()
        )
        return json.encodeToString(QrPayeeInvoice.serializer(), invoice)
    }

    /**
     * Payer processes the scanned invoice QR, builds/signs the transaction, encrypts it, and returns the response QR payload.
     */
    suspend fun processInvoiceAndCreatePayment(
        invoiceJson: String,
        certificateStore: CertificateStore,
        keyStoreManager: KeyStoreManager,
        transactionEngine: LocalTransactionEngine,
        keyAlias: String = "spark_device_signing_key"
    ): Result<Pair<String, SparkTransaction>> = runCatching {
        val invoice = json.decodeFromString(QrPayeeInvoice.serializer(), invoiceJson)
        val amountPaise = invoice.amountPaise.toLong()

        // 1. Verify Payee Cert
        val payeeCertValidation = certificateStore.verifyCertificateOffline(invoice.certPem)
        val payeeCert = when (payeeCertValidation) {
            is CertificateValidationResult.Valid -> payeeCertValidation.certificate
            is CertificateValidationResult.Invalid -> throw Exception("Payee cert invalid: ${payeeCertValidation.reason}")
        }

        // 2. Mutual Auth & X25519 ECDH AES-256 Key Derivation
        val myEphemeralKeyPair = SessionCrypto.generateEphemeralKeyPair()
        val payeeRawPub = Base64.getUrlDecoder().decode(invoice.ephemeralX25519PubBase64Url)
        val derivedAesKey = SessionCrypto.deriveAesSessionKey(myEphemeralKeyPair.keyPair.private, payeeRawPub)

        val payeeChallengeBytes = Base64.getUrlDecoder().decode(invoice.challengeBase64Url)
        val payerSigBytes = keyStoreManager.sign(keyAlias, payeeChallengeBytes)
        val payerSigBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(payerSigBytes)

        // 3. Build, Sign, and Encrypt Transaction
        val payeeParty = Party(
            deviceId = payeeCert.deviceId,
            accountId = payeeCert.accountId,
            cert = invoice.certPem
        )
        val signedTx = transactionEngine.buildTransaction(amountPaise, payeeParty).getOrThrow()
        val txJson = TransactionBuilder.serialize(signedTx)
        val encryptedBytes = SessionCrypto.encryptAesGcm(derivedAesKey, txJson.toByteArray(Charsets.UTF_8))
        val encryptedBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(encryptedBytes)

        val myCertPem = certificateStore.getDeviceCertificatePem()
            ?: throw Exception("Payer cert missing")

        val paymentPayload = QrPayerPayload(
            certPem = myCertPem,
            ephemeralX25519PubBase64Url = myEphemeralKeyPair.publicKeyBase64Url,
            challengeSignatureBase64Url = payerSigBase64Url,
            encryptedTxBase64Url = encryptedBase64Url
        )

        Pair(json.encodeToString(QrPayerPayload.serializer(), paymentPayload), signedTx)
    }

    /**
     * Generates an Android Bitmap for a given QR string payload using ZXing.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
