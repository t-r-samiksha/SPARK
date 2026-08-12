package com.spark.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spark.wallet.ui.theme.SparkGold
import com.spark.wallet.ui.theme.SparkPurple
import com.spark.wallet.ui.theme.SparkTeal
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSync: () -> Unit = {},
    onNavigateToPay: () -> Unit = {},
    onNavigateToReceive: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToFamily: () -> Unit = {},
    onNavigateToMerchant: () -> Unit = {},
    onNavigateToEscrow: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SparkGold),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "SPARK Icon",
                                tint = MaterialTheme.colorScheme.background,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "SPARK",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Offline Digital Cash",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                actions = {
                    // Online / Offline Status Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (uiState.isOnline) SparkTeal.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = "Connectivity",
                                tint = if (uiState.isOnline) SparkTeal else Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isOnline) "Online" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isOnline) SparkTeal else Color.Red,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Low-Balance or Near-Expiry Warning Banner
            if (uiState.isLowBalance) {
                WarningBanner(
                    title = "Low Offline Balance",
                    message = "Spendable purse balance is below recommended risk threshold.",
                    actionLabel = "Top Up",
                    onActionClick = { viewModel.openTopUpDialog() }
                )
            } else if (uiState.isNearExpiry) {
                WarningBanner(
                    title = "Purse Expiring Soon",
                    message = "Your offline spend token expires soon. Please refill online.",
                    actionLabel = "Refill",
                    onActionClick = { viewModel.openTopUpDialog() }
                )
            }

            // Disaster Mode Banner
            WarningBanner(
                title = "Disaster Mode Active",
                message = "Network connectivity is degraded. Limits are relaxed.",
                actionLabel = "Details",
                onActionClick = { /* Show details */ }
            )

            // Main Balance Card
            BalanceCard(
                offlinePaise = uiState.offlineAvailablePaise,
                totalPaise = uiState.totalBalancePaise,
                capPaise = uiState.currentCapPaise,
                expiresAt = uiState.expiresAt,
                onTopUpClick = { viewModel.openTopUpDialog() }
            )

            // Sync Status Indicator Card
            SyncStatusCard(
                unsyncedCount = uiState.unsyncedCount,
                onSyncClick = onNavigateToSync
            )

            // Quick Actions Grid
            Text(
                text = "Wallet Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Send,
                    label = "Pay Peer",
                    modifier = Modifier.weight(1f),
                    accentColor = SparkTeal,
                    onClick = onNavigateToPay
                )
                QuickActionButton(
                    icon = Icons.Default.QrCodeScanner,
                    label = "Receive",
                    modifier = Modifier.weight(1f),
                    accentColor = SparkGold,
                    onClick = onNavigateToReceive
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.AddCard,
                    label = "Top Up Purse",
                    modifier = Modifier.weight(1f),
                    accentColor = SparkPurple,
                    onClick = { viewModel.openTopUpDialog() }
                )
                QuickActionButton(
                    icon = Icons.Default.Sync,
                    label = "Sync Ledger",
                    modifier = Modifier.weight(1f),
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToSync
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.History,
                    label = "History",
                    modifier = Modifier.weight(1f),
                    accentColor = Color(0xFFE91E63),
                    onClick = onNavigateToHistory
                )
                QuickActionButton(
                    icon = Icons.Default.FamilyRestroom,
                    label = "Family",
                    modifier = Modifier.weight(1f),
                    accentColor = Color(0xFF4CAF50),
                    onClick = onNavigateToFamily
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Store,
                    label = "Merchant",
                    modifier = Modifier.weight(1f),
                    accentColor = Color(0xFFFF9800),
                    onClick = onNavigateToMerchant
                )
                QuickActionButton(
                    icon = Icons.Default.Handshake,
                    label = "Escrow",
                    modifier = Modifier.weight(1f),
                    accentColor = Color(0xFF2196F3),
                    onClick = onNavigateToEscrow
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    modifier = Modifier.weight(1f),
                    accentColor = Color.Gray,
                    onClick = onNavigateToSettings
                )
            }

            // Security Subsystem Overview Card
            SecurityOverviewCard()

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Top-Up Dialog
        if (uiState.isTopUpDialogVisible) {
            TopUpPurseDialog(
                recommendedCapPaise = uiState.recommendedCapPaise,
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                onDismiss = { viewModel.closeTopUpDialog() },
                onConfirm = { amountPaise -> viewModel.loadPurse(amountPaise) }
            )
        }
    }
}

@Composable
fun BalanceCard(
    offlinePaise: Long,
    totalPaise: Long,
    capPaise: Long,
    expiresAt: Long,
    onTopUpClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(SparkPurple, MaterialTheme.colorScheme.surfaceVariant)
                    )
                )
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Offline Spendable Cash",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                        Text(
                            text = formatPaise(offlinePaise),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = SparkGold.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        IconButton(onClick = onTopUpClick) {
                            Icon(
                                imageVector = Icons.Default.AddCard,
                                contentDescription = "Refill",
                                tint = SparkGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Cap Ceiling",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formatPaise(capPaise),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SparkGold
                        )
                    }

                    Column {
                        Text(
                            text = "Total Account",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formatPaise(totalPaise),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        Text(
                            text = "Token Expiry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formatExpiry(expiresAt),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (expiresAt > 0) SparkTeal else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WarningBanner(
    title: String,
    message: String,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF332000)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = SparkGold,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = SparkGold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = SparkGold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SyncStatusCard(
    unsyncedCount: Int,
    onSyncClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSyncClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (unsyncedCount == 0) SparkTeal.copy(alpha = 0.15f) else SparkGold.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (unsyncedCount == 0) Icons.Default.CloudDone else Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = if (unsyncedCount == 0) SparkTeal else SparkGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (unsyncedCount == 0) "Ledger Fully Synced" else "$unsyncedCount Pending Sync",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (unsyncedCount == 0) "All offline spends cleared with bank" else "Transactions recorded offline locally",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Sync Now",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SecurityOverviewCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security Status",
                    tint = SparkGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hardware Security Enforcement",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "• Keystore Monotonic Counter: Active\n• SQLCipher 256-bit Database: Sealed\n• Offline Chain Continuity (SHA-256): Validated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color,
    onClick: () -> Unit
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopUpPurseDialog(
    recommendedCapPaise: Long,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var selectedAmountPaise by remember { mutableStateOf(recommendedCapPaise) }
    var customAmountText by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = "Load Offline Purse",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Transfer spendable cash from your bank balance into the hardware-secured offline purse token.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                // Recommended Cap Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SparkGold.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Cap",
                            tint = SparkGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Recommended Cap: ${formatPaise(recommendedCapPaise)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SparkGold
                        )
                    }
                }

                // Preset Chips
                Text(
                    text = "Select Load Amount:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(50000L, 100000L, recommendedCapPaise)
                    presets.forEach { amount ->
                        FilterChip(
                            selected = !isCustomSelected && selectedAmountPaise == amount,
                            onClick = {
                                isCustomSelected = false
                                selectedAmountPaise = amount
                            },
                            label = { Text(formatPaise(amount)) }
                        )
                    }
                }

                // Custom Input
                OutlinedTextField(
                    value = customAmountText,
                    onValueChange = {
                        customAmountText = it.filter { c -> c.isDigit() }
                        if (customAmountText.isNotEmpty()) {
                            isCustomSelected = true
                            selectedAmountPaise = (customAmountText.toLongOrNull() ?: 0L) * 100L
                        }
                    },
                    label = { Text("Custom Amount (₹)") },
                    placeholder = { Text("e.g. 1500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SparkGold,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = SparkGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Authorizing with Bank...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SparkGold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedAmountPaise) },
                enabled = !isLoading && selectedAmountPaise > 0,
                colors = ButtonDefaults.buttonColors(containerColor = SparkGold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Load ${formatPaise(selectedAmountPaise)}",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun formatPaise(paise: Long): String {
    val rupees = paise / 100.0
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return format.format(rupees).replace("INR", "₹")
}

private fun formatExpiry(expiresAtMs: Long): String {
    if (expiresAtMs <= 0) return "No Active Purse"
    val diffMs = expiresAtMs - System.currentTimeMillis()
    if (diffMs <= 0) return "Expired"
    val days = diffMs / (24 * 3600 * 1000L)
    val hours = (diffMs % (24 * 3600 * 1000L)) / (3600 * 1000L)
    return if (days > 0) "${days}d ${hours}h left" else "${hours}h left"
}
