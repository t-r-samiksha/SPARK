package com.spark.wallet.data

import com.spark.wallet.data.entity.CachedCert
import com.spark.wallet.data.entity.CachedTrust
import com.spark.wallet.data.entity.LocalLedgerEntry
import com.spark.wallet.data.entity.LocalPurse
import com.spark.wallet.data.entity.PendingRelay
import com.spark.wallet.security.DatabaseKeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class DatabaseDaoTest {

    @Test
    fun testLocalPurseEntityRoundTrip() {
        val tokenId = UUID.randomUUID().toString()
        val purse = LocalPurse(
            tokenId = tokenId,
            cap = 500000L, // 5000 rupees
            remaining = 350000L, // 3500 rupees
            counterCurrent = 4L,
            signedTokenBlob = "-----BEGIN SPARK PURSE TOKEN-----\nMOCK_TOKEN_BLOB\n-----END SPARK PURSE TOKEN-----",
            expiresAt = System.currentTimeMillis() + 86400000L
        )

        // Verify entity fields
        assertEquals(tokenId, purse.tokenId)
        assertEquals(500000L, purse.cap)
        assertEquals(350000L, purse.remaining)
        assertEquals(4L, purse.counterCurrent)
        assertTrue(purse.signedTokenBlob.contains("SPARK PURSE TOKEN"))
        assertTrue(purse.expiresAt > 0)

        // Verify state mutation / copy
        val updatedPurse = purse.copy(
            remaining = 250000L,
            counterCurrent = 5L
        )
        assertEquals(250000L, updatedPurse.remaining)
        assertEquals(5L, updatedPurse.counterCurrent)
    }

    @Test
    fun testLocalLedgerEntryEntityRoundTrip() {
        val txId = UUID.randomUUID().toString()
        val counterpartyId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis() / 1000

        val entry = LocalLedgerEntry(
            txId = txId,
            direction = "out",
            counterpartyId = counterpartyId,
            amount = 25000L,
            counter = 1L,
            prevHash = null,
            hash = "sampleSha256HashBase64Url",
            signature = "sampleSignatureBase64Url",
            timestamp = timestamp,
            synced = false
        )

        assertEquals(txId, entry.txId)
        assertEquals("out", entry.direction)
        assertEquals(counterpartyId, entry.counterpartyId)
        assertEquals(25000L, entry.amount)
        assertEquals(1L, entry.counter)
        assertNull(entry.prevHash)
        assertFalse(entry.synced)

        val syncedEntry = entry.copy(synced = true)
        assertTrue(syncedEntry.synced)
    }

    @Test
    fun testCachedCertEntityRoundTrip() {
        val deviceId = UUID.randomUUID().toString()
        val pubKey = "luMvVjfGi9lT4_L0t1lrAeKzda6qfdamp4v_yQzpBVE"
        val certBlob = "-----BEGIN SPARK DEVICE CERTIFICATE-----\n...\n-----END SPARK DEVICE CERTIFICATE-----"
        val expiresAt = System.currentTimeMillis() + 31536000000L

        val cert = CachedCert(
            deviceId = deviceId,
            publicKey = pubKey,
            certBlob = certBlob,
            expiresAt = expiresAt
        )

        assertEquals(deviceId, cert.deviceId)
        assertEquals(pubKey, cert.publicKey)
        assertEquals(certBlob, cert.certBlob)
        assertEquals(expiresAt, cert.expiresAt)
    }

    @Test
    fun testCachedTrustEntityRoundTrip() {
        val subjectId = UUID.randomUUID().toString()
        val attestationBlobs = """["blob1","blob2"]"""
        val cachedAt = System.currentTimeMillis()

        val trust = CachedTrust(
            subjectId = subjectId,
            trustScore = 0.95,
            attestationBlobs = attestationBlobs,
            cachedAt = cachedAt
        )

        assertEquals(subjectId, trust.subjectId)
        assertEquals(0.95, trust.trustScore, 0.001)
        assertEquals(attestationBlobs, trust.attestationBlobs)
        assertEquals(cachedAt, trust.cachedAt)
    }

    @Test
    fun testPendingRelayEntityRoundTrip() {
        val txId = UUID.randomUUID().toString()
        val blob = """{"tx_id":"$txId","amount":"25000"}"""
        val ttl = System.currentTimeMillis() + 3600000L
        val receivedAt = System.currentTimeMillis()

        val relay = PendingRelay(
            txId = txId,
            blob = blob,
            destinationHint = "DEV_DESTINATION_HINT_1",
            ttl = ttl,
            receivedAt = receivedAt
        )

        assertEquals(txId, relay.txId)
        assertEquals(blob, relay.blob)
        assertEquals("DEV_DESTINATION_HINT_1", relay.destinationHint)
        assertEquals(ttl, relay.ttl)
        assertEquals(receivedAt, relay.receivedAt)
    }

    @Test
    fun testDatabaseKeyManagerKeystorePassphraseSealingAndUnsealing() {
        val keyManager = DatabaseKeyManager()

        // 1. Generate 32-byte cryptographically secure random passphrase
        val rawPassphrase = ByteArray(32)
        SecureRandom().nextBytes(rawPassphrase)

        // 2. Seal passphrase with Keystore AES-256-GCM master key
        val sealedBlob = keyManager.sealPassphrase(rawPassphrase)
        assertNotNull("Sealed blob should not be null", sealedBlob)
        assertTrue("Sealed blob should not be empty", sealedBlob.isNotEmpty())

        // 3. Unseal passphrase and assert exact byte equality
        val unsealedPassphrase = keyManager.unsealPassphrase(sealedBlob)
        assertArrayEquals("Unsealed passphrase must match original bytes", rawPassphrase, unsealedPassphrase)

        // 4. Test getOrCreateDatabasePassphrase persistence
        val initialPassphrase = keyManager.getOrCreateDatabasePassphrase()
        assertEquals(32, initialPassphrase.size)

        val retrievedPassphrase = keyManager.getOrCreateDatabasePassphrase()
        assertArrayEquals("Subsequent calls must return identical unsealed passphrase", initialPassphrase, retrievedPassphrase)

        keyManager.clearPassphrase()
    }

    @Test
    fun testAppDatabaseMigrationStub() {
        val migration = AppDatabase.MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
    }

    @Test
    fun testDatabaseKeyManagerInvalidBlobThrows() {
        val keyManager = DatabaseKeyManager()
        try {
            keyManager.unsealPassphrase("invalid-short-base64")
            fail("Expected exception for malformed sealed blob")
        } catch (e: Exception) {
            assertTrue(e is IllegalArgumentException || e.message != null)
        }
    }
}
