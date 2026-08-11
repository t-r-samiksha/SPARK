package com.spark.wallet.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer

/**
 * Implements deterministic Canonical JSON Serialization according to SPARK specification.
 * - Keys sorted lexicographically at all nesting levels.
 * - Compact whitespace (no spaces/newlines).
 * - Unicode NFC normalization for string values.
 * - Excludes `signature` field when preparing data for signing/verification.
 */
object CanonicalSerializer {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Serializes a JSON object canonically, stripping the `signature` field if requested.
     */
    fun serializeCanonical(jsonObject: JsonObject, excludeSignature: Boolean = true): String {
        val canonicalMap = mutableMapOf<String, JsonElement>()

        for ((key, value) in jsonObject) {
            if (excludeSignature && key == "signature") continue
            canonicalMap[key] = normalizeElement(value)
        }

        val sortedKeys = canonicalMap.keys.sorted()
        val sb = StringBuilder()
        sb.append("{")
        sortedKeys.forEachIndexed { index, key ->
            if (index > 0) sb.append(",")
            sb.append("\"").append(escapeJson(Normalizer.normalize(key, Normalizer.Form.NFC))).append("\":")
            sb.append(stringifyCanonical(canonicalMap[key]!!))
        }
        sb.append("}")
        return sb.toString()
    }

    /**
     * Converts a canonical JSON string into UTF-8 bytes for signing/verifying.
     */
    fun canonicalBytes(jsonObject: JsonObject, excludeSignature: Boolean = true): ByteArray {
        return serializeCanonical(jsonObject, excludeSignature).toByteArray(Charsets.UTF_8)
    }

    private fun normalizeElement(element: JsonElement): JsonElement {
        return when (element) {
            is JsonObject -> {
                val normalizedMap = element.mapValues { normalizeElement(it.value) }
                JsonObject(normalizedMap)
            }
            is JsonArray -> {
                JsonArray(element.map { normalizeElement(it) })
            }
            is JsonPrimitive -> {
                if (element.isString) {
                    JsonPrimitive(Normalizer.normalize(element.content, Normalizer.Form.NFC))
                } else {
                    element
                }
            }
            is JsonNull -> JsonNull
        }
    }

    private fun stringifyCanonical(element: JsonElement): String {
        return when (element) {
            is JsonObject -> {
                val sortedKeys = element.keys.sorted()
                val sb = StringBuilder()
                sb.append("{")
                sortedKeys.forEachIndexed { index, key ->
                    if (index > 0) sb.append(",")
                    sb.append("\"").append(escapeJson(Normalizer.normalize(key, Normalizer.Form.NFC))).append("\":")
                    sb.append(stringifyCanonical(element[key]!!))
                }
                sb.append("}")
                sb.toString()
            }
            is JsonArray -> {
                val sb = StringBuilder()
                sb.append("[")
                element.forEachIndexed { index, child ->
                    if (index > 0) sb.append(",")
                    sb.append(stringifyCanonical(child))
                }
                sb.append("]")
                sb.toString()
            }
            is JsonPrimitive -> {
                if (element.isString) {
                    "\"${escapeJson(element.content)}\""
                } else {
                    element.content
                }
            }
            is JsonNull -> "null"
        }
    }

    private fun escapeJson(input: String): String {
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
