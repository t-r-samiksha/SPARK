package com.spark.wallet.protocol

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed class TapHandshakeState {
    object Idle : TapHandshakeState()
    object Advertising : TapHandshakeState()
    object Scanning : TapHandshakeState()
    data class Connected(val peerDeviceId: String) : TapHandshakeState()
    data class ExchangingCertificates(val peerDeviceId: String) : TapHandshakeState()
    data class PaymentTransferred(val txId: String) : TapHandshakeState()
    data class Error(val message: String) : TapHandshakeState()
}

/**
 * Manages the offline tap-to-pay handshake over BLE / NFC.
 */
class TapHandshake(
    private val context: Context,
    private val localDeviceId: String
) {
    private val _state = MutableStateFlow<TapHandshakeState>(TapHandshakeState.Idle)
    val state: StateFlow<TapHandshakeState> = _state.asStateFlow()

    fun startAdvertising(serviceUuid: UUID) {
        _state.value = TapHandshakeState.Advertising
        // Nordic BLE advertiser setup
    }

    fun startScanning(serviceUuid: UUID) {
        _state.value = TapHandshakeState.Scanning
        // Nordic BLE scanner setup
    }

    fun stop() {
        _state.value = TapHandshakeState.Idle
    }
}
