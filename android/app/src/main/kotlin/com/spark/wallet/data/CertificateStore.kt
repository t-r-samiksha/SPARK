package com.spark.wallet.data

import android.content.Context
import android.content.SharedPreferences
import com.spark.wallet.security.KeyStoreManager
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

/**
 * Result of offline certificate verification.
 */
sealed class CertificateValidationResult {
    data class Valid(val certificate: SparkCertificate) : CertificateValidationResult()
    data class Invalid(val reason: String) : CertificateValidationResult()
}

/**
 * Manages local persistent storage and offline trust caching for SPARK certificates.
 *
 * CRITICAL OFFLINE TRUST INVARIANT:
 * Holds the device's own certificate PEM and caches the Bank Root CA certificate/public key locally.
 * Offline transaction verification (peer-to-peer / stranger verification) relies on this store
 * to validate counterparty certificates without any network access.
 */
class CertificateStore(private val context: Context? = null) {

    companion object {
        private const val PREFS_NAME = "spark_certificate_store_prefs"
        private const val KEY_DEVICE_CERT_PEM = "device_cert_pem"
        private const val KEY_BANK_ROOT_CA_PEM = "bank_root_ca_pem"
        private const val KEY_BANK_ROOT_CA_PUBKEY = "bank_root_ca_public_key"

        // Default Bank Root CA public key (base64url unpadded, 32 bytes)
        // Bundled into the app so offline verification works out of the box before first sync
        const val DEFAULT_BANK_ROOT_CA_PUBLIC_KEY = "luMvVjfGi9lT4_L0t1lrAeKzda6qfdamp4v_yQzpBVE"
    }

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var inMemoryDeviceCertPem: String? = null
    private var inMemoryBankRootCaPem: String? = null
    private var inMemoryBankRootCaPubKey: String? = null

    /**
     * Persists the enrolled device certificate (full PEM).
     */
    fun saveDeviceCertificate(pem: String) {
        require(pem.isNotBlank()) { "Certificate PEM cannot be blank" }
        // Validate that it parses cleanly before persisting
        CertificateFormat.parseDeviceCertificate(pem)

        inMemoryDeviceCertPem = pem
        prefs?.edit()?.putString(KEY_DEVICE_CERT_PEM, pem)?.apply()
    }

    /**
     * Retrieves the stored device certificate PEM.
     */
    fun getDeviceCertificate(): String? {
        return prefs?.getString(KEY_DEVICE_CERT_PEM, null) ?: inMemoryDeviceCertPem
    }

    /**
     * Retrieves and parses the stored device certificate into a [SparkCertificate].
     */
    fun getParsedDeviceCertificate(): SparkCertificate? {
        val pem = getDeviceCertificate() ?: return null
        return try {
            CertificateFormat.parseDeviceCertificate(pem)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the device has a valid, stored enrollment certificate.
     */
    fun isEnrolled(): Boolean {
        return getDeviceCertificate() != null
    }

    /**
     * Caches the Bank Root CA certificate / public key locally for offline stranger verification.
     */
    fun saveBankRootCertificate(pemOrPublicKey: String) {
        require(pemOrPublicKey.isNotBlank()) { "Bank root CA value cannot be blank" }
        if (pemOrPublicKey.contains("BEGIN")) {
            inMemoryBankRootCaPem = pemOrPublicKey
            prefs?.edit()?.putString(KEY_BANK_ROOT_CA_PEM, pemOrPublicKey)?.apply()
        } else {
            inMemoryBankRootCaPubKey = pemOrPublicKey
            prefs?.edit()?.putString(KEY_BANK_ROOT_CA_PUBKEY, pemOrPublicKey)?.apply()
        }
    }

    /**
     * Retrieves the cached Bank Root CA certificate PEM, if available.
     */
    fun getBankRootCertificate(): String? {
        return prefs?.getString(KEY_BANK_ROOT_CA_PEM, null) ?: inMemoryBankRootCaPem
    }

    /**
     * Retrieves the active Bank Root CA Ed25519 public key (base64url unpadded).
     */
    fun getBankRootPublicKey(): String {
        return prefs?.getString(KEY_BANK_ROOT_CA_PUBKEY, null)
            ?: inMemoryBankRootCaPubKey
            ?: DEFAULT_BANK_ROOT_CA_PUBLIC_KEY
    }

    /**
     * Fully offline verification of a counterparty's SPARK device certificate.
     * Validates:
     * 1. Proper PEM wrapping and field structure.
     * 2. Device ID binding (if expectedDeviceId is provided).
     * 3. Validity time window (not_before <= now <= not_after).
     * 4. Ed25519 signature verification against the locally cached Bank Root CA public key.
     */
    fun verifyCertificateOffline(certPem: String, expectedDeviceId: String? = null): CertificateValidationResult {
        val cert = try {
            CertificateFormat.parseDeviceCertificate(certPem)
        } catch (e: Exception) {
            return CertificateValidationResult.Invalid("Certificate is not a valid SPARK DEVICE CERTIFICATE PEM: ${e.message}")
        }

        if (expectedDeviceId != null && cert.deviceId != expectedDeviceId) {
            return CertificateValidationResult.Invalid(
                "Certificate device_id (${cert.deviceId}) does not match expected device_id ($expectedDeviceId)"
            )
        }

        // Validity window check
        try {
            val now = Instant.now()
            val notBefore = Instant.parse(cert.notBefore)
            val notAfter = Instant.parse(cert.notAfter)

            if (now.isBefore(notBefore) || now.isAfter(notAfter)) {
                return CertificateValidationResult.Invalid(
                    "Certificate is outside its validity window ($notBefore to $notAfter, current: $now)"
                )
            }
        } catch (e: Exception) {
            return CertificateValidationResult.Invalid("Malformed ISO 8601 timestamp in certificate: ${e.message}")
        }

        // Verify Ed25519 signature with the cached Bank Root CA key
        val rootPublicKeyBase64Url = getBankRootPublicKey()
        val signingBytes = cert.toCanonicalSigningBytes()
        val signatureBytes = try {
            Base64.getUrlDecoder().decode(cert.signature)
        } catch (e: Exception) {
            return CertificateValidationResult.Invalid("Certificate signature is not valid base64url")
        }

        val isValidSig = verifyEd25519SignatureWithPublicKey(rootPublicKeyBase64Url, signingBytes, signatureBytes)
        if (!isValidSig) {
            return CertificateValidationResult.Invalid("Certificate signature is not valid for the Bank Root CA key")
        }

        return CertificateValidationResult.Valid(cert)
    }

    private fun verifyEd25519SignatureWithPublicKey(
        publicKeyBase64Url: String,
        data: ByteArray,
        signatureBytes: ByteArray
    ): Boolean {
        return try {
            val rawKeyBytes = Base64.getUrlDecoder().decode(publicKeyBase64Url)
            val pubKey = createEd25519PublicKeyFromRaw(rawKeyBytes)

            val signature = try {
                Signature.getInstance("Ed25519")
            } catch (e: Exception) {
                Signature.getInstance("EdDSA")
            }

            signature.initVerify(pubKey)
            signature.update(data)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    private fun createEd25519PublicKeyFromRaw(raw32Bytes: ByteArray): PublicKey {
        // Standard Ed25519 SubjectPublicKeyInfo DER header (12 bytes)
        val derHeader = byteArrayOf(
            0x30, 0x2a, // SEQUENCE (42 bytes)
            0x30, 0x05, // SEQUENCE (5 bytes)
            0x06, 0x03, 0x2b, 0x65, 0x70, // OID 1.3.101.112 (id-Ed25519)
            0x03, 0x21, 0x00 // BIT STRING (33 bytes, 0 unused bits)
        )
        val x509Bytes = derHeader + raw32Bytes
        val spec = X509EncodedKeySpec(x509Bytes)
        val kf = try {
            KeyFactory.getInstance("Ed25519")
        } catch (e: Exception) {
            KeyFactory.getInstance("EdDSA")
        }
        return kf.generatePublic(spec)
    }

    /**
     * Clears all stored certificates (e.g. for account reset / tests).
     */
    fun clear() {
        inMemoryDeviceCertPem = null
        inMemoryBankRootCaPem = null
        inMemoryBankRootCaPubKey = null
        prefs?.edit()?.clear()?.apply()
    }
}
