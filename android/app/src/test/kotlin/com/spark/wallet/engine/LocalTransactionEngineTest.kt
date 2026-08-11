package com.spark.wallet.engine

import com.spark.wallet.data.AppDatabase
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.CertificateValidationResult
import com.spark.wallet.data.SparkCertificate
import com.spark.wallet.data.dao.CachedCertDao
import com.spark.wallet.data.dao.CachedTrustDao
import com.spark.wallet.data.dao.LocalLedgerDao
import com.spark.wallet.data.dao.LocalPurseDao
import com.spark.wallet.data.dao.PendingRelayDao
import com.spark.wallet.data.dao.TransactionDao
import com.spark.wallet.data.entity.CachedCert
import com.spark.wallet.data.entity.CachedTrust
import com.spark.wallet.data.entity.LocalLedgerEntry
import com.spark.wallet.data.entity.LocalPurse
import com.spark.wallet.data.entity.PendingRelay
import com.spark.wallet.data.entity.TransactionRecord
import com.spark.wallet.protocol.CanonicalSerializer
import com.spark.wallet.protocol.Party
import com.spark.wallet.protocol.SparkTransaction
import com.spark.wallet.security.KeyStoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.UUID

class LocalTransactionEngineTest {

    private lateinit var keyStoreManager: KeyStoreManager
    private val keyAlias = "test_engine_key_alias"
    private val tokenId = UUID.randomUUID().toString()
    private val payerDeviceId = UUID.randomUUID().toString()
    private val payerAccountId = UUID.randomUUID().toString()

    private val payeeDeviceId = UUID.randomUUID().toString()
    private val payeeAccountId = UUID.randomUUID().toString()

    private lateinit var mockPurseDao: InMemoryPurseDao
    private lateinit var mockLedgerDao: InMemoryLedgerDao
    private lateinit var mockCachedCertDao: InMemoryCachedCertDao
    private lateinit var mockAppDatabase: AppDatabase
    private lateinit var mockCertStore: InMemoryCertificateStore

    private lateinit var engine: LocalTransactionEngine

    @Before
    fun setUp() {
        keyStoreManager = KeyStoreManager()
        keyStoreManager.generateEd25519KeyPair(alias = keyAlias)

        mockPurseDao = InMemoryPurseDao()
        mockLedgerDao = InMemoryLedgerDao()
        mockCachedCertDao = InMemoryCachedCertDao()
        mockCertStore = InMemoryCertificateStore(payerDeviceId, payerAccountId)

        // Seed an active purse with 100,000 paise (1,000 INR)
        runBlocking {
            mockPurseDao.insertPurse(
                LocalPurse(
                    tokenId = tokenId,
                    cap = 100000L,
                    remaining = 100000L,
                    counterCurrent = 0L,
                    signedTokenBlob = "-----BEGIN SPARK PURSE TOKEN-----\nMOCK_PURSE\n-----END SPARK PURSE TOKEN-----",
                    expiresAt = System.currentTimeMillis() + 86400000L
                )
            )
        }

        engine = LocalTransactionEngine(
            purseDao = mockPurseDao,
            ledgerDao = mockLedgerDao,
            cachedCertDao = mockCachedCertDao,
            certificateStore = mockCertStore,
            keyStoreManager = keyStoreManager,
            keyAlias = keyAlias,
            replayWindowSeconds = 300L
        )
    }

    /**
     * CORE REQUIREMENT:
     * Signs THREE chained transactions from the same device and confirms
     * tx3.prev_tx_hash == SHA256(canonical_json(tx2_including_sig)),
     * not SHA256(presignature bytes).
     */
    @Test
    fun testThreeChainedTransactionsPrevTxHashIncludesSignature() = runBlocking {
        val payeeParty = Party(
            deviceId = payeeDeviceId,
            accountId = payeeAccountId,
            cert = "-----BEGIN SPARK DEVICE CERTIFICATE-----\nPAYEE_CERT\n-----END SPARK DEVICE CERTIFICATE-----"
        )

        // --- Transaction 1 ---
        val tx1Result = engine.buildTransaction(
            amountPaise = 10000L, // 100 INR
            payee = payeeParty,
            tokenIdOverride = tokenId
        )
        assertTrue("Tx1 should succeed", tx1Result.isSuccess)
        val tx1 = tx1Result.getOrThrow()

        // Invariants for Tx 1
        assertEquals("First transaction in chain must have null prev_tx_hash", null, tx1.prevTxHash)
        assertEquals(1L, tx1.deviceCounter)
        assertEquals("10000", tx1.amount)

        // Compute expected hash of completed Tx 1 (INCLUDING its signature)
        val tx1FullCanonicalJson = String(CanonicalSerializer.canonicalizeFull(tx1), Charsets.UTF_8)
        assertTrue("Tx1 canonical full JSON must contain signature", tx1FullCanonicalJson.contains("signature"))
        val expectedTx1Hash = CanonicalSerializer.computeTransactionHash(tx1)

        // Pre-signature hash for comparison (MINUS signature)
        val tx1PreSignatureHash = CanonicalSerializer.computeTransactionHash(
            CanonicalSerializer.canonicalizeForSigning(tx1)
        )
        assertNotEquals("Hash including signature must differ from pre-signature hash", expectedTx1Hash, tx1PreSignatureHash)

        // --- Transaction 2 ---
        val tx2Result = engine.buildTransaction(
            amountPaise = 20000L, // 200 INR
            payee = payeeParty,
            tokenIdOverride = tokenId
        )
        assertTrue("Tx2 should succeed", tx2Result.isSuccess)
        val tx2 = tx2Result.getOrThrow()

        // Invariants for Tx 2
        assertEquals("Tx2 prev_tx_hash MUST equal SHA-256(canonical_json(tx1_including_sig))", expectedTx1Hash, tx2.prevTxHash)
        assertNotEquals("Tx2 prev_tx_hash must NOT equal SHA-256(presignature_bytes)", tx1PreSignatureHash, tx2.prevTxHash)
        assertEquals(2L, tx2.deviceCounter)
        assertEquals("20000", tx2.amount)

        // Compute expected hash of completed Tx 2 (INCLUDING its signature)
        val tx2FullCanonicalJson = String(CanonicalSerializer.canonicalizeFull(tx2), Charsets.UTF_8)
        assertTrue("Tx2 canonical full JSON must contain signature", tx2FullCanonicalJson.contains("signature"))
        val expectedTx2Hash = CanonicalSerializer.computeTransactionHash(tx2)

        // Pre-signature hash of Tx 2 (MINUS signature)
        val tx2PreSignatureHash = CanonicalSerializer.computeTransactionHash(
            CanonicalSerializer.canonicalizeForSigning(tx2)
        )
        assertNotEquals("Tx2 full hash must differ from pre-signature hash", expectedTx2Hash, tx2PreSignatureHash)

        // --- Transaction 3 ---
        val tx3Result = engine.buildTransaction(
            amountPaise = 30000L, // 300 INR
            payee = payeeParty,
            tokenIdOverride = tokenId
        )
        assertTrue("Tx3 should succeed", tx3Result.isSuccess)
        val tx3 = tx3Result.getOrThrow()

        // Invariants for Tx 3
        assertEquals(
            "CRITICAL ASSERTION: tx3.prev_tx_hash == SHA256(canonical_json(tx2_including_sig))",
            expectedTx2Hash,
            tx3.prevTxHash
        )
        assertNotEquals(
            "CRITICAL ASSERTION: tx3.prev_tx_hash MUST NOT equal SHA256(tx2_presignature_bytes)",
            tx2PreSignatureHash,
            tx3.prevTxHash
        )
        assertEquals(3L, tx3.deviceCounter)
        assertEquals("30000", tx3.amount)

        // Verify remaining purse balance: 100,000 - 10,000 - 20,000 - 30,000 = 40,000 paise
        val updatedPurse = mockPurseDao.getPurseByTokenId(tokenId)
        assertNotNull(updatedPurse)
        assertEquals(40000L, updatedPurse!!.remaining)
        assertEquals(3L, updatedPurse.counterCurrent)

        // Verify ledger has 3 outgoing entries recorded
        val ledgerEntries = mockLedgerDao.getAllEntries()
        assertEquals(3, ledgerEntries.size)
        assertEquals(listOf(1L, 2L, 3L), ledgerEntries.map { it.counter })
        assertTrue(ledgerEntries.all { it.direction == "out" })
    }

    @Test
    fun testAtomicPurseDecrementAndInsufficientBalance() = runBlocking {
        val payeeParty = Party(
            deviceId = payeeDeviceId,
            accountId = payeeAccountId,
            cert = "-----BEGIN SPARK DEVICE CERTIFICATE-----\nPAYEE_CERT\n-----END SPARK DEVICE CERTIFICATE-----"
        )

        // Spend entire purse balance: 100000 paise
        val spendAllResult = engine.buildTransaction(
            amountPaise = 100000L,
            payee = payeeParty,
            tokenIdOverride = tokenId
        )
        assertTrue("Spend all should succeed", spendAllResult.isSuccess)

        val purse = mockPurseDao.getPurseByTokenId(tokenId)!!
        assertEquals(0L, purse.remaining)
        assertEquals(1L, purse.counterCurrent)

        // Attempting to spend 1 more paisa must fail with InsufficientFunds
        val overspendResult = engine.buildTransaction(
            amountPaise = 1L,
            payee = payeeParty,
            tokenIdOverride = tokenId
        )
        assertTrue("Overspend must fail", overspendResult.isFailure)
        assertTrue(
            "Failure must be InsufficientFunds",
            overspendResult.exceptionOrNull() is TransactionException.InsufficientFunds
        )
    }

    @Test
    fun testReplayProtectionOnReceiveTimestampOutsideWindow() = runBlocking {
        val now = System.currentTimeMillis() / 1000
        val staleTimestamp = now - 600 // 10 minutes ago (> 300s window)

        val tx = SparkTransaction(
            txId = UUID.randomUUID().toString(),
            tokenId = tokenId,
            amount = "25000",
            payer = Party(
                deviceId = payerDeviceId,
                accountId = payerAccountId,
                cert = mockCertStore.getDeviceCertificatePem()!!
            ),
            payee = Party(
                deviceId = payeeDeviceId,
                accountId = payeeAccountId,
                cert = "MOCK_CERT"
            ),
            deviceCounter = 1L,
            prevTxHash = null,
            timestamp = staleTimestamp,
            signature = "dummy-sig"
        )

        val result = engine.receiveTransaction(tx, nowEpochSeconds = now)
        assertTrue("Receiving transaction outside timestamp window must fail", result.isFailure)
        assertTrue(
            "Exception must be TimestampOutsideWindow",
            result.exceptionOrNull() is TransactionException.TimestampOutsideWindow
        )
    }

    @Test
    fun testReplayProtectionOnReceiveStaleCounter() = runBlocking {
        val now = System.currentTimeMillis() / 1000

        // Simulate an already received transaction with counter = 5
        mockLedgerDao.insertEntry(
            LocalLedgerEntry(
                txId = UUID.randomUUID().toString(),
                direction = "in",
                counterpartyId = payerDeviceId,
                amount = 25000L,
                counter = 5L,
                prevHash = null,
                hash = "hash1",
                signature = "sig1",
                timestamp = now - 10,
                synced = false
            )
        )

        // New transaction arriving with stale counter = 5
        val txStale = SparkTransaction(
            txId = UUID.randomUUID().toString(),
            tokenId = tokenId,
            amount = "25000",
            payer = Party(
                deviceId = payerDeviceId,
                accountId = payerAccountId,
                cert = mockCertStore.getDeviceCertificatePem()!!
            ),
            payee = Party(
                deviceId = payeeDeviceId,
                accountId = payeeAccountId,
                cert = "MOCK_CERT"
            ),
            deviceCounter = 5L, // Stale counter (not > 5)
            prevTxHash = null,
            timestamp = now,
            signature = "dummy-sig"
        )

        val result = engine.receiveTransaction(txStale, nowEpochSeconds = now)
        assertTrue("Receiving transaction with stale counter must fail", result.isFailure)
        assertTrue(
            "Exception must be StaleOrReplayedCounter",
            result.exceptionOrNull() is TransactionException.StaleOrReplayedCounter
        )
    }
}

// In-Memory Mocks for unit testing DAOs and CertificateStore without disk locks

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

class InMemoryLedgerDao : LocalLedgerDao {
    private val entries = mutableListOf<LocalLedgerEntry>()

    override suspend fun insertEntry(entry: LocalLedgerEntry) { entries.add(entry) }
    override suspend fun insertAll(entries: List<LocalLedgerEntry>) { this.entries.addAll(entries) }
    override suspend fun getEntryById(txId: String): LocalLedgerEntry? = entries.find { it.txId == txId }
    override fun getAllEntriesFlow(): Flow<List<LocalLedgerEntry>> = flowOf(entries.toList())
    override suspend fun getAllEntries(): List<LocalLedgerEntry> = entries.toList()
    override suspend fun getUnsyncedEntries(): List<LocalLedgerEntry> = entries.filter { !it.synced }
    override suspend fun markSynced(txId: String) {
        val idx = entries.indexOfFirst { it.txId == txId }
        if (idx >= 0) entries[idx] = entries[idx].copy(synced = true)
    }
    override suspend fun markAllSynced(txIds: List<String>) {
        txIds.forEach { markSynced(it) }
    }
    override suspend fun getLatestEntry(): LocalLedgerEntry? = entries.maxByOrNull { it.counter }
    override suspend fun getEntriesForCounterparty(counterpartyId: String): List<LocalLedgerEntry> =
        entries.filter { it.counterpartyId == counterpartyId }
    override suspend fun clearAll() { entries.clear() }
}

class InMemoryCachedCertDao : CachedCertDao {
    private val certs = mutableMapOf<String, CachedCert>()
    override suspend fun insertCert(cert: CachedCert) { certs[cert.deviceId] = cert }
    override suspend fun insertAll(certs: List<CachedCert>) { certs.forEach { insertCert(it) } }
    override suspend fun getCertByDeviceId(deviceId: String): CachedCert? = certs[deviceId]
    override suspend fun getAllCerts(): List<CachedCert> = certs.values.toList()
    override fun getAllCertsFlow(): Flow<List<CachedCert>> = flowOf(certs.values.toList())
    override suspend fun deleteCert(deviceId: String) { certs.remove(deviceId) }
    override suspend fun deleteExpiredCerts(currentTime: Long): Int {
        val before = certs.size
        certs.values.removeAll { it.expiresAt < currentTime }
        return before - certs.size
    }
    override suspend fun clearAll() { certs.clear() }
}

class InMemoryCachedTrustDao : CachedTrustDao {
    private val trustMap = mutableMapOf<String, CachedTrust>()
    override suspend fun insertTrust(trust: CachedTrust) { trustMap[trust.subjectId] = trust }
    override suspend fun insertAll(trusts: List<CachedTrust>) { trusts.forEach { insertTrust(it) } }
    override suspend fun getTrustBySubjectId(subjectId: String): CachedTrust? = trustMap[subjectId]
    override suspend fun getAllTrust(): List<CachedTrust> = trustMap.values.toList()
    override fun getAllTrustFlow(): Flow<List<CachedTrust>> = flowOf(trustMap.values.toList())
    override suspend fun deleteTrust(subjectId: String) { trustMap.remove(subjectId) }
    override suspend fun clearAll() { trustMap.clear() }
}

class InMemoryPendingRelayDao : PendingRelayDao {
    private val relayMap = mutableMapOf<String, PendingRelay>()
    override suspend fun insertPendingRelay(relay: PendingRelay) { relayMap[relay.txId] = relay }
    override suspend fun insertAll(relays: List<PendingRelay>) { relays.forEach { insertPendingRelay(it) } }
    override suspend fun getPendingRelayById(txId: String): PendingRelay? = relayMap[txId]
    override suspend fun getAllPendingRelays(): List<PendingRelay> = relayMap.values.toList()
    override fun getAllPendingRelaysFlow(): Flow<List<PendingRelay>> = flowOf(relayMap.values.toList())
    override suspend fun deletePendingRelay(txId: String) { relayMap.remove(txId) }
    override suspend fun deleteExpiredRelays(currentTime: Long): Int {
        val before = relayMap.size
        relayMap.values.removeAll { it.ttl < currentTime }
        return before - relayMap.size
    }
    override suspend fun clearAll() { relayMap.clear() }
}

class InMemoryCertificateStore(
    private val deviceId: String,
    private val accountId: String
) : CertificateStore(null) {
    override fun getDeviceId(): String = deviceId
    override fun getAccountId(): String = accountId
    override fun getDeviceCertificatePem(): String = "-----BEGIN SPARK DEVICE CERTIFICATE-----\nMOCK_DEV_CERT\n-----END SPARK DEVICE CERTIFICATE-----"
    override fun verifyCertificateOffline(certPem: String, expectedDeviceId: String?): CertificateValidationResult {
        val cert = SparkCertificate(
            deviceId = expectedDeviceId ?: deviceId,
            accountId = accountId,
            devicePublicKey = "luMvVjfGi9lT4_L0t1lrAeKzda6qfdamp4v_yQzpBVE",
            serialNumber = "1001",
            notBefore = "2026-01-01T00:00:00Z",
            notAfter = "2030-01-01T00:00:00Z",
            signature = "sig"
        )
        return CertificateValidationResult.Valid(cert)
    }
}
