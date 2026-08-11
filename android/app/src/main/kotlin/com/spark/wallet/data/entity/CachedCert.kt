package com.spark.wallet.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache for verified counterparty device certificates.
 */
@Entity(tableName = "cached_certs")
data class CachedCert(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "public_key")
    val publicKey: String,

    @ColumnInfo(name = "cert_blob")
    val certBlob: String,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long
)
