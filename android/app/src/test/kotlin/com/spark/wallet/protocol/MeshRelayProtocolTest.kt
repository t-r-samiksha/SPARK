package com.spark.wallet.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshRelayProtocolTest {

    @Test
    fun `isSelectSparkAid identifies correct APDU`() {
        val apdu = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), 0x07.toByte()) +
                SparkApduProtocol.SPARK_AID + byteArrayOf(0x00.toByte())
        assertTrue(SparkApduProtocol.isSelectSparkAid(apdu))
    }

    @Test
    fun `buildApdu correctly formats INS_RELAY_TX`() {
        val payload = "test_payload".toByteArray(Charsets.UTF_8)
        val apdu = SparkApduProtocol.buildApdu(SparkApduProtocol.INS_RELAY_TX, payload)
        
        assertEquals(0x80.toByte(), apdu[0])
        assertEquals(SparkApduProtocol.INS_RELAY_TX, apdu[1])
        assertEquals(0x00.toByte(), apdu[2])
        assertEquals(0x00.toByte(), apdu[3])
        assertEquals(0x00.toByte(), apdu[4])
        // Size is 12 bytes
        assertEquals(0x00.toByte(), apdu[5]) // lcHigh
        assertEquals(0x0C.toByte(), apdu[6]) // lcLow
        
        val extracted = SparkApduProtocol.extractPayload(apdu)
        assertArrayEquals(payload, extracted)
    }

    @Test
    fun `EncryptedRelayPayload serializes correctly`() {
        val payload = SparkApduProtocol.EncryptedRelayPayload(
            encryptedBlobBase64Url = "base64==",
            ttl = 3
        )
        val json = SparkApduProtocol.json.encodeToString(
            SparkApduProtocol.EncryptedRelayPayload.serializer(),
            payload
        )
        assertTrue(json.contains("base64=="))
        assertTrue(json.contains("ttl"))
        
        val decoded = SparkApduProtocol.json.decodeFromString(
            SparkApduProtocol.EncryptedRelayPayload.serializer(),
            json
        )
        assertEquals(payload.encryptedBlobBase64Url, decoded.encryptedBlobBase64Url)
        assertEquals(payload.ttl, decoded.ttl)
    }
}
