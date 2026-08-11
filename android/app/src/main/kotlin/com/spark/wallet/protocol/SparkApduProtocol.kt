package com.spark.wallet.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * APDU Constants and Data Models for SPARK Tap Handshake over IsoDep Host Card Emulation.
 */
object SparkApduProtocol {

    // SPARK AID: "F0 53 50 41 52 4B 01" (F0 S P A R K 01)
    val SPARK_AID = byteArrayOf(
        0xF0.toByte(), 0x53.toByte(), 0x50.toByte(), 0x41.toByte(), 0x52.toByte(), 0x4B.toByte(), 0x01.toByte()
    )

    // ISO 7816-4 Status Words
    val SW_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
    val SW_APP_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    val SW_AUTH_FAILED = byteArrayOf(0x69.toByte(), 0x82.toByte())
    val SW_INVALID_INS = byteArrayOf(0x6D.toByte(), 0x00.toByte())
    val SW_UNKNOWN_ERROR = byteArrayOf(0x6F.toByte(), 0x00.toByte())

    // Instruction bytes
    const val INS_SELECT: Byte = 0xA4.toByte()
    const val INS_EXCHANGE_AUTH: Byte = 0x10.toByte()
    const val INS_TRANSFER_TX: Byte = 0x20.toByte()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Payee Initial Response on SELECT AID:
     * Returns payee certificate PEM, payee ephemeral X25519 public key, and random auth challenge.
     */
    @Serializable
    data class SelectAidResponse(
        @SerialName("cert") val certPem: String,
        @SerialName("ephemeral_pub") val ephemeralX25519PubBase64Url: String,
        @SerialName("challenge") val challengeBase64Url: String
    )

    /**
     * Payer Request for Mutual Authentication & Key Exchange (INS 0x10):
     * Sends payer certificate, payer ephemeral X25519 public key, Ed25519 signature over payee's challenge,
     * and payer's random auth challenge.
     */
    @Serializable
    data class AuthExchangeRequest(
        @SerialName("cert") val certPem: String,
        @SerialName("ephemeral_pub") val ephemeralX25519PubBase64Url: String,
        @SerialName("challenge_sig") val challengeSignatureBase64Url: String,
        @SerialName("challenge") val challengeBase64Url: String
    )

    /**
     * Payee Response for Mutual Authentication (INS 0x10):
     * Returns Ed25519 signature over payer's challenge.
     */
    @Serializable
    data class AuthExchangeResponse(
        @SerialName("challenge_sig") val challengeSignatureBase64Url: String,
        @SerialName("status") val status: String = "AUTHENTICATED"
    )

    /**
     * Payer Request for Encrypted Transaction Transfer (INS 0x20):
     * Contains AES-256-GCM encrypted transaction payload (IV + Ciphertext + Tag) in base64url.
     */
    @Serializable
    data class EncryptedTransactionPayload(
        @SerialName("encrypted_blob") val encryptedBlobBase64Url: String
    )

    /**
     * Payee Response for Transaction Transfer:
     */
    @Serializable
    data class TransferResponse(
        @SerialName("status") val status: String, // "ACCEPTED" or "REJECTED"
        @SerialName("tx_hash") val txHash: String? = null,
        @SerialName("error") val error: String? = null
    )

    fun buildApdu(ins: Byte, payload: ByteArray): ByteArray {
        val lcHigh = ((payload.size shr 8) and 0xFF).toByte()
        val lcLow = (payload.size and 0xFF).toByte()
        return byteArrayOf(0x80.toByte(), ins, 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), lcHigh, lcLow) +
                payload
    }

    fun extractPayload(apdu: ByteArray): ByteArray {
        if (apdu.size < 5) return ByteArray(0)
        return if (apdu[4] == 0x00.toByte() && apdu.size >= 7) {
            val lc = ((apdu[5].toInt() and 0xFF) shl 8) or (apdu[6].toInt() and 0xFF)
            val start = 7
            val end = Math.min(apdu.size, start + lc)
            apdu.copyOfRange(start, end)
        } else {
            val lc = apdu[4].toInt() and 0xFF
            val start = 5
            val end = Math.min(apdu.size, start + lc)
            apdu.copyOfRange(start, end)
        }
    }

    fun wrapResponse(payloadBytes: ByteArray, statusWord: ByteArray = SW_SUCCESS): ByteArray {
        val result = ByteArray(payloadBytes.size + 2)
        System.arraycopy(payloadBytes, 0, result, 0, payloadBytes.size)
        result[result.size - 2] = statusWord[0]
        result[result.size - 1] = statusWord[1]
        return result
    }

    fun isSelectSparkAid(apdu: ByteArray): Boolean {
        if (apdu.size < 11) return false
        if (apdu[0] != 0x00.toByte() || apdu[1] != INS_SELECT || apdu[2] != 0x04.toByte() || apdu[3] != 0x00.toByte()) {
            return false
        }
        val aidLen = apdu[4].toInt() and 0xFF
        if (aidLen != SPARK_AID.size || apdu.size < 5 + aidLen) return false
        for (i in SPARK_AID.indices) {
            if (apdu[5 + i] != SPARK_AID[i]) return false
        }
        return true
    }
}
