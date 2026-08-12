package com.spark.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spark.wallet.engine.TrustPathResult
import com.spark.wallet.ui.theme.SparkGold
import com.spark.wallet.ui.theme.SparkTeal
import com.spark.wallet.ui.theme.SparkPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterpartyScreen(
    counterpartyId: String,
    trustPathResult: TrustPathResult?,
    isLoading: Boolean,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trust Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SparkPurple.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(40.dp),
                    tint = SparkPurple
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = counterpartyId.take(12) + "...",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Device ID: $counterpartyId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(color = SparkGold)
            } else if (trustPathResult != null) {
                TrustScoreCard(trustPathResult)
                Spacer(modifier = Modifier.height(24.dp))
                TrustPathList(trustPathResult.path)
            } else {
                Text(
                    text = "No trust data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun TrustScoreCard(result: TrustPathResult) {
    val cardColor = if (result.isHighRisk) Color(0xFFFFEbee) else Color(0xFFE8F5E9)
    val contentColor = if (result.isHighRisk) Color(0xFFD32F2F) else Color(0xFF388E3C)
    val icon = if (result.isHighRisk) Icons.Default.Warning else Icons.Default.CheckCircle

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Status",
                    tint = contentColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Trust Score: ${String.format("%.2f", result.score)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (result.isHighRisk) {
                Text(
                    text = "High Risk. No trusted path found within 3 hops or score too low.",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                Text(
                    text = "Trusted counterparty based on established settlement network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun TrustPathList(path: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Trust Path (${path.size - 1} hops)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        path.forEachIndexed { index, nodeId ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SparkTeal.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = SparkTeal
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    val label = when (index) {
                        0 -> "You"
                        path.size - 1 -> "Target Payee"
                        else -> "Intermediate Trust Node"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = nodeId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
            if (index < path.size - 1) {
                Box(
                    modifier = Modifier
                        .padding(start = 15.dp)
                        .width(2.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
            }
        }
    }
}
