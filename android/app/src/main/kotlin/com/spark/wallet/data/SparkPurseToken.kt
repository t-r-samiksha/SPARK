package com.spark.wallet.data

import com.spark.wallet.protocol.CanonicalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Data model for a signed SPARK Purse Token per docs/purse-token-format.md.
 */
@Serializable
data class SparkPurseToken(
    @SerialName("device_id") val deviceId: String,
    @SerialName("value") val value: String,
    @SerialName("cap") val cap: String,
    @SerialName("counter_start") val counterStart: Long,
    @SerialName("expiry") val expiry: Long, // Unix epoch seconds
    @SerialName("token_id") val tokenId: String,
    @SerialName("signature") val signature: String
) {
    fun toCanonicalSigningBytes(): ByteArray {
        val jsonObject = Json.encodeToJsonElement(serializer(), this).jsonObject
        return CanonicalSerializer.canonicalize(jsonObject, excludeSignature = true)
    }
}

object PurseTokenFormat {
    const val PURSE_TOKEN_LABEL = "SPARK PURSE TOKEN"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses a SPARK PURSE TOKEN PEM into a [SparkPurseToken].
     */
    fun parsePurseToken(pem: String): SparkPurseToken {
        val utf8Bytes = CertificateFormat.unwrapPem(PURSE_TOKEN_LABEL, pem)
        val jsonString = String(utf8Bytes, Charsets.UTF_8)
        return json.decodeFromString(SparkPurseToken.serializer(), jsonString)
    }

    /**
     * Serializes a [SparkPurseToken] into an RFC 7468 PEM container.
     */
    fun wrapPurseToken(token: SparkPurseToken): String {
        val jsonObject = json.encodeToJsonElement(SparkPurseToken.serializer(), token).jsonObject
        val fullCanonicalBytes = CanonicalSerializer.canonicalize(jsonObject, excludeSignature = false)
        return CertificateFormat.wrapPem(PURSE_TOKEN_LABEL, fullCanonicalBytes)
    }
}
