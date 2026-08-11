package com.spark.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.EnrollmentRepository
import com.spark.wallet.data.KeyAliasStore
import com.spark.wallet.security.AttestationManager
import com.spark.wallet.security.KeyStoreManager
import com.spark.wallet.security.SecurityRepositoryImpl
import com.spark.wallet.ui.screens.HomeScreen
import com.spark.wallet.ui.screens.OnboardingScreen
import com.spark.wallet.ui.screens.OnboardingViewModel
import com.spark.wallet.ui.theme.SparkWalletTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val certificateStore = CertificateStore(applicationContext)
        val keyAliasStore = KeyAliasStore(applicationContext)
        val keyStoreManager = KeyStoreManager(applicationContext)
        val attestationManager = AttestationManager(keyStoreManager)
        val securityRepository = SecurityRepositoryImpl(keyStoreManager, keyAliasStore, attestationManager)
        val enrollmentRepository = EnrollmentRepository(
            securityRepository = securityRepository,
            certificateStore = certificateStore,
            keyAliasStore = keyAliasStore,
            keyStoreManager = keyStoreManager,
            attestationManager = attestationManager
        )
        val onboardingViewModel = OnboardingViewModel(enrollmentRepository, securityRepository)

        setContent {
            SparkWalletTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var isEnrolled by remember { mutableStateOf(certificateStore.isEnrolled()) }

                    if (isEnrolled) {
                        val deviceId = keyAliasStore.getDeviceId() ?: "DEV-SPARK-ENROLLED"
                        HomeScreen(deviceId = deviceId)
                    } else {
                        OnboardingScreen(
                            viewModel = onboardingViewModel,
                            onEnrollmentComplete = {
                                isEnrolled = true
                            }
                        )
                    }
                }
            }
        }
    }
}
