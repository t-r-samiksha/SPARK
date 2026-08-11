package com.spark.wallet.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Storage manager for persistent device security identifiers.
 *
 * CRITICAL SECURITY INVARIANT:
 * This store persists ONLY the key alias and device metadata.
 * Raw cryptographic private key material NEVER enters or leaves this storage layer.
 */
class KeyAliasStore(private val context: Context? = null) {

    companion object {
        private const val PREFS_NAME = "spark_security_alias_prefs"
        private const val KEY_DEVICE_KEY_ALIAS = "device_key_alias"
        private const val KEY_DEVICE_ID = "device_id"
    }

    private var inMemoryAlias: String? = null
    private var inMemoryDeviceId: String? = null

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Stores ONLY the key alias reference in local persistent storage.
     */
    fun saveKeyAlias(alias: String) {
        require(alias.isNotBlank()) { "Key alias cannot be blank" }
        inMemoryAlias = alias
        prefs?.edit()?.putString(KEY_DEVICE_KEY_ALIAS, alias)?.apply()
    }

    /**
     * Retrieves the stored key alias.
     */
    fun getKeyAlias(): String? {
        return prefs?.getString(KEY_DEVICE_KEY_ALIAS, null) ?: inMemoryAlias
    }

    /**
     * Checks if a key alias is currently registered.
     */
    fun hasKeyAlias(): Boolean {
        return getKeyAlias() != null
    }

    /**
     * Clears the registered key alias.
     */
    fun clearKeyAlias() {
        inMemoryAlias = null
        prefs?.edit()?.remove(KEY_DEVICE_KEY_ALIAS)?.apply()
    }

    /**
     * Persists device ID identifier.
     */
    fun saveDeviceId(deviceId: String) {
        inMemoryDeviceId = deviceId
        prefs?.edit()?.putString(KEY_DEVICE_ID, deviceId)?.apply()
    }

    /**
     * Retrieves device ID.
     */
    fun getDeviceId(): String? {
        return prefs?.getString(KEY_DEVICE_ID, null) ?: inMemoryDeviceId
    }
}
