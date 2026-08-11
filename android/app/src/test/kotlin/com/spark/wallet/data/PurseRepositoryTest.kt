package com.spark.wallet.data

import com.spark.wallet.data.dao.LocalPurseDao
import com.spark.wallet.data.entity.LocalPurse
import com.spark.wallet.network.LimitRecommendationResponse
import com.spark.wallet.network.PurseLoadRequest
import com.spark.wallet.network.PurseLoadResponse
import com.spark.wallet.network.PurseTopUpRequest
import com.spark.wallet.network.PurseTopUpResponse
import com.spark.wallet.network.SparkApiService
import com.spark.wallet.ui.screens.HomeUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.UUID

class PurseRepositoryTest {

    private lateinit var mockPurseDao: InMemoryPurseDao
    private lateinit var mockApiService: MockPurseApiService
    private lateinit var purseRepository: PurseRepository

    private val deviceId = UUID.randomUUID().toString()
    private val tokenId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        mockPurseDao = InMemoryPurseDao()
        mockApiService = MockPurseApiService(deviceId, tokenId)
        purseRepository = PurseRepositoryImpl(
            purseDao = mockPurseDao,
            apiService = mockApiService
        )
    }

    @Test
    fun testPurseTokenFormatParsingAndPemWrapping() {
        val originalToken = SparkPurseToken(
            deviceId = deviceId,
            value = "150000",
            cap = "200000",
            counterStart = 10L,
            expiry = 1770600000L,
            tokenId = tokenId,
            signature = "6QQtyJFvKopqPH5o7EmhbpC-mU_OwGsy1QVKWV5ZpV79R1KHXqHoVqMwBOyEQ5MOxd36b0NF4H4GAVczrxuzDw"
        )

        // 1. Wrap into PEM
        val pem = PurseTokenFormat.wrapPurseToken(originalToken)
        assertTrue(pem.startsWith("-----BEGIN SPARK PURSE TOKEN-----"))
        assertTrue(pem.endsWith("-----END SPARK PURSE TOKEN-----"))

        // 2. Parse from PEM
        val parsedToken = PurseTokenFormat.parsePurseToken(pem)
        assertEquals(originalToken.deviceId, parsedToken.deviceId)
        assertEquals(originalToken.value, parsedToken.value)
        assertEquals(originalToken.cap, parsedToken.cap)
        assertEquals(originalToken.counterStart, parsedToken.counterStart)
        assertEquals(originalToken.expiry, parsedToken.expiry)
        assertEquals(originalToken.tokenId, parsedToken.tokenId)
        assertEquals(originalToken.signature, parsedToken.signature)
    }

    @Test
    fun testGetRecommendedLimit() = runBlocking {
        val limitResult = purseRepository.getRecommendedLimit()
        assertTrue(limitResult.isSuccess)
        assertEquals(200000L, limitResult.getOrThrow())
    }

    @Test
    fun testLoadPurseFlowSuccess() = runBlocking {
        val loadResult = purseRepository.loadPurse(150000L)
        assertTrue("Load purse should succeed", loadResult.isSuccess)

        val purse = loadResult.getOrThrow()
        assertEquals(tokenId, purse.tokenId)
        assertEquals(200000L, purse.cap)
        assertEquals(150000L, purse.remaining)
        assertEquals(0L, purse.counterCurrent)
        assertTrue(purse.expiresAt > 0)

        // Check stored in DB
        val stored = mockPurseDao.getPurseByTokenId(tokenId)
        assertNotNull(stored)
        assertEquals(150000L, stored!!.remaining)
    }

    @Test
    fun testTopUpPurseFlowSuccess() = runBlocking {
        // Initial load
        purseRepository.loadPurse(50000L).getOrThrow()

        // Top up with 100000 paise
        val topUpResult = purseRepository.topUpPurse(100000L)
        assertTrue("Top up should succeed", topUpResult.isSuccess)

        val updated = topUpResult.getOrThrow()
        assertEquals(100000L, updated.remaining)
    }

    @Test
    fun testHomeUiStateLowBalanceAndNearExpiry() {
        val activePurse = LocalPurse(
            tokenId = tokenId,
            cap = 200000L, // 2,000 INR
            remaining = 30000L, // 300 INR (< 20% of 2000 INR = 400 INR)
            counterCurrent = 2L,
            signedTokenBlob = "MOCK",
            expiresAt = System.currentTimeMillis() + (24 * 3600 * 1000L) // 24h (< 48h)
        )

        val lowBalanceState = HomeUiState(
            offlineAvailablePaise = activePurse.remaining,
            currentCapPaise = activePurse.cap,
            expiresAt = activePurse.expiresAt,
            activePurse = activePurse
        )

        assertTrue("isLowBalance must be true when balance < 20% of cap", lowBalanceState.isLowBalance)
        assertTrue("isNearExpiry must be true when expiry < 48 hours", lowBalanceState.isNearExpiry)

        val healthyPurse = activePurse.copy(
            remaining = 150000L, // 1500 INR
            expiresAt = System.currentTimeMillis() + (30 * 24 * 3600 * 1000L) // 30 days
        )

        val healthyState = HomeUiState(
            offlineAvailablePaise = healthyPurse.remaining,
            currentCapPaise = healthyPurse.cap,
            expiresAt = healthyPurse.expiresAt,
            activePurse = healthyPurse
        )

        assertFalse("isLowBalance must be false for healthy balance", healthyState.isLowBalance)
        assertFalse("isNearExpiry must be false for healthy expiry", healthyState.isNearExpiry)
    }

    // In-memory mock API Service for purse endpoints
    class MockPurseApiService(
        private val deviceId: String,
        private val tokenId: String
    ) : SparkApiService {
        override suspend fun enroll(request: com.spark.wallet.network.EnrollRequest): Response<com.spark.wallet.network.EnrollResponse> =
            throw UnsupportedOperationException()

        override suspend fun getLimitRecommendation(): Response<LimitRecommendationResponse> {
            return Response.success(LimitRecommendationResponse(recommendedCap = "200000", cap = "200000"))
        }

        override suspend fun loadPurse(request: PurseLoadRequest): Response<PurseLoadResponse> {
            val token = SparkPurseToken(
                deviceId = deviceId,
                value = request.value,
                cap = "200000",
                counterStart = 0L,
                expiry = (System.currentTimeMillis() + 86400000L * 30) / 1000,
                tokenId = tokenId,
                signature = "dummy-sig"
            )
            val pem = PurseTokenFormat.wrapPurseToken(token)
            return Response.success(PurseLoadResponse(purseToken = pem))
        }

        override suspend fun topUpPurse(request: PurseTopUpRequest): Response<PurseTopUpResponse> {
            val token = SparkPurseToken(
                deviceId = deviceId,
                value = request.amount,
                cap = "200000",
                counterStart = 5L, // Mocking that some transactions happened
                expiry = (System.currentTimeMillis() + 86400000L * 30) / 1000,
                tokenId = request.tokenId,
                signature = "dummy-signature"
            )
            val pem = PurseTokenFormat.wrapPurseToken(token)
            return Response.success(PurseTopUpResponse(purseToken = pem, status = "SUCCESS"))
        }

        override suspend fun syncTransactions(request: com.spark.wallet.network.SyncTransactionsRequest) = throw UnsupportedOperationException()
        override suspend fun getSyncUpdates(sinceEpochSec: String?) = throw UnsupportedOperationException()
    }

    class InMemoryPurseDao : LocalPurseDao {
        private val map = mutableMapOf<String, LocalPurse>()

        override suspend fun insertPurse(purse: LocalPurse) { map[purse.tokenId] = purse }
        override suspend fun updatePurse(purse: LocalPurse) { map[purse.tokenId] = purse }
        override suspend fun getPurseByTokenId(tokenId: String): LocalPurse? = map[tokenId]
        override suspend fun getActivePurse(): LocalPurse? = map.values.firstOrNull { it.remaining > 0 }
        override fun getAllPursesFlow(): Flow<List<LocalPurse>> = flowOf(map.values.toList())
        override suspend fun getAllPurses(): List<LocalPurse> = map.values.toList()
        override suspend fun updateRemainingAndCounter(tokenId: String, remaining: Long, counter: Long) {
            val existing = map[tokenId]
            if (existing != null) {
                map[tokenId] = existing.copy(remaining = remaining, counterCurrent = counter)
            }
        }
        override suspend fun deletePurse(tokenId: String) { map.remove(tokenId) }
        override suspend fun clearAll() { map.clear() }
    }
}
