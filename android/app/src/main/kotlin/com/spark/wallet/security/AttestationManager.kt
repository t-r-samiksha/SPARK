package com.spark.wallet.security

import java.security.KeyPair
import java.security.cert.Certificate
import java.security.cert.X509Certificate

/**
 * Handles Android Key Attestation to prove hardware security module (StrongBox/TEE) backing.
 */
class AttestationManager(private val keyStoreManager: KeyStoreManager) {

    /**
     * Generates an Ed25519 key pair with an attestation challenge payload provided by the SPARK backend.
     */
    fun generateAttestedKey(alias: String, challenge: ByteArray): KeyGenerationResult {
        return keyStoreManager.generateEd25519KeyPair(
            alias = alias,
            challenge = challenge,
            requireUserAuth = false
        )
    }

    /**
     * Retrieves the X.509 certificate attestation chain for an alias.
     */
    fun getAttestationChain(alias: String): List<Certificate> {
        return keyStoreManager.getCertificateChain(alias)
    }

    /**
     * Retrieves the current AttestationStatus for an alias.
     */
    fun getAttestationStatus(alias: String): AttestationStatus {
        return keyStoreManager.getAttestationStatus(alias)
    }

    /**
     * Validates whether the certificate chain contains valid X509 certificates.
     */
    fun isAttestationChainValid(alias: String): Boolean {
        val chain = getAttestationChain(alias)
        if (chain.isEmpty()) return false
        return chain.all { it is X509Certificate }
    }
}
