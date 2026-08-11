package com.spark.wallet.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.Signature
import kotlin.coroutines.resume

/**
 * Result status for biometric authentication.
 */
sealed class BiometricAuthResult {
    data class Success(val cryptoObject: BiometricPrompt.CryptoObject?) : BiometricAuthResult()
    data class Error(val errorCode: Int, val errorMessage: CharSequence) : BiometricAuthResult()
    object Failed : BiometricAuthResult()
    object Canceled : BiometricAuthResult()
}

/**
 * Biometric capability and hardware status on the device.
 */
sealed class BiometricCapability {
    object Available : BiometricCapability()
    object NoneEnrolled : BiometricCapability()
    object NoHardware : BiometricCapability()
    object HardwareUnavailable : BiometricCapability()
    data class Unsupported(val status: Int) : BiometricCapability()
}

/**
 * Configuration options for the BiometricPrompt dialog.
 */
data class BiometricPromptConfig(
    val title: String = "Authorize Transaction",
    val subtitle: String? = null,
    val description: String? = null,
    val negativeButtonText: String = "Cancel",
    val confirmationRequired: Boolean = true,
    val allowedAuthenticators: Int = BiometricManager.Authenticators.BIOMETRIC_STRONG
)

/**
 * Wraps AndroidX BiometricPrompt to provide clean coroutine-based biometric authentication
 * for securing cryptographic spend operations with hardware-backed signing keys.
 */
object BiometricAuthPrompt {

    /**
     * Checks if biometric authentication is available on the current device.
     */
    fun checkBiometricAvailability(
        context: Context,
        authenticators: Int = BiometricManager.Authenticators.BIOMETRIC_STRONG
    ): BiometricCapability {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.NoHardware
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapability.HardwareUnavailable
            else -> BiometricCapability.Unsupported(biometricManager.canAuthenticate(authenticators))
        }
    }

    /**
     * Shows a BiometricPrompt suspending until the user succeeds, fails, or cancels.
     * Optionally takes a [BiometricPrompt.CryptoObject] initialized with a hardware signing key.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        config: BiometricPromptConfig,
        cryptoObject: BiometricPrompt.CryptoObject? = null
    ): BiometricAuthResult = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) {
                    continuation.resume(BiometricAuthResult.Success(result.cryptoObject))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (continuation.isActive) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        continuation.resume(BiometricAuthResult.Canceled)
                    } else {
                        continuation.resume(BiometricAuthResult.Error(errorCode, errString))
                    }
                }
            }

            override fun onAuthenticationFailed() {
                // Biometric attempt failed, user can retry until max attempts reached
            }
        }

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(config.title)
            .setConfirmationRequired(config.confirmationRequired)
            .setAllowedAuthenticators(config.allowedAuthenticators)
            .setNegativeButtonText(config.negativeButtonText)

        config.subtitle?.let { promptInfoBuilder.setSubtitle(it) }
        config.description?.let { promptInfoBuilder.setDescription(it) }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }

        if (cryptoObject != null) {
            biometricPrompt.authenticate(promptInfoBuilder.build(), cryptoObject)
        } else {
            biometricPrompt.authenticate(promptInfoBuilder.build())
        }
    }

    /**
     * Convenience method to prompt biometric auth specifically for spending transactions.
     */
    suspend fun authenticateForSpend(
        activity: FragmentActivity,
        amountPaise: Long,
        recipientDeviceId: String? = null,
        signingSignature: Signature? = null
    ): BiometricAuthResult {
        val rupees = amountPaise / 100.0
        val formattedAmount = "₹ %.2f".format(java.util.Locale.US, rupees)
        val description = if (recipientDeviceId != null) {
            "Recipient: $recipientDeviceId"
        } else {
            "SPARK Offline Spend Authorization"
        }

        val config = BiometricPromptConfig(
            title = "Confirm Payment",
            subtitle = formattedAmount,
            description = description,
            negativeButtonText = "Cancel"
        )

        val cryptoObject = signingSignature?.let { BiometricPrompt.CryptoObject(it) }
        return authenticate(activity, config, cryptoObject)
    }
}
