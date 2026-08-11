package com.spark.wallet.security

import java.security.cert.Certificate

/**
 * Represents the hardware security module backing of a cryptographic key.
 */
enum class SecurityBacking {
    STRONGBOX,
    TEE,
    UNAVAILABLE
}

/**
 * Data class holding the attestation status and hardware backing info.
 * Exposed to UI components like Settings screen indicator.
 */
data class AttestationStatus(
    val backing: SecurityBacking,
    val alias: String? = null,
    val hasAttestationChain: Boolean = false,
    val certificateChain: List<Certificate> = emptyList(),
    val isHardwareBacked: Boolean = backing == SecurityBacking.STRONGBOX || backing == SecurityBacking.TEE,
    val isStrongBoxBacked: Boolean = backing == SecurityBacking.STRONGBOX
)
