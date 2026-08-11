package com.spark.wallet.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalSerializerTest {

    @Test
    fun testCanonicalSerializationMatchesDocVector() {
        val rawJsonString = """
            {
                "device_id": "x",
                "amount": "25000",
                "signature": "some-dummy-sig-to-exclude"
            }
        """.trimIndent()

        val jsonObject = Json.parseToJsonElement(rawJsonString).jsonObject
        val canonicalOutput = CanonicalSerializer.serializeCanonical(jsonObject, excludeSignature = true)

        // Expected format per docs/canonical-serialization.md:
        // {"amount":"25000","device_id":"x"}
        assertEquals("""{"amount":"25000","device_id":"x"}""", canonicalOutput)
    }

    @Test
    fun testNestedCanonicalSerialization() {
        val rawJsonString = """
            {
                "z": 1,
                "a": {
                    "beta": "2",
                    "alpha": "1"
                }
            }
        """.trimIndent()

        val jsonObject = Json.parseToJsonElement(rawJsonString).jsonObject
        val canonicalOutput = CanonicalSerializer.serializeCanonical(jsonObject, excludeSignature = false)

        assertEquals("""{"a":{"alpha":"1","beta":"2"},"z":1}""", canonicalOutput)
    }
}
