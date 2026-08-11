package com.spark.wallet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spark.wallet.data.entity.CachedTrust
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTrustDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrust(trust: CachedTrust)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trusts: List<CachedTrust>)

    @Query("SELECT * FROM cached_trust WHERE subject_id = :subjectId LIMIT 1")
    suspend fun getTrustBySubjectId(subjectId: String): CachedTrust?

    @Query("SELECT * FROM cached_trust ORDER BY cached_at DESC")
    suspend fun getAllTrust(): List<CachedTrust>

    @Query("SELECT * FROM cached_trust ORDER BY cached_at DESC")
    fun getAllTrustFlow(): Flow<List<CachedTrust>>

    @Query("DELETE FROM cached_trust WHERE subject_id = :subjectId")
    suspend fun deleteTrust(subjectId: String)

    @Query("DELETE FROM cached_trust")
    suspend fun clearAll()
}
