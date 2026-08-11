package com.spark.wallet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spark.wallet.data.entity.LocalLedgerEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalLedgerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: LocalLedgerEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LocalLedgerEntry>)

    @Query("SELECT * FROM local_ledger WHERE tx_id = :txId LIMIT 1")
    suspend fun getEntryById(txId: String): LocalLedgerEntry?

    @Query("SELECT * FROM local_ledger ORDER BY timestamp DESC")
    fun getAllEntriesFlow(): Flow<List<LocalLedgerEntry>>

    @Query("SELECT * FROM local_ledger ORDER BY timestamp DESC")
    suspend fun getAllEntries(): List<LocalLedgerEntry>

    @Query("SELECT * FROM local_ledger WHERE synced = 0 ORDER BY counter ASC")
    suspend fun getUnsyncedEntries(): List<LocalLedgerEntry>

    @Query("UPDATE local_ledger SET synced = 1 WHERE tx_id = :txId")
    suspend fun markSynced(txId: String)

    @Query("UPDATE local_ledger SET synced = 1 WHERE tx_id IN (:txIds)")
    suspend fun markAllSynced(txIds: List<String>)

    @Query("SELECT * FROM local_ledger ORDER BY counter DESC LIMIT 1")
    suspend fun getLatestEntry(): LocalLedgerEntry?

    @Query("SELECT * FROM local_ledger WHERE counterparty_id = :counterpartyId ORDER BY timestamp DESC")
    suspend fun getEntriesForCounterparty(counterpartyId: String): List<LocalLedgerEntry>

    @Query("DELETE FROM local_ledger")
    suspend fun clearAll()
}
