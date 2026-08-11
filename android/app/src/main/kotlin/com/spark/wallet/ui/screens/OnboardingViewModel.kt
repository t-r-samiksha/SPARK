package com.spark.wallet.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spark.wallet.data.EnrollmentRepository
import com.spark.wallet.data.EnrollmentResult
import com.spark.wallet.data.SparkCertificate
import com.spark.wallet.security.AttestationStatus
import com.spark.wallet.security.SecurityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class OnboardingUiState {
    object Idle : OnboardingUiState()
    data class Loading(val currentStep: String) : OnboardingUiState()
    data class Success(val certificate: SparkCertificate, val certificatePem: String) : OnboardingUiState()
    data class Error(
        val message: String,
        val isNetworkError: Boolean = false,
        val isAttestationError: Boolean = false,
        val isConflict: Boolean = false
    ) : OnboardingUiState()
}

class OnboardingViewModel(
    private val enrollmentRepository: EnrollmentRepository,
    private val securityRepository: SecurityRepository
) : ViewModel() {

    companion object {
        const val DEMO_ACCOUNT_ID = "00000000-0000-4000-8000-000000000001"
        private val UUID_V4_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    }

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _accountId = MutableStateFlow(DEMO_ACCOUNT_ID)
    val accountId: StateFlow<String> = _accountId.asStateFlow()

    private val _attestationStatus = MutableStateFlow(securityRepository.getAttestationStatus())
    val attestationStatus: StateFlow<AttestationStatus> = _attestationStatus.asStateFlow()

    fun onAccountIdChanged(id: String) {
        _accountId.value = id.trim()
    }

    fun useDemoAccount() {
        _accountId.value = DEMO_ACCOUNT_ID
    }

    fun generateNewRandomAccount() {
        _accountId.value = UUID.randomUUID().toString().lowercase()
    }

    fun isAccountIdValid(): Boolean {
        return UUID_V4_PATTERN.matches(_accountId.value.trim())
    }

    fun startEnrollment() {
        val targetAccountId = _accountId.value.trim().lowercase()

        if (!UUID_V4_PATTERN.matches(targetAccountId)) {
            _uiState.value = OnboardingUiState.Error(
                message = "Account ID must be a valid UUID v4 (e.g. 00000000-0000-4000-8000-000000000001)"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Loading("Generating hardware-backed Ed25519 keys inside StrongBox/TEE...")

            val result = enrollmentRepository.enrollDevice(targetAccountId)

            when (result) {
                is EnrollmentResult.Success -> {
                    _attestationStatus.value = securityRepository.getAttestationStatus()
                    _uiState.value = OnboardingUiState.Success(
                        certificate = result.certificate,
                        certificatePem = result.certificatePem
                    )
                }
                is EnrollmentResult.Error -> {
                    _uiState.value = OnboardingUiState.Error(
                        message = result.message,
                        isNetworkError = result.isNetworkError,
                        isAttestationError = result.isAttestationError,
                        isConflict = result.isConflict
                    )
                }
            }
        }
    }

    fun resetError() {
        _uiState.value = OnboardingUiState.Idle
    }
}
