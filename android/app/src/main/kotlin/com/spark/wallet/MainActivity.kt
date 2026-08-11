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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spark.wallet.data.AppDatabase
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.EnrollmentRepository
import com.spark.wallet.data.KeyAliasStore
import com.spark.wallet.data.PurseRepositoryImpl
import com.spark.wallet.data.sync.SyncRepository
import com.spark.wallet.security.AttestationManager
import com.spark.wallet.security.KeyStoreManager
import com.spark.wallet.security.SecurityRepositoryImpl
import com.spark.wallet.ui.screens.HomeScreen
import com.spark.wallet.ui.screens.HomeViewModel
import com.spark.wallet.ui.screens.OnboardingScreen
import com.spark.wallet.ui.screens.OnboardingViewModel
import com.spark.wallet.ui.screens.PendingQueueScreen
import com.spark.wallet.ui.screens.PendingQueueViewModel
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

        val database = AppDatabase.getDatabase(applicationContext)
        val purseRepository = PurseRepositoryImpl(database.purseDao(), certificateStore = certificateStore)

        val syncRepository = SyncRepository(
            context = applicationContext,
            ledgerDao = database.ledgerDao(),
            pendingRelayDao = database.pendingRelayDao(),
            cachedCertDao = database.cachedCertDao(),
            cachedTrustDao = database.cachedTrustDao(),
            purseDao = database.purseDao(),
            certificateStore = certificateStore
        )

        // Start opportunistic background sync whenever any network path becomes available
        syncRepository.startOpportunisticSync(lifecycleScope)

        setContent {
            SparkWalletTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var isEnrolled by remember { mutableStateOf(certificateStore.isEnrolled()) }
                    var currentScreen by remember { mutableStateOf("home") }

                    if (isEnrolled) {
                        when (currentScreen) {
                            "pending_queue" -> {
                                val pendingQueueViewModel: PendingQueueViewModel = viewModel(
                                    factory = PendingQueueViewModel.provideFactory(
                                        context = applicationContext,
                                        syncRepository = syncRepository,
                                        ledgerDao = database.ledgerDao(),
                                        pendingRelayDao = database.pendingRelayDao()
                                    )
                                )
                                PendingQueueScreen(
                                    viewModel = pendingQueueViewModel,
                                    onNavigateBack = { currentScreen = "home" }
                                )
                            }
                            else -> {
                                val homeViewModel: HomeViewModel = viewModel(
                                    factory = HomeViewModel.provideFactory(
                                        purseRepository = purseRepository,
                                        ledgerDao = database.ledgerDao(),
                                        certificateStore = certificateStore
                                    )
                                )
                                HomeScreen(
                                    viewModel = homeViewModel,
                                    onNavigateToSync = { currentScreen = "pending_queue" }
                                )
                            }
                        }
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
