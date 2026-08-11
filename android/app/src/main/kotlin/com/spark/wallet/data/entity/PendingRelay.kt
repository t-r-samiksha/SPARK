package com.spark.wallet.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Queue for store-and-forward transaction bundles relayed across peers offline.
 */
@Entity(tableName = "pending_relay")
data class PendingRelay(
    @PrimaryKey
    @ColumnInfo(name = "tx_id")
    val txId: String,

    @ColumnInfo(name = "blob")
    val blob: String,

    @ColumnInfo(name = "destination_hint")
    val destinationHint: String?,

    @ColumnInfo(name = "ttl")
    val ttl: Long,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long
)
