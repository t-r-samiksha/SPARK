package com.spark.wallet.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.SparkCertificate
import com.spark.wallet.data.dao.CachedCertDao
import com.spark.wallet.data.dao.CachedTrustDao
import com.spark.wallet.data.dao.LocalLedgerDao
import com.spark.wallet.data.dao.LocalPurseDao
import com.spark.wallet.data.dao.PendingRelayDao
import com.spark.wallet.data.entity.CachedCert
import com.spark.wallet.data.entity.CachedTrust
import com.spark.wallet.data.entity.LocalLedgerEntry
import com.spark.wallet.data.entity.LocalPurse
import com.spark.wallet.data.entity.PendingRelay
import com.spark.wallet.network.DisasterFlag
import com.spark.wallet.network.EnrollRequest
import com.spark.wallet.network.EnrollResponse
import com.spark.wallet.network.LimitRecommendationResponse
import com.spark.wallet.network.PurseLoadRequest
import com.spark.wallet.network.PurseLoadResponse
import com.spark.wallet.network.PurseTopUpRequest
import com.spark.wallet.network.PurseTopUpResponse
import com.spark.wallet.network.SettleResult
import com.spark.wallet.network.SparkApiService
import com.spark.wallet.network.SyncTransactionsRequest
import com.spark.wallet.network.SyncTransactionsResponse
import com.spark.wallet.network.SyncUpdatesResponse
import com.spark.wallet.protocol.Party
import com.spark.wallet.protocol.SparkTransaction
import com.spark.wallet.protocol.TransactionBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.UUID

class SyncRepositoryTest {

    private lateinit var mockLedgerDao: InMemoryLedgerDao
    private lateinit var mockRelayDao: InMemoryPendingRelayDao
    private lateinit var mockCertDao: InMemoryCachedCertDao
    private lateinit var mockTrustDao: InMemoryCachedTrustDao
    private lateinit var mockPurseDao: InMemoryPurseDao
    private lateinit var mockCertStore: MockCertStore
    private lateinit var mockApiService: MockSyncApiService
    private lateinit var syncRepository: SyncRepository

    private val deviceId = UUID.randomUUID().toString()
    private val accountId = "00000000-0000-4000-8000-000000000001"
    private val revokedSerial = "SPARK-REVOKED-001"
    private val validSerial = "SPARK-VALID-002"

    @Before
    fun setUp() {
        mockLedgerDao = InMemoryLedgerDao()
        mockRelayDao = InMemoryPendingRelayDao()
        mockCertDao = InMemoryCachedCertDao()
        mockTrustDao = InMemoryCachedTrustDao()
        mockPurseDao = InMemoryPurseDao()
        mockCertStore = MockCertStore(deviceId, accountId)
        mockApiService = MockSyncApiService()

        val mockContext = createMockContext()
        val mockConnectivity = object : ConnectivityObserver(mockContext) {
            // override for tests
        }

        syncRepository = SyncRepository(
            context = mockContext,
            ledgerDao = mockLedgerDao,
            pendingRelayDao = mockRelayDao,
            cachedCertDao = mockCertDao,
            cachedTrustDao = mockTrustDao,
            purseDao = mockPurseDao,
            certificateStore = mockCertStore,
            apiService = mockApiService,
            connectivityObserver = mockConnectivity
        )
    }

    @Test
    fun testSmsSyncEncodingAndDecodingRoundtrip() {
        val originalTx = SparkTransaction(
            txId = UUID.randomUUID().toString(),
            tokenId = UUID.randomUUID().toString(),
            amount = "25000",
            payer = Party("dev-1", "acc-1", "-----BEGIN CERT-----\nMOCK\n-----END CERT-----"),
            payee = Party("dev-2", "acc-2", "-----BEGIN CERT-----\nMOCK2\n-----END CERT-----"),
            deviceCounter = 42L,
            prevTxHash = "prev-hash-base64url",
            timestamp = 1770600000L,
            signature = "sig-base64url"
        )

        // 1. Encode into SMS chunks
        val smsParts = SmsSyncManager.encodeTransaction(originalTx)
        assertTrue("SMS should produce at least one part", smsParts.isNotEmpty())

        // 2. Decode back from SMS parts
        val decodedTx = SmsSyncManager.decodeTransaction(smsParts)
        assertEquals(originalTx.txId, decodedTx.txId)
        assertEquals(originalTx.tokenId, decodedTx.tokenId)
        assertEquals(originalTx.amount, decodedTx.amount)
        assertEquals(originalTx.deviceCounter, decodedTx.deviceCounter)
        assertEquals(originalTx.prevTxHash, decodedTx.prevTxHash)
        assertEquals(originalTx.timestamp, decodedTx.timestamp)
        assertEquals(originalTx.signature, decodedTx.signature)
    }

    @Test
    fun testBatchSyncTransactionsSuccess() = runBlocking {
        // Add 1 unsynced ledger entry
        val txId1 = UUID.randomUUID().toString()
        mockLedgerDao.insertEntry(
            LocalLedgerEntry(
                txId = txId1,
                direction = "out",
                counterpartyId = "payee-1",
                amount = 25000L,
                counter = 1L,
                prevHash = null,
                hash = "hash1",
                signature = "sig1",
                timestamp = 1770600000L,
                synced = false
            )
        )

        // Add 1 pending relayed transaction
        val txId2 = UUID.randomUUID().toString()
        val relayedTx = SparkTransaction(
            txId = txId2,
            tokenId = "TOKEN-2",
            amount = "50000",
            payer = Party("dev-x", "acc-x", "cert-x"),
            payee = Party("dev-y", "acc-y", "cert-y"),
            deviceCounter = 5L,
            prevTxHash = "prev-hash",
            timestamp = 1770600000L,
            signature = "sig2"
        )
        mockRelayDao.insertPendingRelay(
            PendingRelay(
                txId = txId2,
                blob = TransactionBuilder.serialize(relayedTx),
                destinationHint = "dev-y",
                ttl = 3,
                receivedAt = System.currentTimeMillis()
            )
        )

        // Run sync
        val syncResult = syncRepository.syncTransactions()
        assertTrue(syncResult.isSuccess)
        val summary = syncResult.getOrThrow()

        assertEquals(2, summary.settledCount)
        assertEquals(0, summary.rejectedCount)

        // Verify local ledger marked synced
        val entry1 = mockLedgerDao.getEntryById(txId1)
        assertNotNull(entry1)
        assertTrue(entry1!!.synced)

        // Verify pending relay cleared
        val remainingRelays = mockRelayDao.getAllPendingRelays()
        assertTrue(remainingRelays.isEmpty())
    }

    @Test
    fun testSyncUpdatesAppliesCrlRevocationsAndDisasterFlags() = runBlocking {
        // 1. Seed initial purse
        val purse = LocalPurse(
            tokenId = "PURSE-1",
            cap = 200000L, // 2000 INR
            remaining = 150000L,
            counterCurrent = 1L,
            signedTokenBlob = "MOCK",
            expiresAt = System.currentTimeMillis() + 86400000L
        )
        mockPurseDao.insertPurse(purse)

        // 2. Seed a revoked certificate and a valid certificate in cached_certs
        val revokedCertPem = createMockCertPem("dev-revoked", revokedSerial)
        val validCertPem = createMockCertPem("dev-valid", validSerial)

        mockCertDao.insertCert(
            CachedCert("dev-revoked", "pub-1", revokedCertPem, System.currentTimeMillis() + 86400000L)
        )
        mockCertDao.insertCert(
            CachedCert("dev-valid", "pub-2", validCertPem, System.currentTimeMillis() + 86400000L)
        )

        // Configure mock API updates
        mockApiService.nextUpdates = SyncUpdatesResponse(
            crl = listOf(revokedSerial),
            crlCursor = 1770600000L,
            flags = listOf(
                DisasterFlag(
                    kind = "disaster",
                    type = "FLOOD_ALERT",
                    active = true,
                    higherCap = "500000" // Raises cap to 5,000 INR
                )
            ),
            trustAttestations = listOf("-----BEGIN SPARK TRUST ATTESTATION-----\nMOCK_ATTESTATION\n-----END SPARK TRUST ATTESTATION-----")
        )

        // Run sync updates
        val updatesResult = syncRepository.syncUpdates()
        assertTrue(updatesResult.isSuccess)

        // Verify CRL application: revoked cert deleted, valid cert kept
        assertNull(mockCertDao.getCertByDeviceId("dev-revoked"))
        assertNotNull(mockCertDao.getCertByDeviceId("dev-valid"))

        // Verify Disaster Flag applied: purse cap updated to 500,000 paise
        val updatedPurse = mockPurseDao.getActivePurse()
        assertNotNull(updatedPurse)
        assertEquals(500000L, updatedPurse!!.cap)

        // Verify Trust Attestation cached
        val cachedTrust = mockTrustDao.getTrustBySubjectId(deviceId)
        assertNotNull(cachedTrust)
    }

    private fun createMockCertPem(devId: String, serial: String): String {
        val cert = SparkCertificate(
            deviceId = devId,
            accountId = accountId,
            devicePublicKey = "dummy-pubkey-base64url",
            serialNumber = serial,
            notBefore = "2026-08-01T00:00:00Z",
            notAfter = "2027-08-01T00:00:00Z",
            signature = "dummy-sig"
        )
        return cert.toPem()
    }

    private fun createMockContext(): Context {
        val memoryPrefs = InMemorySharedPreferences()
        return object : android.content.ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = memoryPrefs
            override fun getSystemService(name: String): Any? {
                return null
            }
        }
    }

    class MockConnectivityObserver : ConnectivityObserver(createMockContextForConnectivity()) {
        override fun isCurrentlyConnected(): Boolean = true
    }

    companion object {
        fun createMockContextForConnectivity(): Context {
            return object : android.content.ContextWrapper(null) {
                override fun getSystemService(name: String): Any? = null
            }
        }
    }

    class MockSyncApiService : SparkApiService {
        var nextUpdates: SyncUpdatesResponse = SyncUpdatesResponse()

        override suspend fun enroll(request: EnrollRequest): Response<EnrollResponse> = throw UnsupportedOperationException()
        override suspend fun getLimitRecommendation(): Response<LimitRecommendationResponse> = throw UnsupportedOperationException()
        override suspend fun loadPurse(request: PurseLoadRequest): Response<PurseLoadResponse> = throw UnsupportedOperationException()
        override suspend fun topUpPurse(request: PurseTopUpRequest): Response<PurseTopUpResponse> = throw UnsupportedOperationException()

        override suspend fun syncTransactions(request: SyncTransactionsRequest): Response<SyncTransactionsResponse> {
            val results = request.transactions.map {
                SettleResult(txId = it.txId, status = "SETTLED")
            }
            return Response.success(SyncTransactionsResponse(results = results))
        }

        override suspend fun getSyncUpdates(sinceEpochSec: String?): Response<SyncUpdatesResponse> {
            return Response.success(nextUpdates)
        }
    }

    class MockCertStore(private val devId: String, private val accId: String) : CertificateStore(null) {
        override fun getDeviceId(): String = devId
        override fun getAccountId(): String = accId
        override fun getDeviceCertificatePem(): String = "MOCK_PEM"
    }

    class InMemorySharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class EditorImpl(private val target: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any>()
            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { if (value != null) temp[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { temp[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { temp[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { temp[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { temp[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = apply { temp.remove(key) }
            override fun clear(): SharedPreferences.Editor = apply { temp.clear() }
            override fun commit(): Boolean { target.putAll(temp); return true }
            override fun apply() { target.putAll(temp) }
        }
    }

    class InMemoryLedgerDao : LocalLedgerDao {
        private val list = mutableListOf<LocalLedgerEntry>()
        override suspend fun insertEntry(entry: LocalLedgerEntry) { list.add(entry) }
        override suspend fun insertAll(entries: List<LocalLedgerEntry>) { list.addAll(entries) }
        override suspend fun getEntryById(txId: String): LocalLedgerEntry? = list.firstOrNull { it.txId == txId }
        override suspend fun getLatestEntry(): LocalLedgerEntry? = list.lastOrNull()
        override suspend fun getAllEntries(): List<LocalLedgerEntry> = list.toList()
        override fun getAllEntriesFlow(): Flow<List<LocalLedgerEntry>> = flowOf(list.toList())
        override suspend fun getUnsyncedEntries(): List<LocalLedgerEntry> = list.filter { !it.synced }
        override suspend fun markSynced(txId: String) {
            val idx = list.indexOfFirst { it.txId == txId }
            if (idx != -1) list[idx] = list[idx].copy(synced = true)
        }
        override suspend fun markAllSynced(txIds: List<String>) {
            for (i in list.indices) {
                if (list[i].txId in txIds) list[i] = list[i].copy(synced = true)
            }
        }
        override suspend fun getEntriesForCounterparty(counterpartyId: String): List<LocalLedgerEntry> =
            list.filter { it.counterpartyId == counterpartyId }
        override suspend fun clearAll() { list.clear() }
    }

    class InMemoryPendingRelayDao : PendingRelayDao {
        private val map = mutableMapOf<String, PendingRelay>()
        override suspend fun insertPendingRelay(relay: PendingRelay) { map[relay.txId] = relay }
        override suspend fun insertAll(relays: List<PendingRelay>) { relays.forEach { map[it.txId] = it } }
        override suspend fun getAllPendingRelays(): List<PendingRelay> = map.values.toList()
        override fun getAllPendingRelaysFlow(): Flow<List<PendingRelay>> = flowOf(map.values.toList())
        override suspend fun getPendingRelayById(txId: String): PendingRelay? = map[txId]
        override suspend fun deletePendingRelay(txId: String) { map.remove(txId) }
        override suspend fun deleteExpiredRelays(currentTime: Long): Int {
            val before = map.size
            map.entries.removeIf { (it.value.receivedAt + it.value.ttl * 1000L) < currentTime }
            return before - map.size
        }
        override suspend fun clearAll() { map.clear() }
    }

    class InMemoryCachedCertDao : CachedCertDao {
        private val map = mutableMapOf<String, CachedCert>()
        override suspend fun insertCert(cert: CachedCert) { map[cert.deviceId] = cert }
        override suspend fun insertAll(certs: List<CachedCert>) { certs.forEach { map[it.deviceId] = it } }
        override suspend fun getCertByDeviceId(deviceId: String): CachedCert? = map[deviceId]
        override suspend fun getAllCerts(): List<CachedCert> = map.values.toList()
        override fun getAllCertsFlow(): Flow<List<CachedCert>> = flowOf(map.values.toList())
        override suspend fun deleteCert(deviceId: String) { map.remove(deviceId) }
        override suspend fun deleteExpiredCerts(currentTime: Long): Int = 0
        override suspend fun clearAll() { map.clear() }
    }

    class InMemoryCachedTrustDao : CachedTrustDao {
        private val map = mutableMapOf<String, CachedTrust>()
        override suspend fun insertTrust(trust: CachedTrust) { map[trust.subjectId] = trust }
        override suspend fun insertAll(trusts: List<CachedTrust>) { trusts.forEach { map[it.subjectId] = it } }
        override suspend fun getTrustBySubjectId(subjectId: String): CachedTrust? = map[subjectId]
        override suspend fun getAllTrust(): List<CachedTrust> = map.values.toList()
        override fun getAllTrustFlow(): Flow<List<CachedTrust>> = flowOf(map.values.toList())
        override suspend fun deleteTrust(subjectId: String) { map.remove(subjectId) }
        override suspend fun clearAll() { map.clear() }
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
