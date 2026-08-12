package com.spark.wallet.engine

import com.spark.wallet.data.AppDatabase
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.dao.CachedTrustDao
import com.spark.wallet.data.entity.CachedTrust
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TrustManagerTest {

    private lateinit var cachedTrustDao: MockCachedTrustDao
    private lateinit var certificateStore: CertificateStore
    private lateinit var trustManager: TrustManager

    class MockCachedTrustDao : CachedTrustDao {
        var trusts = mutableListOf<CachedTrust>()
        override suspend fun insertTrust(trust: CachedTrust) { trusts.add(trust) }
        override suspend fun insertAll(newTrusts: List<CachedTrust>) { trusts.addAll(newTrusts) }
        override suspend fun getTrustBySubjectId(subjectId: String): CachedTrust? = trusts.find { it.subjectId == subjectId }
        override suspend fun getAllTrust(): List<CachedTrust> = trusts
        override fun getAllTrustFlow(): kotlinx.coroutines.flow.Flow<List<CachedTrust>> = kotlinx.coroutines.flow.flowOf(trusts)
        override suspend fun deleteTrust(subjectId: String) { trusts.removeIf { it.subjectId == subjectId } }
        override suspend fun clearAll() { trusts.clear() }
    }

    @Before
    fun setup() {
        cachedTrustDao = MockCachedTrustDao()
        
        certificateStore = object : CertificateStore(null) {
            override fun getDeviceId(): String? = "MyDevice"
            override fun getAccountId(): String? = "MyAccount"
            override fun getDeviceCertificatePem(): String? = "MOCK_PEM"
        }
        
        trustManager = TrustManager(cachedTrustDao, certificateStore)
    }

    @Test
    fun `findTrustPath returns 1_0 when payer equals payee`() = runBlocking {
        val result = trustManager.findTrustPath("DeviceA", "DeviceA")
        
        assertTrue(result.isValid)
        assertFalse(result.isHighRisk)
        assertEquals(1.0, result.score, 0.0)
        assertEquals(1, result.path.size)
    }

    @Test
    fun `findTrustPath returns valid path when 1 hop away`() = runBlocking {
        val attestation = """
            [
                {
                    "subject_a": "DeviceA",
                    "subject_b": "DeviceB",
                    "settled_amount": "500",
                    "settlement_count": 1,
                    "timestamp": "2026-08-01T00:00:00Z",
                    "signature": "sig"
                }
            ]
        """.trimIndent()
        
        cachedTrustDao.trusts.add(CachedTrust("DeviceA", 1.0, attestation, 0L))

        val result = trustManager.findTrustPath("DeviceA", "DeviceB")
        
        assertTrue(result.isValid)
        assertFalse(result.isHighRisk)
        assertEquals(0.5, result.score, 0.0) // 1 hop = 0.5^1 = 0.5
        assertEquals(2, result.path.size)
        assertEquals("DeviceA", result.path[0])
        assertEquals("DeviceB", result.path[1])
    }

    @Test
    fun `findTrustPath limits to 3 hops and fails if further`() = runBlocking {
        val attestationA = """[{"subject_a": "A", "subject_b": "B", "settled_amount": "1", "settlement_count": 1, "timestamp": "", "signature": ""}]"""
        val attestationB = """[{"subject_a": "B", "subject_b": "C", "settled_amount": "1", "settlement_count": 1, "timestamp": "", "signature": ""}]"""
        val attestationC = """[{"subject_a": "C", "subject_b": "D", "settled_amount": "1", "settlement_count": 1, "timestamp": "", "signature": ""}]"""
        val attestationD = """[{"subject_a": "D", "subject_b": "E", "settled_amount": "1", "settlement_count": 1, "timestamp": "", "signature": ""}]"""

        cachedTrustDao.trusts.addAll(listOf(
            CachedTrust("A", 1.0, attestationA, 0L),
            CachedTrust("B", 1.0, attestationB, 0L),
            CachedTrust("C", 1.0, attestationC, 0L),
            CachedTrust("D", 1.0, attestationD, 0L)
        ))

        // A -> B -> C -> D is 3 hops, so finding D should succeed
        val resultD = trustManager.findTrustPath("A", "D")
        assertTrue(resultD.isValid)
        assertEquals(0.125, resultD.score, 0.001) // 3 hops = 0.5^3 = 0.125
        assertTrue(resultD.isHighRisk) // 0.125 < 0.25 threshold

        // A -> B -> C -> D -> E is 4 hops, so finding E should fail
        val resultE = trustManager.findTrustPath("A", "E")
        assertFalse(resultE.isValid)
        assertEquals(0.0, resultE.score, 0.0)
    }
}
