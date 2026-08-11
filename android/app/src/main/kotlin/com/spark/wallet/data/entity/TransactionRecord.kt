package com.spark.wallet.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionRecord(
    @PrimaryKey
    @ColumnInfo(name = "tx_id")
    val txId: String,

    @ColumnInfo(name = "token_id")
    val tokenId: String,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,

    @ColumnInfo(name = "payer_device_id")
    val payerDeviceId: String,

    @ColumnInfo(name = "payer_account_id")
    val payerAccountId: String,

    @ColumnInfo(name = "payee_device_id")
    val payeeDeviceId: String,

    @ColumnInfo(name = "payee_account_id")
    val payeeAccountId: String,

    @ColumnInfo(name = "device_counter")
    val deviceCounter: Long,

    @ColumnInfo(name = "prev_tx_hash")
    val prevTxHash: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "signature")
    val signature: String,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "raw_json")
    val rawJson: String
)
