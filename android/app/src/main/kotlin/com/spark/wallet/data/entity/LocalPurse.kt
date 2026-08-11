package com.spark.wallet.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents the offline digital cash purse allocated to this device.
 */
@Entity(tableName = "local_purse")
data class LocalPurse(
    @PrimaryKey
    @ColumnInfo(name = "token_id")
    val tokenId: String,

    @ColumnInfo(name = "cap")
    val cap: Long,

    @ColumnInfo(name = "remaining")
    val remaining: Long,

    @ColumnInfo(name = "counter_current")
    val counterCurrent: Long,

    @ColumnInfo(name = "signed_token_blob")
    val signedTokenBlob: String,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long
)
