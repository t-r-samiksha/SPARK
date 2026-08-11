package com.spark.wallet.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spark.wallet.data.AppDatabase
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.PurseRepository
import com.spark.wallet.data.PurseRepositoryImpl
import com.spark.wallet.data.dao.LocalLedgerDao
import com.spark.wallet.data.dao.LocalPurseDao
import com.spark.wallet.data.entity.LocalPurse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val offlineAvailablePaise: Long = 0L,
    val totalBalancePaise: Long = 500000L, // 5000 INR simulated bank account balance + offline purse
    val currentCapPaise: Long = 200000L, // 2000 INR default cap
    val expiresAt: Long = 0L,
    val unsyncedCount: Int = 0,
    val isOnline: Boolean = true,
    val isLoading: Boolean = false,
    val isTopUpDialogVisible: Boolean = false,
    val recommendedCapPaise: Long = 200000L,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val activePurse: LocalPurse? = null
) {
    val isLowBalance: Boolean
        get() = activePurse != null && (offlineAvailablePaise < (currentCapPaise * 0.20) || offlineAvailablePaise <= 50000L)

    val isNearExpiry: Boolean
        get() = activePurse != null && expiresAt > 0 && (expiresAt - System.currentTimeMillis()) < (48 * 3600 * 1000L)
}

class HomeViewModel(
    private val purseRepository: PurseRepository,
    private val ledgerDao: LocalLedgerDao,
    private val certificateStore: CertificateStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observePurse()
        observeLedger()
        fetchRecommendation()
    }

    private fun observePurse() {
        viewModelScope.launch {
            purseRepository.activePurseFlow.collectLatest { purse ->
                _uiState.update { state ->
                    if (purse != null) {
                        state.copy(
                            offlineAvailablePaise = purse.remaining,
                            currentCapPaise = purse.cap,
                            expiresAt = purse.expiresAt,
                            activePurse = purse,
                            totalBalancePaise = 500000L + purse.remaining
                        )
                    } else {
                        state.copy(
                            offlineAvailablePaise = 0L,
                            activePurse = null
                        )
                    }
                }
            }
        }
    }

    private fun observeLedger() {
        viewModelScope.launch {
            ledgerDao.getAllEntriesFlow().collectLatest { entries ->
                val unsynced = entries.count { !it.synced }
                _uiState.update { it.copy(unsyncedCount = unsynced) }
            }
        }
    }

    fun fetchRecommendation() {
        viewModelScope.launch {
            purseRepository.getRecommendedLimit().onSuccess { cap ->
                _uiState.update { it.copy(recommendedCapPaise = cap) }
            }
        }
    }

    fun openTopUpDialog() {
        _uiState.update { it.copy(isTopUpDialogVisible = true, errorMessage = null) }
        fetchRecommendation()
    }

    fun closeTopUpDialog() {
        _uiState.update { it.copy(isTopUpDialogVisible = false) }
    }

    fun loadPurse(amountPaise: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = if (_uiState.value.activePurse == null) {
                purseRepository.loadPurse(amountPaise)
            } else {
                purseRepository.topUpPurse(amountPaise)
            }

            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isTopUpDialogVisible = false,
                        successMessage = "Purse loaded with ₹${amountPaise / 100} successfully!"
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load purse token"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    companion object {
        fun provideFactory(
            purseRepository: PurseRepository,
            ledgerDao: LocalLedgerDao,
            certificateStore: CertificateStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(purseRepository, ledgerDao, certificateStore) as T
            }
        }
    }
}
