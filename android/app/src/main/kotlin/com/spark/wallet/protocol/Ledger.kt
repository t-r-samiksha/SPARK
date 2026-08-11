package com.spark.wallet.protocol

import kotlinx.coroutines.flow.Flow

/**
 * Interface representing the local transaction ledger.
 */
interface Ledger {
    /**
     * Records a new incoming or outgoing transaction.
     */
    suspend fun recordTransaction(transaction: SparkTransaction, isPayer: Boolean)

    /**
     * Gets the latest device counter and hash for monotonic chaining.
     */
    suspend fun getLatestState(): LedgerState

    /**
     * Streams all transaction records.
     */
    fun observeTransactions(): Flow<List<SparkTransaction>>
}

data class LedgerState(
    val lastCounter: Long,
    val lastTxHash: String?,
    val availableBalancePaise: Long
)
