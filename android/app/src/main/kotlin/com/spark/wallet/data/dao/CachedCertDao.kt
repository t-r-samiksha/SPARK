package com.spark.wallet.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spark.wallet.data.entity.CachedCert
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedCertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCert(cert: CachedCert)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(certs: List<CachedCert>)

    @Query("SELECT * FROM cached_certs WHERE device_id = :deviceId LIMIT 1")
    suspend fun getCertByDeviceId(deviceId: String): CachedCert?

    @Query("SELECT * FROM cached_certs ORDER BY expires_at DESC")
    suspend fun getAllCerts(): List<CachedCert>

    @Query("SELECT * FROM cached_certs ORDER BY expires_at DESC")
    fun getAllCertsFlow(): Flow<List<CachedCert>>

    @Query("DELETE FROM cached_certs WHERE device_id = :deviceId")
    suspend fun deleteCert(deviceId: String)

    @Query("DELETE FROM cached_certs WHERE expires_at < :currentTime")
    suspend fun deleteExpiredCerts(currentTime: Long): Int

    @Query("DELETE FROM cached_certs")
    suspend fun clearAll()
}
