package com.spark.wallet.protocol

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EphemeralX25519KeyPair(
    val keyPair: KeyPair,
    val rawPublicKeyBytes: ByteArray
) {
    val publicKeyBase64Url: String = Base64.getUrlEncoder().withoutPadding().encodeToString(rawPublicKeyBytes)
}

/**
 * Manages X25519 ECDH key agreement and AES-256-GCM symmetric session encryption for offline peer transports.
 */
object SessionCrypto {

    private const val X25519_ALGORITHM = "X25519"
    private const val XDH_ALGORITHM = "XDH"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    // X.509 DER prefix for X25519 public key (RFC 8410: OID 1.3.101.110)
    private val X25519_X509_HEADER = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00
    )

    // PKCS#8 DER prefix for X25519 private key (RFC 8410: OID 1.3.101.110)
    private val X25519_PKCS8_HEADER = byteArrayOf(
        0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x04, 0x22, 0x04, 0x20
    )

    /**
     * Generates a fresh ephemeral X25519 keypair for session negotiation.
     */
    fun generateEphemeralKeyPair(): EphemeralX25519KeyPair {
        val kpg = try {
            KeyPairGenerator.getInstance(X25519_ALGORITHM)
        } catch (_: Exception) {
            KeyPairGenerator.getInstance(XDH_ALGORITHM)
        }

        val keyPair = kpg.generateKeyPair()
        val rawPub = extractRawX25519PublicKey(keyPair.public)
        return EphemeralX25519KeyPair(keyPair, rawPub)
    }

    /**
     * Computes the X25519 ECDH shared secret and derives a 256-bit AES session key using SHA-256 HKDF.
     */
    fun deriveAesSessionKey(
        myPrivateKey: PrivateKey,
        peerRawPublicKey: ByteArray
    ): SecretKeySpec {
        val peerPublicKey = decodeRawX25519PublicKey(peerRawPublicKey)

        val keyAgreement = try {
            KeyAgreement.getInstance(X25519_ALGORITHM)
        } catch (_: Exception) {
            KeyAgreement.getInstance(XDH_ALGORITHM)
        }

        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Derive 32-byte (256-bit) AES key via SHA-256 digest
        val sessionKeyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        return SecretKeySpec(sessionKeyBytes, "AES")
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM.
     * Output format: 12-byte random IV + Ciphertext + 16-byte GCM auth tag.
     */
    fun encryptAesGcm(secretKey: SecretKeySpec, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertextWithTag = cipher.doFinal(plaintext)

        val result = ByteArray(iv.size + ciphertextWithTag.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertextWithTag, 0, result, iv.size, ciphertextWithTag.size)
        return result
    }

    /**
     * Decrypts encrypted bytes (12-byte IV + Ciphertext + 16-byte tag) using AES-256-GCM.
     */
    fun decryptAesGcm(secretKey: SecretKeySpec, encryptedPayload: ByteArray): ByteArray {
        require(encryptedPayload.size > GCM_IV_LENGTH) { "Payload too short for AES-GCM IV" }

        val iv = ByteArray(GCM_IV_LENGTH)
        val ciphertext = ByteArray(encryptedPayload.size - GCM_IV_LENGTH)
        System.arraycopy(encryptedPayload, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(encryptedPayload, GCM_IV_LENGTH, ciphertext, 0, ciphertext.size)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(ciphertext)
    }

    fun extractRawX25519PublicKey(publicKey: PublicKey): ByteArray {
        val encoded = publicKey.encoded
        return if (encoded.size == 44) {
            encoded.copyOfRange(12, 44)
        } else {
            encoded.takeLast(32).toByteArray()
        }
    }

    fun decodeRawX25519PublicKey(raw32Bytes: ByteArray): PublicKey {
        require(raw32Bytes.size == 32) { "X25519 public key must be 32 bytes" }
        val x509Bytes = X25519_X509_HEADER + raw32Bytes
        val kf = try {
            KeyFactory.getInstance(X25519_ALGORITHM)
        } catch (_: Exception) {
            KeyFactory.getInstance(XDH_ALGORITHM)
        }
        return kf.generatePublic(X509EncodedKeySpec(x509Bytes))
    }
}
