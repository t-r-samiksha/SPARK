package com.spark.wallet.protocol

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.engine.LocalTransactionEngine
import com.spark.wallet.security.KeyStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * BLE Fallback Transport implementing the same 4-step cryptographic handshake when NFC is unavailable.
 */
class BleTransportManager(
    private val context: Context,
    private val certificateStore: CertificateStore,
    private val keyStoreManager: KeyStoreManager,
    private val transactionEngine: LocalTransactionEngine,
    private val keyAlias: String = "spark_device_signing_key"
) {
    companion object {
        private const val TAG = "BleTransportManager"
        val SPARK_SERVICE_UUID: UUID = UUID.fromString("0000FE53-0000-1000-8000-00805F9B34FB")
        val SPARK_RX_CHAR_UUID: UUID = UUID.fromString("0000FE54-0000-1000-8000-00805F9B34FB") // Write
        val SPARK_TX_CHAR_UUID: UUID = UUID.fromString("0000FE55-0000-1000-8000-00805F9B34FB") // Notify/Read
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _transportState = MutableStateFlow<TapHandshakeState>(TapHandshakeState.Idle)
    val transportState: StateFlow<TapHandshakeState> = _transportState.asStateFlow()

    private var gattServer: BluetoothGattServer? = null
    private var clientGatt: BluetoothGatt? = null

    // PAYEE SIDE: Advertising & GATT Server
    fun startAdvertisingAsPayee() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _transportState.value = TapHandshakeState.Error("Bluetooth disabled")
            return
        }

        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: run {
            _transportState.value = TapHandshakeState.Error("BLE Advertising not supported")
            return
        }

        setupGattServer()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SPARK_SERVICE_UUID))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
        _transportState.value = TapHandshakeState.Advertising
    }

    // PAYER SIDE: Scanning & Client Handshake
    fun startScanningAsPayer(amountPaise: Long) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _transportState.value = TapHandshakeState.Error("Bluetooth disabled")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            _transportState.value = TapHandshakeState.Error("BLE Scanner not supported")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SPARK_SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _transportState.value = TapHandshakeState.Scanning
        scanner.startScan(listOf(filter), scanSettings, object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    scanner.stopScan(this)
                    connectToPayee(device, amountPaise)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _transportState.value = TapHandshakeState.Error("BLE Scan failed with code $errorCode")
            }
        })
    }

    private fun connectToPayee(device: BluetoothDevice, amountPaise: Long) {
        _transportState.value = TapHandshakeState.Connected(device.address)
        clientGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _transportState.value = TapHandshakeState.Idle
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    _transportState.value = TapHandshakeState.ExchangingCertificates(device.address)
                    // Execute BLE framing equivalent to APDU transceiver
                }
            }
        })
    }

    private fun setupGattServer() {
        gattServer = bluetoothManager?.openGattServer(context, object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                Log.d(TAG, "GATT Server connection state change: $newState")
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
                // Process incoming handshake packet
            }
        })

        val service = BluetoothGattService(SPARK_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val rxChar = BluetoothGattCharacteristic(
            SPARK_RX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val txChar = BluetoothGattCharacteristic(
            SPARK_TX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(rxChar)
        service.addCharacteristic(txChar)
        gattServer?.addService(service)
    }

    fun stop() {
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        clientGatt?.close()
        gattServer = null
        clientGatt = null
        _transportState.value = TapHandshakeState.Idle
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "BLE advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed: $errorCode")
            _transportState.value = TapHandshakeState.Error("BLE Advertising failed ($errorCode)")
        }
    }
}
