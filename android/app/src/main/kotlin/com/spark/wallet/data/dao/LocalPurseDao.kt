package com.spark.wallet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spark.wallet.data.entity.LocalPurse
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalPurseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurse(purse: LocalPurse)

    @Update
    suspend fun updatePurse(purse: LocalPurse)

    @Query("SELECT * FROM local_purse WHERE token_id = :tokenId LIMIT 1")
    suspend fun getPurseByTokenId(tokenId: String): LocalPurse?

    @Query("SELECT * FROM local_purse WHERE remaining > 0 ORDER BY expires_at DESC LIMIT 1")
    suspend fun getActivePurse(): LocalPurse?

    @Query("SELECT * FROM local_purse ORDER BY expires_at DESC")
    fun getAllPursesFlow(): Flow<List<LocalPurse>>

    @Query("SELECT * FROM local_purse ORDER BY expires_at DESC")
    suspend fun getAllPurses(): List<LocalPurse>

    @Query("UPDATE local_purse SET remaining = :remaining, counter_current = :counter WHERE token_id = :tokenId")
    suspend fun updateRemainingAndCounter(tokenId: String, remaining: Long, counter: Long)

    @Query("DELETE FROM local_purse WHERE token_id = :tokenId")
    suspend fun deletePurse(tokenId: String)

    @Query("DELETE FROM local_purse")
    suspend fun clearAll()
}
