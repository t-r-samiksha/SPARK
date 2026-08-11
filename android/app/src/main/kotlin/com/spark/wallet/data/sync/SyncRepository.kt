package com.spark.wallet.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.spark.wallet.data.CertificateFormat
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.dao.CachedCertDao
import com.spark.wallet.data.dao.CachedTrustDao
import com.spark.wallet.data.dao.LocalLedgerDao
import com.spark.wallet.data.dao.LocalPurseDao
import com.spark.wallet.data.dao.PendingRelayDao
import com.spark.wallet.data.entity.CachedTrust
import com.spark.wallet.data.entity.LocalLedgerEntry
import com.spark.wallet.network.NetworkClient
import com.spark.wallet.network.SettleIncident
import com.spark.wallet.network.SettleResult
import com.spark.wallet.network.SparkApiService
import com.spark.wallet.network.SyncTransactionsRequest
import com.spark.wallet.network.SyncUpdatesResponse
import com.spark.wallet.protocol.Party
import com.spark.wallet.protocol.SparkTransaction
import com.spark.wallet.protocol.TransactionBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SyncSummary(
    val settledCount: Int = 0,
    val rejectedCount: Int = 0,
    val incidentsCount: Int = 0,
    val crlCount: Int = 0,
    val trustAttestationsCount: Int = 0,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

/**
 * Manages full bidirectional sync with SPARK backend:
 * 1. Batch settlement of own and relayed transactions via POST /sync/transactions
 * 2. Applying CRL revocations, disaster flags, risk caps, and trust attestations via GET /sync/updates
 * 3. Opportunistic background syncing when network connectivity appears.
 */
class SyncRepository(
    private val context: Context,
    private val ledgerDao: LocalLedgerDao,
    private val pendingRelayDao: PendingRelayDao,
    private val cachedCertDao: CachedCertDao,
    private val cachedTrustDao: CachedTrustDao,
    private val purseDao: LocalPurseDao,
    private val certificateStore: CertificateStore,
    private val apiService: SparkApiService = NetworkClient.createApiService(),
    private val connectivityObserver: ConnectivityObserver = ConnectivityObserver(context)
) {
    companion object {
        private const val TAG = "SyncRepository"
        private const val PREFS_NAME = "spark_sync_prefs"
        private const val KEY_CRL_CURSOR = "last_crl_cursor"
        private const val KEY_ESCROW_CURSOR = "last_escrow_cursor"
        private const val KEY_LAST_SYNC_TIME = "last_sync_timestamp"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncSummary = MutableStateFlow<SyncSummary?>(null)
    val lastSyncSummary: StateFlow<SyncSummary?> = _lastSyncSummary.asStateFlow()

    val isOnlineFlow: Flow<Boolean> = connectivityObserver.isOnlineFlow

    /**
     * Starts listening to connectivity state and triggers opportunistic sync whenever online.
     */
    fun startOpportunisticSync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            connectivityObserver.isOnlineFlow.collectLatest { isOnline ->
                if (isOnline) {
                    Log.i(TAG, "Network path detected. Triggering opportunistic auto-sync...")
                    fullSync()
                }
            }
        }
    }

    /**
     * Executes full sync: uploads unsynced & relayed transactions, then pulls CRL and trust updates.
     */
    suspend fun fullSync(): Result<SyncSummary> {
        _isSyncing.value = true
        return try {
            val txResult = syncTransactions().getOrThrow()
            val updatesResult = syncUpdates().getOrThrow()

            val summary = SyncSummary(
                settledCount = txResult.settledCount,
                rejectedCount = txResult.rejectedCount,
                incidentsCount = txResult.incidentsCount,
                crlCount = updatesResult.crl.size,
                trustAttestationsCount = updatesResult.trustAttestations.size,
                isSuccess = true
            )
            prefs.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
            _lastSyncSummary.value = summary
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Full sync failed", e)
            val summary = SyncSummary(
                isSuccess = false,
                errorMessage = e.message ?: "Sync failed"
            )
            _lastSyncSummary.value = summary
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * POST /api/v1/sync/transactions: Batches own unsynced ledger entries + pending relayed transactions.
     */
    suspend fun syncTransactions(): Result<SyncSummary> = runCatching {
        val unsyncedLedger = ledgerDao.getUnsyncedEntries()
        val pendingRelays = pendingRelayDao.getAllPendingRelays()

        if (unsyncedLedger.isEmpty() && pendingRelays.isEmpty()) {
            return@runCatching SyncSummary()
        }

        val transactionsToSync = mutableListOf<SparkTransaction>()

        // 1. Reconstruct transactions from own ledger
        val myDeviceId = certificateStore.getDeviceId() ?: "UNKNOWN_DEV"
        val myAccountId = certificateStore.getAccountId() ?: "UNKNOWN_ACC"
        val myCertPem = certificateStore.getDeviceCertificatePem() ?: ""

        val activePurse = purseDao.getActivePurse()
        val currentTokenId = activePurse?.tokenId ?: "00000000-0000-4000-8000-000000000000"

        for (entry in unsyncedLedger) {
            val tx = SparkTransaction(
                txId = entry.txId,
                tokenId = currentTokenId,
                amount = entry.amount.toString(),
                payer = Party(
                    deviceId = if (entry.direction == "out") myDeviceId else entry.counterpartyId,
                    accountId = myAccountId,
                    cert = myCertPem
                ),
                payee = Party(
                    deviceId = if (entry.direction == "out") entry.counterpartyId else myDeviceId,
                    accountId = myAccountId,
                    cert = myCertPem
                ),
                deviceCounter = entry.counter,
                prevTxHash = entry.prevHash,
                timestamp = entry.timestamp,
                signature = entry.signature
            )
            transactionsToSync.add(tx)
        }

        // 2. Parse relayed transactions
        for (relay in pendingRelays) {
            try {
                val tx = TransactionBuilder.deserialize(relay.blob)
                if (transactionsToSync.none { it.txId == tx.txId }) {
                    transactionsToSync.add(tx)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed relayed transaction blob ${relay.txId}: ${e.message}")
            }
        }

        Log.i(TAG, "Submitting batch of ${transactionsToSync.size} transaction(s) to /sync/transactions")
        val response = apiService.syncTransactions(SyncTransactionsRequest(transactionsToSync))

        if (!response.isSuccessful || response.body() == null) {
            val errorBody = response.errorBody()?.string()
            throw Exception("POST /sync/transactions failed (${response.code()}): ${errorBody ?: "Unknown error"}")
        }

        val body = response.body()!!
        var settledCount = 0
        var rejectedCount = 0

        for (result in body.results) {
            if (result.status == "SETTLED") {
                settledCount++
                ledgerDao.markSynced(result.txId)
                pendingRelayDao.deletePendingRelay(result.txId)
            } else {
                rejectedCount++
                Log.w(TAG, "Transaction ${result.txId} was rejected during sync: ${result.rejectionReason}")
            }
        }

        SyncSummary(
            settledCount = settledCount,
            rejectedCount = rejectedCount,
            incidentsCount = body.incidents.size,
            isSuccess = true
        )
    }

    /**
     * GET /api/v1/sync/updates: Fetches and applies CRL entries, disaster flags, risk caps, and trust attestations.
     */
    suspend fun syncUpdates(): Result<SyncUpdatesResponse> = runCatching {
        val lastCursor = prefs.getLong(KEY_CRL_CURSOR, 0L)
        val sinceParam = if (lastCursor > 0L) lastCursor.toString() else null

        Log.i(TAG, "Fetching /sync/updates (since=$sinceParam)...")
        val response = apiService.getSyncUpdates(sinceEpochSec = sinceParam)

        if (!response.isSuccessful || response.body() == null) {
            val errorBody = response.errorBody()?.string()
            throw Exception("GET /sync/updates failed (${response.code()}): ${errorBody ?: "Unknown error"}")
        }

        val updates = response.body()!!

        // 1. Apply Certificate Revocation List (CRL)
        if (updates.crl.isNotEmpty()) {
            Log.i(TAG, "Applying CRL updates (${updates.crl.size} revoked serials)")
            val allCached = cachedCertDao.getAllCerts()
            for (cached in allCached) {
                try {
                    val parsedCert = CertificateFormat.parseDeviceCertificate(cached.certBlob)
                    if (parsedCert.serialNumber in updates.crl) {
                        Log.w(TAG, "Purging revoked certificate for device ${cached.deviceId} (Serial: ${parsedCert.serialNumber})")
                        cachedCertDao.deleteCert(cached.deviceId)
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Apply Disaster Flags & Higher Risk Caps
        for (flag in updates.flags) {
            if (flag.active && flag.higherCap != null) {
                Log.i(TAG, "Active disaster mode flag detected (${flag.type}). Temporary higher cap: ${flag.higherCap}")
                val currentPurse = purseDao.getActivePurse()
                if (currentPurse != null && flag.higherCap.toLong() > currentPurse.cap) {
                    purseDao.updatePurse(currentPurse.copy(cap = flag.higherCap.toLong()))
                }
            }
        }

        // 3. Store Trust Attestations
        for (attestationPem in updates.trustAttestations) {
            try {
                val subjectId = certificateStore.getDeviceId() ?: "LOCAL_DEVICE"
                cachedTrustDao.insertTrust(
                    CachedTrust(
                        subjectId = subjectId,
                        trustScore = 1.0,
                        attestationBlobs = attestationPem,
                        cachedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache trust attestation: ${e.message}")
            }
        }

        // 4. Update sync cursors
        prefs.edit()
            .putLong(KEY_CRL_CURSOR, updates.crlCursor)
            .putLong(KEY_ESCROW_CURSOR, updates.escrowSettlementsCursor)
            .apply()

        updates
    }

    fun getLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
}
