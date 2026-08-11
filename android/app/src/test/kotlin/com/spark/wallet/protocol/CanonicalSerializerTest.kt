package com.spark.wallet.protocol

import com.spark.wallet.security.KeyStoreManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class CanonicalSerializerTest {

    companion object {
        // Test vector from docs/canonical-serialization.md#test-vector
        const val TEST_PRIVATE_SEED_BASE64URL = "rWUD47KRVvCyr9N4knN-ZyKP1z2o0UKQEJCoVuNrSRw"
        const val TEST_PUBLIC_KEY_BASE64URL = "luMvVjfGi9lT4_L0t1lrAeKzda6qfdamp4v_yQzpBVE"
        const val EXPECTED_SIGNATURE_BASE64URL = "6QQtyJFvKopqPH5o7EmhbpC-mU_OwGsy1QVKWV5ZpV79R1KHXqHoVqMwBOyEQ5MOxd36b0NF4H4GAVczrxuzDw"
    }

    @Test
    fun testDocVectorKnownAnswerSignatureMatch() {
        // 1. Derive Ed25519 private key from the fixed reference seed in docs/canonical-serialization.md
        val privateKey = CanonicalSerializer.derivePrivateKeyFromBase64UrlSeed(TEST_PRIVATE_SEED_BASE64URL)
        assertNotNull("Private key derivation must succeed", privateKey)

        // 2. Canonicalize the sample message {"amount":"25000","device_id":"x"}
        // Note: raw input with arbitrary key order and whitespace
        val rawJsonString = """
            {
                "device_id": "x",
                "amount": "25000"
            }
        """.trimIndent()

        val jsonObject = Json.parseToJsonElement(rawJsonString).jsonObject
        val canonicalOutputString = CanonicalSerializer.serializeCanonical(jsonObject, excludeSignature = false)
        val canonicalBytes = CanonicalSerializer.canonicalize(jsonObject, excludeSignature = false)

        // Must match exact expected compact sorted string: {"amount":"25000","device_id":"x"}
        assertEquals("""{"amount":"25000","device_id":"x"}""", canonicalOutputString)
        assertEquals("""{"amount":"25000","device_id":"x"}""", String(canonicalBytes, Charsets.UTF_8))

        // 3. Sign using pure Ed25519
        val producedSignature = CanonicalSerializer.sign(canonicalBytes, privateKey)

        // 4. Assert the output matches the expected signature EXACTLY
        assertEquals(
            "Produced signature must match the documented test vector exactly",
            EXPECTED_SIGNATURE_BASE64URL,
            producedSignature
        )

        // 5. Verify the signature against the public key
        val pubKeyBytes = Base64.getUrlDecoder().decode(TEST_PUBLIC_KEY_BASE64URL)
        val derHeader = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        )
        val x509Spec = X509EncodedKeySpec(derHeader + pubKeyBytes)
        val kf = try { KeyFactory.getInstance("Ed25519") } catch (_: Exception) { KeyFactory.getInstance("EdDSA") }
        val publicKey = kf.generatePublic(x509Spec)

        val verifier = try { Signature.getInstance("Ed25519") } catch (_: Exception) { Signature.getInstance("EdDSA") }
        verifier.initVerify(publicKey)
        verifier.update(canonicalBytes)
        assertTrue("Signature must verify with the reference public key", verifier.verify(Base64.getUrlDecoder().decode(producedSignature)))
    }

    @Test
    fun testCanonicalSerializationSignatureExclusion() {
        val rawJsonString = """
            {
                "device_id": "x",
                "amount": "25000",
                "signature": "some-dummy-sig-to-exclude"
            }
        """.trimIndent()

        val jsonObject = Json.parseToJsonElement(rawJsonString).jsonObject
        val canonicalOutput = CanonicalSerializer.serializeCanonical(jsonObject, excludeSignature = true)

        assertEquals("""{"amount":"25000","device_id":"x"}""", canonicalOutput)
        assertFalse(canonicalOutput.contains("signature"))
    }

    @Test
    fun testNestedCanonicalSerializationWithMultiLinePemCert() {
        val multiLinePem = "-----BEGIN SPARK DEVICE CERTIFICATE-----\nline1\nline2\n-----END SPARK DEVICE CERTIFICATE-----"

        val tx = SparkTransaction(
            txId = "4a1e6e2b-9c3e-4a2e-8f1a-6b2c9d4e7f10",
            tokenId = "9f2c1a3e-5b4d-4e6f-8a1b-2c3d4e5f6a7b",
            amount = "25000",
            payer = Party(
                deviceId = "1a2b3c4d-1111-4a2b-8c1d-2e3f4a5b6c7d",
                accountId = "2b3c4d5e-2222-4b3c-9d2e-3f4a5b6c7d8e",
                cert = multiLinePem
            ),
            payee = Party(
                deviceId = "3c4d5e6f-3333-4c5d-ae3f-4a5b6c7d8e9f",
                accountId = "4d5e6f7a-4444-4d6e-bf4a-5b6c7d8e9fa0",
                cert = multiLinePem
            ),
            deviceCounter = 42,
            prevTxHash = "kZ7X2mN4pQ8rS1tU6vW9xY0zA3bC5dE7fG9hJ1kL3mN",
            timestamp = 1770000000,
            signature = "initial-sig-placeholder"
        )

        val canonicalBytes = CanonicalSerializer.canonicalize(tx)
        val canonicalString = String(canonicalBytes, Charsets.UTF_8)

        // Verify keys are strictly sorted at root and nested levels
        assertTrue(canonicalString.startsWith("""{"amount":"25000","device_counter":42,"payee":{"account_id":"""))
        assertFalse("Signature must be excluded from canonical bytes", canonicalString.contains("initial-sig-placeholder"))
        assertTrue("Multi-line PEM newlines must be escaped as \\n in JSON string", canonicalString.contains("\\n"))
    }

    @Test
    fun testTransactionBuilderBuildSigned() {
        val keyStoreManager = KeyStoreManager()
        val alias = "test_tx_signer_key"
        keyStoreManager.generateEd25519KeyPair(alias = alias)

        val partyPayer = Party(
            deviceId = "payer-device-id",
            accountId = "payer-account-id",
            cert = "-----BEGIN SPARK DEVICE CERTIFICATE-----\nMOCK_PAYER_CERT\n-----END SPARK DEVICE CERTIFICATE-----"
        )
        val partyPayee = Party(
            deviceId = "payee-device-id",
            accountId = "payee-account-id",
            cert = "-----BEGIN SPARK DEVICE CERTIFICATE-----\nMOCK_PAYEE_CERT\n-----END SPARK DEVICE CERTIFICATE-----"
        )

        val signedTx = TransactionBuilder.buildSignedTransaction(
            tokenId = "test-token-123",
            amountPaise = 50000,
            payer = partyPayer,
            payee = partyPayee,
            deviceCounter = 1,
            prevTxHash = null,
            signer = { canonicalBytes ->
                TransactionBuilder.sign(canonicalBytes, keyStoreManager, alias)
            }
        )

        assertNotNull(signedTx.signature)
        assertTrue(signedTx.signature.isNotEmpty())
        assertFalse(signedTx.signature.contains("="))
        assertEquals("50000", signedTx.amount)

        // Verify signature with KeyStoreManager
        val canonicalBytes = signedTx.toCanonicalSigningBytes()
        val sigBytes = Base64.getUrlDecoder().decode(signedTx.signature)
        val isValid = keyStoreManager.verify(alias, canonicalBytes, sigBytes)
        assertTrue("Built transaction signature must be valid", isValid)
    }
}
