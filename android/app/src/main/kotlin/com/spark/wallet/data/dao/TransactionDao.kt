package com.spark.wallet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spark.wallet.data.entity.TransactionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionRecord>)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionRecord>>

    @Query("SELECT * FROM transactions WHERE tx_id = :txId LIMIT 1")
    suspend fun getTransactionById(txId: String): TransactionRecord?

    @Query("SELECT * FROM transactions WHERE is_synced = 0 ORDER BY device_counter ASC")
    suspend fun getUnsyncedTransactions(): List<TransactionRecord>

    @Query("UPDATE transactions SET is_synced = 1 WHERE tx_id IN (:txIds)")
    suspend fun markSynced(txIds: List<String>)

    @Query("SELECT * FROM transactions ORDER BY device_counter DESC LIMIT 1")
    suspend fun getLatestTransaction(): TransactionRecord?
}
