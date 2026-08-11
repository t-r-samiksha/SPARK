package com.spark.wallet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spark.wallet.data.entity.PendingRelay
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingRelayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingRelay(relay: PendingRelay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(relays: List<PendingRelay>)

    @Query("SELECT * FROM pending_relay WHERE tx_id = :txId LIMIT 1")
    suspend fun getPendingRelayById(txId: String): PendingRelay?

    @Query("SELECT * FROM pending_relay ORDER BY received_at ASC")
    suspend fun getAllPendingRelays(): List<PendingRelay>

    @Query("SELECT * FROM pending_relay ORDER BY received_at ASC")
    fun getAllPendingRelaysFlow(): Flow<List<PendingRelay>>

    @Query("DELETE FROM pending_relay WHERE tx_id = :txId")
    suspend fun deletePendingRelay(txId: String)

    @Query("DELETE FROM pending_relay WHERE ttl < :currentTime")
    suspend fun deleteExpiredRelays(currentTime: Long): Int

    @Query("DELETE FROM pending_relay")
    suspend fun clearAll()
}
