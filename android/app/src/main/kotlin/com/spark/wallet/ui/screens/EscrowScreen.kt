package com.spark.wallet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscrowScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escrow") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Escrow Manager", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = { /* Create Escrow */ }, modifier = Modifier.fillMaxWidth()) { Text("Create Escrow") }
            OutlinedButton(onClick = { /* Track Escrow */ }, modifier = Modifier.fillMaxWidth()) { Text("Track Escrows") }
            OutlinedButton(onClick = { /* Confirm Release */ }, modifier = Modifier.fillMaxWidth()) { Text("Confirm Release") }
            OutlinedButton(onClick = { /* Dispute */ }, modifier = Modifier.fillMaxWidth()) { Text("Dispute Escrow") }
        }
    }
}
