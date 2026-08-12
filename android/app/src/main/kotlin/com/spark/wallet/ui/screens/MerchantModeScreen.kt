package com.spark.wallet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantModeScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merchant Mode") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("HCE Accept", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            var isMerchantModeEnabled by remember { mutableStateOf(false) }
            Row {
                Text("Enable Merchant Mode:")
                Spacer(modifier = Modifier.width(16.dp))
                Switch(checked = isMerchantModeEnabled, onCheckedChange = { isMerchantModeEnabled = it })
            }
        }
    }
}
