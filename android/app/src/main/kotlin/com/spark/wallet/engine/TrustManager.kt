package com.spark.wallet.engine

import android.util.Log
import com.spark.wallet.data.AppDatabase
import com.spark.wallet.data.CertificateStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.util.LinkedList
import java.util.Queue
import java.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

@Serializable
data class TrustAttestation(
    @SerialName("subject_a") val subjectA: String,
    @SerialName("subject_b") val subjectB: String,
    @SerialName("settled_amount") val settledAmount: String,
    @SerialName("settlement_count") val settlementCount: Int,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("signature") val signature: String
)

data class TrustPathResult(
    val score: Double,
    val path: List<String>,
    val isValid: Boolean,
    val isHighRisk: Boolean
)

class TrustManager(
    private val cachedTrustDao: com.spark.wallet.data.dao.CachedTrustDao,
    private val certificateStore: CertificateStore
) {
    companion object {
        private const val TAG = "TrustManager"
        private const val MAX_HOPS = 3
        private const val DECAY_CONSTANT = 0.5
        private const val HIGH_RISK_THRESHOLD = 0.25
        
        private val json = Json { ignoreUnknownKeys = true }
    }

    /**
     * Finds a trust path of length <= 3 hops from payerId to payeeId.
     * Evaluates attestations and calculates a decayed score.
     */
    suspend fun findTrustPath(payerId: String, payeeId: String): TrustPathResult {
        if (payerId == payeeId) {
            return TrustPathResult(1.0, listOf(payerId), true, false)
        }

        val allTrusts = cachedTrustDao.getAllTrust()
        val graph = mutableMapOf<String, MutableList<Pair<String, TrustAttestation>>>()
        
        // Build adjacency list
        for (trustEntry in allTrusts) {
            try {
                val attestations: List<TrustAttestation> = json.decodeFromString(trustEntry.attestationBlobs)
                for (att in attestations) {
                    if (att.subjectA.isNotEmpty() && att.subjectB.isNotEmpty()) {
                        graph.getOrPut(att.subjectA) { mutableListOf() }.add(att.subjectB to att)
                        graph.getOrPut(att.subjectB) { mutableListOf() }.add(att.subjectA to att)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse attestations for subject ${trustEntry.subjectId}", e)
            }
        }

        // BFS to find shortest path
        val queue: Queue<List<String>> = LinkedList()
        queue.add(listOf(payerId))
        
        val visited = mutableSetOf<String>()
        visited.add(payerId)

        var foundPath: List<String>? = null
        var totalScore = 0.0

        while (queue.isNotEmpty()) {
            val path = queue.poll()
            if (path == null) continue
            
            val current = path.last()
            
            if (current == payeeId) {
                foundPath = path
                break
            }

            if (path.size - 1 >= MAX_HOPS) {
                continue // Reached max depth (path size includes start node, so hops = size - 1)
            }

            val neighbors = graph[current] ?: emptyList()
            for ((neighbor, att) in neighbors) {
                if (!visited.contains(neighbor)) {
                    // Verify signature offline (omitted for brevity, assume valid if in DB for now, but we will add logic)
                    visited.add(neighbor)
                    val newPath = path + neighbor
                    queue.add(newPath)
                }
            }
        }

        if (foundPath != null) {
            val hops = foundPath.size - 1
            // Basic decaying-weight score
            totalScore = 1.0 * Math.pow(DECAY_CONSTANT, hops.toDouble())
            
            // In a real implementation, we would multiply by normalized settledAmount/settlementCount.
            // For MVP, we'll use a simplified decay based on hops.
            
            val isHighRisk = totalScore < HIGH_RISK_THRESHOLD
            return TrustPathResult(totalScore, foundPath, true, isHighRisk)
        }

        return TrustPathResult(0.0, emptyList(), false, true)
    }

    private fun verifyAttestationSignature(attestation: TrustAttestation): Boolean {
        // The signature is over the canonical JSON of all fields EXCEPT signature.
        // We'd need the Bank Root PubKey to verify.
        // For MVP, if we reached here it's already in DB, but this is a placeholder.
        return true 
    }
}
