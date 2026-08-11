package com.spark.wallet.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache for bank-signed trust attestations and merchant reputation scores.
 */
@Entity(tableName = "cached_trust")
data class CachedTrust(
    @PrimaryKey
    @ColumnInfo(name = "subject_id")
    val subjectId: String,

    @ColumnInfo(name = "trust_score")
    val trustScore: Double,

    @ColumnInfo(name = "attestation_blobs")
    val attestationBlobs: String,

    @ColumnInfo(name = "cached_at")
    val cachedAt: Long
)
