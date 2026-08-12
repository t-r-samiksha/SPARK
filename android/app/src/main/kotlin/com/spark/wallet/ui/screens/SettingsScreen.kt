package com.spark.wallet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("General Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = { /* Limits */ }, modifier = Modifier.fillMaxWidth()) { Text("Limits & Preferences") }
            OutlinedButton(onClick = { /* Key/Attestation status */ }, modifier = Modifier.fillMaxWidth()) { Text("Key / Attestation Status") }
            OutlinedButton(onClick = { /* Language */ }, modifier = Modifier.fillMaxWidth()) { Text("Language") }
        }
    }
}
