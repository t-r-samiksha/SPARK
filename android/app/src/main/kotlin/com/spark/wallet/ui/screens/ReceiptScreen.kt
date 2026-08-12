package com.spark.wallet.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(onNavigateBack: () -> Unit) {
    val view = LocalView.current
    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipt") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Done") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Transaction Successful!", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Your payment was processed successfully.")
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onNavigateBack) { Text("Done") }
        }
    }
}
