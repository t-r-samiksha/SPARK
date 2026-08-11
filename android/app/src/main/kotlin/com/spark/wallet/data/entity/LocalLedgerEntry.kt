package com.spark.wallet.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local offline ledger recording every completed payment (incoming or outgoing).
 */
@Entity(tableName = "local_ledger")
data class LocalLedgerEntry(
    @PrimaryKey
    @ColumnInfo(name = "tx_id")
    val txId: String,

    @ColumnInfo(name = "direction")
    val direction: String, // "in" | "out"

    @ColumnInfo(name = "counterparty_id")
    val counterpartyId: String,

    @ColumnInfo(name = "amount")
    val amount: Long,

    @ColumnInfo(name = "counter")
    val counter: Long,

    @ColumnInfo(name = "prev_hash")
    val prevHash: String?,

    @ColumnInfo(name = "hash")
    val hash: String,

    @ColumnInfo(name = "signature")
    val signature: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "synced")
    val synced: Boolean = false
)
