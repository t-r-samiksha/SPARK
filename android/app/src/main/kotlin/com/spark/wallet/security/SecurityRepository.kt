package com.spark.wallet.security

import com.spark.wallet.data.KeyAliasStore
import java.security.PublicKey
import java.security.cert.Certificate

/**
 * Repository interface exposing hardware security key operations, attestation status,
 * and signature operations to the rest of the application (e.g. ViewModel, Settings screen, protocol engine).
 */
interface SecurityRepository {

    /**
     * Retrieves the locally registered key alias, if present.
     */
    fun getStoredKeyAlias(): String?

    /**
     * Generates a new hardware-backed Ed25519 device key (StrongBox preferred, TEE fallback).
     * Stores ONLY the key alias in local storage.
     */
    fun generateDeviceSigningKey(
        alias: String = DEFAULT_DEVICE_KEY_ALIAS,
        challenge: ByteArray? = null,
        requireUserAuth: Boolean = false
    ): KeyGenerationResult

    /**
     * Returns an existing device key, or generates one if not present.
     */
    fun getOrCreateDeviceKey(
        alias: String = DEFAULT_DEVICE_KEY_ALIAS,
        challenge: ByteArray? = null
    ): KeyGenerationResult

    /**
     * Exposes the hardware attestation status (STRONGBOX | TEE | UNAVAILABLE)
     * and certificate chain for the Settings screen security indicator.
     */
    fun getAttestationStatus(alias: String? = null): AttestationStatus

    /**
     * Retrieves the public key for the device key alias.
     */
    fun getPublicKey(alias: String? = null): PublicKey?

    /**
     * Retrieves the X.509 certificate attestation chain for the device key alias.
     */
    fun getCertificateChain(alias: String? = null): List<Certificate>

    /**
     * Signs transaction data with the device private key using Ed25519.
     */
    fun sign(data: ByteArray, alias: String? = null): ByteArray

    /**
     * Verifies a signature against payload data using the device public key.
     */
    fun verify(data: ByteArray, signature: ByteArray, alias: String? = null): Boolean

    /**
     * Prohibited: Attempting to export raw private key material fails and throws SecurityException.
     */
    fun exportPrivateKeyMaterial(alias: String? = null): ByteArray

    companion object {
        const val DEFAULT_DEVICE_KEY_ALIAS = "spark_device_signing_key"
    }
}

/**
 * Concrete implementation of [SecurityRepository] coordinating KeyStoreManager,
 * AttestationManager, and KeyAliasStore.
 */
class SecurityRepositoryImpl(
    private val keyStoreManager: KeyStoreManager,
    private val keyAliasStore: KeyAliasStore,
    private val attestationManager: AttestationManager = AttestationManager(keyStoreManager)
) : SecurityRepository {

    override fun getStoredKeyAlias(): String? {
        return keyAliasStore.getKeyAlias()
    }

    override fun generateDeviceSigningKey(
        alias: String,
        challenge: ByteArray?,
        requireUserAuth: Boolean
    ): KeyGenerationResult {
        val result = keyStoreManager.generateEd25519KeyPair(
            alias = alias,
            challenge = challenge,
            requireUserAuth = requireUserAuth
        )

        // Store ONLY the alias locally — never export or persist the private key material itself
        keyAliasStore.saveKeyAlias(alias)

        return result
    }

    override fun getOrCreateDeviceKey(
        alias: String,
        challenge: ByteArray?
    ): KeyGenerationResult {
        val existingAlias = keyAliasStore.getKeyAlias() ?: alias
        if (keyStoreManager.containsAlias(existingAlias)) {
            val pubKey = keyStoreManager.getPublicKey(existingAlias)
            val privKey = keyStoreManager.getPrivateKey(existingAlias)
            if (pubKey != null && privKey != null) {
                val chain = keyStoreManager.getCertificateChain(existingAlias)
                val status = keyStoreManager.getAttestationStatus(existingAlias)
                return KeyGenerationResult(
                    alias = existingAlias,
                    keyPair = java.security.KeyPair(pubKey, privKey),
                    certificateChain = chain,
                    backing = status.backing
                )
            }
        }
        return generateDeviceSigningKey(alias = alias, challenge = challenge)
    }

    override fun getAttestationStatus(alias: String?): AttestationStatus {
        val targetAlias = alias ?: keyAliasStore.getKeyAlias() ?: SecurityRepository.DEFAULT_DEVICE_KEY_ALIAS
        return attestationManager.getAttestationStatus(targetAlias)
    }

    override fun getPublicKey(alias: String?): PublicKey? {
        val targetAlias = alias ?: keyAliasStore.getKeyAlias() ?: SecurityRepository.DEFAULT_DEVICE_KEY_ALIAS
        return keyStoreManager.getPublicKey(targetAlias)
    }

    override fun getCertificateChain(alias: String?): List<Certificate> {
        val targetAlias = alias ?: keyAliasStore.getKeyAlias() ?: SecurityRepository.DEFAULT_DEVICE_KEY_ALIAS
        return attestationManager.getAttestationChain(targetAlias)
    }

    override fun sign(data: ByteArray, alias: String?): ByteArray {
        val targetAlias = alias ?: keyAliasStore.getKeyAlias()
            ?: throw IllegalStateException("No device key alias configured for signing")
        return keyStoreManager.sign(targetAlias, data)
    }

    override fun verify(data: ByteArray, signature: ByteArray, alias: String?): Boolean {
        val targetAlias = alias ?: keyAliasStore.getKeyAlias()
            ?: throw IllegalStateException("No device key alias configured for verification")
        return keyStoreManager.verify(targetAlias, data, signature)
    }

    /**
     * Prohibits raw private key material export. Hardware-backed private keys are non-exportable.
     * Always throws SecurityException.
     */
    override fun exportPrivateKeyMaterial(alias: String?): ByteArray {
        val targetAlias = alias ?: keyAliasStore.getKeyAlias()
            ?: throw IllegalStateException("No device key alias found")
        return keyStoreManager.exportPrivateKeyMaterial(targetAlias)
    }
}
