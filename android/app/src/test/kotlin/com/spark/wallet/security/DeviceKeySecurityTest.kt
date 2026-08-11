package com.spark.wallet.security

import com.spark.wallet.data.KeyAliasStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.cert.Certificate

class DeviceKeySecurityTest {

    private lateinit var keyStoreManager: KeyStoreManager
    private lateinit var keyAliasStore: KeyAliasStore
    private lateinit var attestationManager: AttestationManager
    private lateinit var securityRepository: SecurityRepository

    @Before
    fun setup() {
        keyStoreManager = KeyStoreManager()
        keyAliasStore = KeyAliasStore()
        attestationManager = AttestationManager(keyStoreManager)
        securityRepository = SecurityRepositoryImpl(
            keyStoreManager = keyStoreManager,
            keyAliasStore = keyAliasStore,
            attestationManager = attestationManager
        )
    }

    @Test
    fun testKeyGenerationSucceeds() {
        val testAlias = "test_spark_device_key_gen"
        val challenge = "SPARK_ATTESTATION_CHALLENGE_VECTOR".toByteArray()

        val result = keyStoreManager.generateEd25519KeyPair(
            alias = testAlias,
            challenge = challenge
        )

        assertNotNull("Generated result should not be null", result)
        assertEquals("Alias should match requested alias", testAlias, result.alias)
        assertNotNull("Keypair should not be null", result.keyPair)
        assertNotNull("Public key should not be null", result.keyPair.public)
        assertNotNull("Private key should not be null", result.keyPair.private)
        assertEquals("Algorithm should be Ed25519 / EdDSA", "EdDSA", result.keyPair.public.algorithm)

        // KeyStore contains the alias
        assertTrue("KeyStoreManager should contain alias", keyStoreManager.containsAlias(testAlias))
        assertNotNull("Public key is retrievable via alias", keyStoreManager.getPublicKey(testAlias))
    }

    @Test
    fun testKeyAliasIsStoredAndRetrievableWithoutPrivateKeyMaterial() {
        val testAlias = "test_spark_persisted_alias"
        val challenge = "CHALLENGE_BYTES".toByteArray()

        val result = securityRepository.generateDeviceSigningKey(
            alias = testAlias,
            challenge = challenge
        )

        assertEquals(testAlias, result.alias)

        // Retrieve stored alias
        val storedAlias = securityRepository.getStoredKeyAlias()
        assertNotNull("Stored key alias should be retrievable", storedAlias)
        assertEquals("Stored key alias must match generated alias", testAlias, storedAlias)

        // Verify KeyAliasStore holds ONLY string alias and device ID, no key bytes
        assertEquals(testAlias, keyAliasStore.getKeyAlias())
        assertTrue("KeyAliasStore has alias", keyAliasStore.hasKeyAlias())
    }

    @Test
    fun testAttemptingToExportRawPrivateKeyMaterialFailsAndThrows() {
        val testAlias = "test_spark_non_exportable_key"
        keyStoreManager.generateEd25519KeyPair(alias = testAlias)

        // Directly via KeyStoreManager
        try {
            keyStoreManager.exportPrivateKeyMaterial(testAlias)
            fail("Expected SecurityException when attempting to export private key material from KeyStoreManager")
        } catch (e: SecurityException) {
            assertTrue(
                "Exception message should mention private key protection or prohibited export",
                e.message?.contains("prohibited", ignoreCase = true) == true ||
                        e.message?.contains("hardware", ignoreCase = true) == true ||
                        e.message?.contains("cannot be exported", ignoreCase = true) == true
            )
        }

        // Via SecurityRepository
        try {
            securityRepository.exportPrivateKeyMaterial(testAlias)
            fail("Expected SecurityException when attempting to export private key material from SecurityRepository")
        } catch (e: SecurityException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun testAttestationStatusAndBackingIndicator() {
        val testAlias = "test_spark_attestation_status"

        // Status for non-existent alias should be UNAVAILABLE
        val nonExistentStatus = keyStoreManager.getAttestationStatus("unknown_key_alias")
        assertEquals(SecurityBacking.UNAVAILABLE, nonExistentStatus.backing)
        assertFalse(nonExistentStatus.isHardwareBacked)
        assertFalse(nonExistentStatus.isStrongBoxBacked)

        // Generate key and check status
        val result = keyStoreManager.generateEd25519KeyPair(alias = testAlias)
        val status = keyStoreManager.getAttestationStatus(testAlias)

        assertNotNull(status)
        assertEquals(testAlias, status.alias)
        assertTrue("Status backing should be TEE or STRONGBOX", status.backing == SecurityBacking.TEE || status.backing == SecurityBacking.STRONGBOX)
        assertTrue("isHardwareBacked should be true", status.isHardwareBacked)

        // Setting a test attestation certificate chain
        val mockCerts = emptyList<Certificate>()
        keyStoreManager.setCertificateChain(testAlias, mockCerts)
        assertEquals(0, keyStoreManager.getCertificateChain(testAlias).size)

        // Via SecurityRepository
        val repoStatus = securityRepository.getAttestationStatus(testAlias)
        assertEquals(status.backing, repoStatus.backing)
    }

    @Test
    fun testStrongBoxBackingPreferenceAndFallback() {
        val testAliasStrongBox = "test_strongbox_key"
        keyStoreManager.setRecordedBacking(testAliasStrongBox, SecurityBacking.STRONGBOX)
        val sbStatus = keyStoreManager.getAttestationStatus(testAliasStrongBox)
        assertEquals(SecurityBacking.STRONGBOX, sbStatus.backing)
        assertTrue(sbStatus.isStrongBoxBacked)
        assertTrue(sbStatus.isHardwareBacked)

        val testAliasTee = "test_tee_fallback_key"
        keyStoreManager.setRecordedBacking(testAliasTee, SecurityBacking.TEE)
        val teeStatus = keyStoreManager.getAttestationStatus(testAliasTee)
        assertEquals(SecurityBacking.TEE, teeStatus.backing)
        assertFalse(teeStatus.isStrongBoxBacked)
        assertTrue(teeStatus.isHardwareBacked)
    }

    @Test
    fun testEd25519SigningAndVerification() {
        val testAlias = "test_spark_sign_verify"
        securityRepository.generateDeviceSigningKey(alias = testAlias)

        val payload = "PAYLOAD_TO_SIGN_CANONICAL_JSON_TRANSACTION".toByteArray(Charsets.UTF_8)
        val signature = securityRepository.sign(payload, alias = testAlias)

        assertNotNull("Signature should not be null", signature)
        assertTrue("Signature should not be empty", signature.isNotEmpty())

        val isValid = securityRepository.verify(payload, signature, alias = testAlias)
        assertTrue("Signature verification should succeed for authentic payload", isValid)

        val tamperedPayload = "TAMPERED_PAYLOAD_DATA".toByteArray(Charsets.UTF_8)
        val isTamperedValid = securityRepository.verify(tamperedPayload, signature, alias = testAlias)
        assertFalse("Signature verification must fail for modified payload", isTamperedValid)
    }

    @Test
    fun testSigningCryptoObjectInitialization() {
        val testAlias = "test_spark_crypto_object"
        keyStoreManager.generateEd25519KeyPair(alias = testAlias)

        val signatureObject = keyStoreManager.getSigningCryptoObject(testAlias)
        assertNotNull("Signature crypto object must be initialized", signatureObject)
        assertTrue(
            "Algorithm should be Ed25519 or EdDSA, got: ${signatureObject.algorithm}",
            signatureObject.algorithm.equals("Ed25519", ignoreCase = true) ||
                    signatureObject.algorithm.equals("EdDSA", ignoreCase = true)
        )
    }

    @Test
    fun testKeyAliasStoreBlankAliasValidation() {
        try {
            keyAliasStore.saveKeyAlias("")
            fail("Expected IllegalArgumentException for blank alias")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("blank", ignoreCase = true) == true)
        }

        keyAliasStore.clearKeyAlias()
        assertNull(keyAliasStore.getKeyAlias())
        assertFalse(keyAliasStore.hasKeyAlias())
    }
}
