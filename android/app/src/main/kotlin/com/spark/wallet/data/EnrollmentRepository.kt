package com.spark.wallet.data

import com.spark.wallet.network.NetworkClient
import com.spark.wallet.network.NetworkResult
import com.spark.wallet.security.AttestationManager
import com.spark.wallet.security.KeyStoreManager
import com.spark.wallet.security.SecurityRepository
import java.util.Base64

/**
 * Result state for device enrollment.
 */
sealed class EnrollmentResult {
    data class Success(
        val certificate: SparkCertificate,
        val certificatePem: String
    ) : EnrollmentResult()

    data class Error(
        val message: String,
        val isNetworkError: Boolean = false,
        val isAttestationError: Boolean = false,
        val isConflict: Boolean = false
    ) : EnrollmentResult()
}

/**
 * Repository orchestrating device onboarding:
 * 1. Hardware Ed25519 key generation via SecurityRepository (StrongBox preferred, TEE fallback).
 * 2. Raw 32-byte Base64url public key extraction and attestation blob construction.
 * 3. Network registration via POST /api/v1/enroll.
 * 4. Local persistence of device certificate PEM & caching of Bank Root CA certificate.
 */
class EnrollmentRepository(
    private val securityRepository: SecurityRepository,
    private val certificateStore: CertificateStore,
    private val keyAliasStore: KeyAliasStore,
    private val keyStoreManager: KeyStoreManager,
    private val networkClient: NetworkClient = NetworkClient(),
    private val attestationManager: AttestationManager = AttestationManager(keyStoreManager)
) {

    /**
     * Executes the complete enrollment lifecycle for an account.
     */
    suspend fun enrollDevice(
        accountId: String,
        alias: String = SecurityRepository.DEFAULT_DEVICE_KEY_ALIAS
    ): EnrollmentResult {
        // 1. Generate hardware-backed Ed25519 keypair and attestation chain
        val challenge = "SPARK_ENROLL_CHALLENGE_${System.currentTimeMillis()}".toByteArray()
        val keyGenResult = try {
            securityRepository.generateDeviceSigningKey(
                alias = alias,
                challenge = challenge,
                requireUserAuth = false
            )
        } catch (e: Exception) {
            return EnrollmentResult.Error("Hardware key generation failed: ${e.localizedMessage}")
        }

        // 2. Format public key as 32-byte Base64url unpadded string per docs/id-conventions.md
        val publicKeyBase64Url = try {
            CertificateFormat.encodePublicKeyRawBase64Url(keyGenResult.keyPair.public)
        } catch (e: Exception) {
            return EnrollmentResult.Error("Failed to encode Ed25519 public key: ${e.localizedMessage}")
        }

        // 3. Assemble attestation payload
        val attestationBlob = createAttestationBlob(keyGenResult)

        // 4. POST /api/v1/enroll
        val networkResult = networkClient.enrollDevice(
            accountId = accountId,
            publicKey = publicKeyBase64Url,
            attestationBlob = attestationBlob
        )

        return when (networkResult) {
            is NetworkResult.Success -> {
                val certPem = networkResult.data.cert
                try {
                    // 5. Parse and persist device certificate
                    val cert = CertificateFormat.parseDeviceCertificate(certPem)
                    certificateStore.saveDeviceCertificate(certPem)

                    // 6. Cache device ID and account ID
                    keyAliasStore.saveDeviceId(cert.deviceId)

                    // 7. Cache the Bank Root CA trust certificate
                    certificateStore.saveBankRootCertificate(CertificateStore.DEFAULT_BANK_ROOT_CA_PUBLIC_KEY)

                    EnrollmentResult.Success(cert, certPem)
                } catch (e: Exception) {
                    EnrollmentResult.Error("Failed to parse returned device certificate: ${e.localizedMessage}")
                }
            }
            is NetworkResult.Error -> {
                EnrollmentResult.Error(
                    message = networkResult.errorMessage,
                    isNetworkError = networkResult.isNetworkError,
                    isAttestationError = networkResult.isAttestationError,
                    isConflict = networkResult.isConflict
                )
            }
        }
    }

    private fun createAttestationBlob(keyGenResult: com.spark.wallet.security.KeyGenerationResult): String {
        val chain = keyGenResult.certificateChain
        if (chain.isNotEmpty()) {
            val pemChain = chain.joinToString("\n") { cert ->
                Base64.getEncoder().encodeToString(cert.encoded)
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(pemChain.toByteArray(Charsets.UTF_8))
        }

        // Standard fallback attestation blob for emulators / development environments
        val fallbackPayload = "SPARK_ATTESTATION_STATUS_${keyGenResult.backing}_ALIAS_${keyGenResult.alias}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(fallbackPayload.toByteArray(Charsets.UTF_8))
    }

    /**
     * Checks if the device is already successfully enrolled.
     */
    fun isDeviceEnrolled(): Boolean {
        return certificateStore.isEnrolled() && keyAliasStore.hasKeyAlias()
    }
}
