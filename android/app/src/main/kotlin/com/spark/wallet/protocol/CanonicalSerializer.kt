package com.spark.wallet.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.text.Normalizer
import java.util.Base64

typealias Transaction = SparkTransaction

/**
 * Implements deterministic Canonical JSON Serialization matching the backend specification.
 *
 * Rules:
 * 1. Object keys sorted in strict lexicographical (byte-wise UTF-8) order recursively.
 * 2. Compact JSON output without whitespace.
 * 3. Amounts serialized as decimal strings in paise (e.g. "25000").
 * 4. Certificate fields preserve full multi-line PEM formatting.
 * 5. Top-level `signature` field excluded from pre-signature bytes.
 * 6. Signature format: raw 64-byte Ed25519 signature encoded in base64url WITHOUT padding.
 * 7. Canonical string encoded to UTF-8 bytes before signing/verifying.
 */
object CanonicalSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val utf8ByteComparator = Comparator<String> { a, b ->
        val bytesA = a.toByteArray(Charsets.UTF_8)
        val bytesB = b.toByteArray(Charsets.UTF_8)
        val minLen = minOf(bytesA.size, bytesB.size)
        for (i in 0 until minLen) {
            val byteA = bytesA[i].toInt() and 0xFF
            val byteB = bytesB[i].toInt() and 0xFF
            if (byteA != byteB) {
                return@Comparator byteA - byteB
            }
        }
        bytesA.size - bytesB.size
    }

    /**
     * Produces the pre-signature canonical UTF-8 bytes for a [SparkTransaction] (excluding `signature`).
     */
    fun canonicalize(tx: SparkTransaction): ByteArray {
        return canonicalize(tx, excludeSignature = true)
    }

    /**
     * Produces canonical UTF-8 bytes for a [SparkTransaction], optionally excluding or including `signature`.
     */
    fun canonicalize(tx: SparkTransaction, excludeSignature: Boolean): ByteArray {
        val jsonObject = json.encodeToJsonElement(SparkTransaction.serializer(), tx).jsonObject
        return canonicalize(jsonObject, excludeSignature)
    }

    /**
     * Produces pre-signature canonical bytes (excluding `signature`).
     */
    fun canonicalizeForSigning(tx: SparkTransaction): ByteArray {
        return canonicalize(tx, excludeSignature = true)
    }

    /**
     * Produces full canonical bytes of a completed signed transaction (including `signature`).
     */
    fun canonicalizeFull(tx: SparkTransaction): ByteArray {
        return canonicalize(tx, excludeSignature = false)
    }

    /**
     * Computes the chaining hash for a transaction.
     * Defined as: SHA-256 of canonical JSON INCLUDING signature, base64url-encoded without padding.
     */
    fun computeTransactionHash(tx: SparkTransaction): String {
        val fullBytes = canonicalizeFull(tx)
        return computeTransactionHash(fullBytes)
    }

    /**
     * Computes SHA-256 hash of raw bytes, base64url-encoded without padding.
     */
    fun computeTransactionHash(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Produces the pre-signature canonical UTF-8 bytes for any [JsonObject].
     */
    fun canonicalize(jsonObject: JsonObject, excludeSignature: Boolean = true): ByteArray {
        return serializeCanonical(jsonObject, excludeSignature).toByteArray(Charsets.UTF_8)
    }

    /**
     * Alias for canonicalize(jsonObject, excludeSignature).
     */
    fun canonicalBytes(jsonObject: JsonObject, excludeSignature: Boolean = true): ByteArray {
        return canonicalize(jsonObject, excludeSignature)
    }

    /**
     * Serializes a [JsonObject] into a compact, sorted canonical JSON string.
     */
    fun serializeCanonical(jsonObject: JsonObject, excludeSignature: Boolean = true): String {
        val filteredMap = mutableMapOf<String, JsonElement>()

        for ((key, value) in jsonObject) {
            if (excludeSignature && key == "signature") continue
            filteredMap[key] = value
        }

        val sortedKeys = filteredMap.keys.sortedWith(utf8ByteComparator)
        val sb = StringBuilder()
        sb.append("{")
        sortedKeys.forEachIndexed { index, key ->
            if (index > 0) sb.append(",")
            sb.append(quoteAndEscape(Normalizer.normalize(key, Normalizer.Form.NFC)))
            sb.append(":")
            sb.append(canonicalizeValue(filteredMap[key]!!))
        }
        sb.append("}")
        return sb.toString()
    }

    /**
     * Serializes any [JsonElement] value recursively.
     */
    fun canonicalizeValue(element: JsonElement): String {
        return when (element) {
            is JsonNull -> "null"
            is JsonPrimitive -> {
                if (element.isString) {
                    val normalized = Normalizer.normalize(element.content, Normalizer.Form.NFC)
                    quoteAndEscape(normalized)
                } else {
                    element.content
                }
            }
            is JsonArray -> {
                val sb = StringBuilder()
                sb.append("[")
                element.forEachIndexed { index, item ->
                    if (index > 0) sb.append(",")
                    sb.append(canonicalizeValue(item))
                }
                sb.append("]")
                sb.toString()
            }
            is JsonObject -> {
                val sortedKeys = element.keys.sortedWith(utf8ByteComparator)
                val sb = StringBuilder()
                sb.append("{")
                sortedKeys.forEachIndexed { index, key ->
                    if (index > 0) sb.append(",")
                    sb.append(quoteAndEscape(Normalizer.normalize(key, Normalizer.Form.NFC)))
                    sb.append(":")
                    sb.append(canonicalizeValue(element[key]!!))
                }
                sb.append("}")
                sb.toString()
            }
        }
    }

    /**
     * Signs pre-signature bytes with an Ed25519 private key.
     * Returns raw 64-byte Ed25519 signature base64url-encoded WITHOUT padding.
     */
    fun sign(bytes: ByteArray, privateKey: PrivateKey): String {
        val signature = try {
            Signature.getInstance("Ed25519")
        } catch (e: Exception) {
            Signature.getInstance("EdDSA")
        }
        signature.initSign(privateKey)
        signature.update(bytes)
        val sigBytes = signature.sign()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes)
    }

    /**
     * Derives an Ed25519 PrivateKey from a 32-byte raw seed.
     */
    fun derivePrivateKeyFromSeed(seed32Bytes: ByteArray): PrivateKey {
        require(seed32Bytes.size == 32) { "Seed must be exactly 32 bytes, got ${seed32Bytes.size}" }
        // PKCS#8 prefix for Ed25519 (RFC 8410)
        val pkcs8Header = byteArrayOf(
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06,
            0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
        )
        val spec = PKCS8EncodedKeySpec(pkcs8Header + seed32Bytes)
        val kf = try {
            KeyFactory.getInstance("Ed25519")
        } catch (e: Exception) {
            KeyFactory.getInstance("EdDSA")
        }
        return kf.generatePrivate(spec)
    }

    /**
     * Derives an Ed25519 PrivateKey from an unpadded base64url 32-byte seed.
     */
    fun derivePrivateKeyFromBase64UrlSeed(base64UrlSeed: String): PrivateKey {
        val seedBytes = Base64.getUrlDecoder().decode(base64UrlSeed)
        return derivePrivateKeyFromSeed(seedBytes)
    }

    private fun quoteAndEscape(input: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (char in input) {
            when (char) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        sb.append(String.format("\\u%04x", char.code))
                    } else {
                        sb.append(char)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
