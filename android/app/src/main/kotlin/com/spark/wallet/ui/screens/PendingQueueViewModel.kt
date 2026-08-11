package com.spark.wallet.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spark.wallet.data.dao.LocalLedgerDao
import com.spark.wallet.data.dao.PendingRelayDao
import com.spark.wallet.data.entity.LocalLedgerEntry
import com.spark.wallet.data.entity.PendingRelay
import com.spark.wallet.data.sync.SmsSyncManager
import com.spark.wallet.data.sync.SyncRepository
import com.spark.wallet.data.sync.SyncSummary
import com.spark.wallet.protocol.Party
import com.spark.wallet.protocol.SparkTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingQueueUiState(
    val unsyncedLedgerEntries: List<LocalLedgerEntry> = emptyList(),
    val pendingRelays: List<PendingRelay> = emptyList(),
    val isOnline: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val syncSummary: SyncSummary? = null,
    val userMessage: String? = null
) {
    val totalPendingCount: Int get() = unsyncedLedgerEntries.size + pendingRelays.size
}

class PendingQueueViewModel(
    private val context: Context,
    private val syncRepository: SyncRepository,
    private val ledgerDao: LocalLedgerDao,
    private val pendingRelayDao: PendingRelayDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendingQueueUiState())
    val uiState: StateFlow<PendingQueueUiState> = _uiState.asStateFlow()

    init {
        observePendingData()
        observeSyncState()
    }

    private fun observePendingData() {
        viewModelScope.launch {
            ledgerDao.getAllEntriesFlow().collectLatest { entries ->
                val unsynced = entries.filter { !it.synced }
                _uiState.update { it.copy(unsyncedLedgerEntries = unsynced) }
            }
        }

        viewModelScope.launch {
            pendingRelayDao.getAllPendingRelaysFlow().collectLatest { relays ->
                _uiState.update { it.copy(pendingRelays = relays) }
            }
        }

        viewModelScope.launch {
            syncRepository.isOnlineFlow.collectLatest { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncRepository.isSyncing.collectLatest { syncing ->
                _uiState.update { it.copy(isSyncing = syncing) }
            }
        }

        viewModelScope.launch {
            syncRepository.lastSyncSummary.collectLatest { summary ->
                _uiState.update {
                    it.copy(
                        syncSummary = summary,
                        lastSyncTimestamp = syncRepository.getLastSyncTimestamp()
                    )
                }
            }
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(userMessage = "Initiating sync with SPARK cloud...") }
            val result = syncRepository.fullSync()
            result.onSuccess { summary ->
                _uiState.update {
                    it.copy(userMessage = "Sync complete: ${summary.settledCount} transactions settled, ${summary.crlCount} CRL updates applied.")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(userMessage = "Sync failed: ${error.message}")
                }
            }
        }
    }

    fun sendViaSms(entry: LocalLedgerEntry) {
        viewModelScope.launch {
            val tx = SparkTransaction(
                txId = entry.txId,
                tokenId = "OFFLINE_PURSE",
                amount = entry.amount.toString(),
                payer = Party(deviceId = "LOCAL_DEVICE", accountId = "LOCAL_ACC", cert = "LOCAL_CERT"),
                payee = Party(deviceId = entry.counterpartyId, accountId = "PEER_ACC", cert = "PEER_CERT"),
                deviceCounter = entry.counter,
                prevTxHash = entry.prevHash,
                timestamp = entry.timestamp,
                signature = entry.signature
            )

            val smsResult = SmsSyncManager.sendTransactionViaSms(context, tx)
            if (smsResult.isSuccess) {
                _uiState.update { it.copy(userMessage = "Dispatched transaction via SMS (${smsResult.getOrThrow()} parts) to Gateway") }
            } else {
                _uiState.update { it.copy(userMessage = "Failed to send SMS: ${smsResult.exceptionOrNull()?.message}") }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    companion object {
        fun provideFactory(
            context: Context,
            syncRepository: SyncRepository,
            ledgerDao: LocalLedgerDao,
            pendingRelayDao: PendingRelayDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PendingQueueViewModel(context, syncRepository, ledgerDao, pendingRelayDao) as T
            }
        }
    }
}
