package com.spark.wallet.data

import com.spark.wallet.protocol.CanonicalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.security.PublicKey
import java.util.Base64

/**
 * SPARK device certificate data model representing the signed binding between
 * a device's Ed25519 public key, device_id, and account_id.
 *
 * See docs/certificate-format.md.
 */
@Serializable
data class SparkCertificate(
    @SerialName("device_id") val deviceId: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("device_public_key") val devicePublicKey: String,
    @SerialName("serial_number") val serialNumber: String,
    @SerialName("not_before") val notBefore: String,
    @SerialName("not_after") val notAfter: String,
    @SerialName("signature") val signature: String
) {
    /**
     * Produces the canonical UTF-8 bytes of this certificate for signature verification
     * (excluding the signature field itself).
     */
    fun toCanonicalSigningBytes(): ByteArray {
        val jsonObject = Json.encodeToJsonElement(this).jsonObject
        return CanonicalSerializer.canonicalBytes(jsonObject, excludeSignature = true)
    }

    /**
     * Wraps this certificate into a standard RFC 7468 PEM container.
     */
    fun toPem(): String {
        val jsonObject = Json.encodeToJsonElement(this).jsonObject
        val canonicalFull = CanonicalSerializer.serializeCanonical(jsonObject, excludeSignature = false)
        return CertificateFormat.wrapPem(CertificateFormat.DEVICE_CERT_LABEL, canonicalFull.toByteArray(Charsets.UTF_8))
    }
}

/**
 * Encoding and parsing utilities for SPARK PEM envelopes and Ed25519 keys.
 */
object CertificateFormat {

    const val DEVICE_CERT_LABEL = "SPARK DEVICE CERTIFICATE"
    const val ROOT_CA_CERT_LABEL = "SPARK ROOT CA CERTIFICATE"
    const val TRUST_ATTESTATION_LABEL = "SPARK TRUST ATTESTATION"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Extracts raw 32-byte Ed25519 public key and encodes it as base64url without padding (43 chars).
     */
    fun encodePublicKeyRawBase64Url(publicKey: PublicKey): String {
        val encoded = publicKey.encoded ?: throw IllegalArgumentException("Public key cannot be encoded")
        val rawBytes = when {
            encoded.size == 44 -> encoded.copyOfRange(12, 44) // Standard X.509 SubjectPublicKeyInfo header (12 bytes)
            encoded.size == 32 -> encoded
            encoded.size > 32 -> encoded.takeLast(32).toByteArray()
            else -> throw IllegalArgumentException("Unexpected Ed25519 public key encoding length: ${encoded.size}")
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)
    }

    /**
     * Wraps payload bytes into standard PEM envelope (RFC 7468) with 64-char lines.
     */
    fun wrapPem(label: String, payloadBytes: ByteArray): String {
        val base64 = Base64.getEncoder().encodeToString(payloadBytes)
        val wrappedLines = base64.chunked(64).joinToString("\n")
        return "-----BEGIN $label-----\n$wrappedLines\n-----END $label-----"
    }

    /**
     * Unwraps a PEM envelope and returns the decoded payload bytes.
     */
    fun unwrapPem(label: String, pem: String): ByteArray {
        val beginMarker = "-----BEGIN $label-----"
        val endMarker = "-----END $label-----"

        val startIndex = pem.indexOf(beginMarker)
        val endIndex = pem.indexOf(endMarker)

        if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) {
            throw IllegalArgumentException("Invalid PEM container for label: $label")
        }

        val base64Content = pem.substring(startIndex + beginMarker.length, endIndex)
            .replace("\r", "")
            .replace("\n", "")
            .trim()

        return Base64.getDecoder().decode(base64Content)
    }

    /**
     * Parses a SPARK DEVICE CERTIFICATE PEM into a [SparkCertificate] object.
     */
    fun parseDeviceCertificate(pem: String): SparkCertificate {
        val utf8Bytes = unwrapPem(DEVICE_CERT_LABEL, pem)
        val jsonString = String(utf8Bytes, Charsets.UTF_8)
        return json.decodeFromString(SparkCertificate.serializer(), jsonString)
    }
}
