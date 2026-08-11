package com.spark.wallet.data

import com.spark.wallet.data.dao.LocalPurseDao
import com.spark.wallet.data.entity.LocalPurse
import com.spark.wallet.network.NetworkClient
import com.spark.wallet.network.PurseLoadRequest
import com.spark.wallet.network.PurseTopUpRequest
import com.spark.wallet.network.SparkApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * HARDWARE VS SOFTWARE COUNTER TRADEOFF:
 * We're using the Keystore-backed monotonic counter as the enforcement point since we don't
 * have real embedded Secure Element (eSE) access on commercial Android hardware.
 * In production with an eSE (or StrongBox Keymaster with monotonic counter support), counter
 * rollbacks are prevented directly in silicon. In our TEE / KeyStore layer, the monotonic counter
 * in local_purse coupled with backend settlement chaining (prev_tx_hash continuity) guarantees
 * that any replay or out-of-order spend is strictly caught and rejected by peers and backend.
 * Note: This is the honest positioning we give in Q&A.
 */
interface PurseRepository {
    val activePurseFlow: Flow<LocalPurse?>
    suspend fun getActivePurse(): LocalPurse?
    suspend fun getRecommendedLimit(): Result<Long>
    suspend fun loadPurse(amountPaise: Long): Result<LocalPurse>
    suspend fun topUpPurse(amountPaise: Long): Result<LocalPurse>
}

class PurseRepositoryImpl(
    private val purseDao: LocalPurseDao,
    private val apiService: SparkApiService = NetworkClient.createApiService(),
    private val certificateStore: CertificateStore? = null
) : PurseRepository {

    override val activePurseFlow: Flow<LocalPurse?> = purseDao.getAllPursesFlow().map { purses ->
        purses.firstOrNull { it.remaining > 0 && it.expiresAt > System.currentTimeMillis() }
            ?: purses.firstOrNull()
    }

    override suspend fun getActivePurse(): LocalPurse? {
        return purseDao.getActivePurse()
    }

    override suspend fun getRecommendedLimit(): Result<Long> = runCatching {
        try {
            val response = apiService.getLimitRecommendation()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.effectiveCap.toLong()
            } else {
                200000L // Default ₹2,000 (200000 paise)
            }
        } catch (_: Exception) {
            200000L // Offline fallback recommendation
        }
    }

    override suspend fun loadPurse(amountPaise: Long): Result<LocalPurse> = runCatching {
        require(amountPaise > 0) { "Load amount must be greater than 0" }

        val response = apiService.loadPurse(PurseLoadRequest(value = amountPaise.toString()))
        if (!response.isSuccessful || response.body() == null) {
            val errorBody = response.errorBody()?.string()
            throw Exception("Failed to load purse (${response.code()}): ${errorBody ?: "Unknown error"}")
        }

        val tokenPem = response.body()!!.purseToken
        val parsedToken = PurseTokenFormat.parsePurseToken(tokenPem)

        val localPurse = LocalPurse(
            tokenId = parsedToken.tokenId,
            cap = parsedToken.cap.toLong(),
            remaining = parsedToken.value.toLong(),
            counterCurrent = parsedToken.counterStart,
            signedTokenBlob = tokenPem,
            expiresAt = parsedToken.expiry * 1000L
        )

        purseDao.insertPurse(localPurse)
        localPurse
    }

    override suspend fun topUpPurse(amountPaise: Long): Result<LocalPurse> = runCatching {
        require(amountPaise > 0) { "Top-up amount must be greater than 0" }
        val currentPurse = purseDao.getActivePurse()

        val response = if (currentPurse != null) {
            try {
                apiService.topUpPurse(
                    PurseTopUpRequest(
                        tokenId = currentPurse.tokenId,
                        amount = amountPaise.toString()
                    )
                )
            } catch (_: Exception) {
                // If topup route returns 501 / error, fall back to loadPurse
                null
            }
        } else null

        if (response != null && response.isSuccessful && response.body()?.purseToken != null) {
            val tokenPem = response.body()!!.purseToken!!
            val parsedToken = PurseTokenFormat.parsePurseToken(tokenPem)

            val updatedPurse = LocalPurse(
                tokenId = parsedToken.tokenId,
                cap = parsedToken.cap.toLong(),
                remaining = parsedToken.value.toLong(),
                counterCurrent = parsedToken.counterStart,
                signedTokenBlob = tokenPem,
                expiresAt = parsedToken.expiry * 1000L
            )
            purseDao.insertPurse(updatedPurse)
            updatedPurse
        } else {
            // Refill via loadPurse
            val currentRemaining = currentPurse?.remaining ?: 0L
            val targetValue = currentRemaining + amountPaise
            loadPurse(targetValue).getOrThrow()
        }
    }
}
