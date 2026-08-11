package com.spark.wallet.network

import com.spark.wallet.protocol.SparkTransaction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Request payload for POST /api/v1/enroll.
 */
@Serializable
data class EnrollRequest(
    @SerialName("account_id") val accountId: String,
    @SerialName("public_key") val publicKey: String,
    @SerialName("attestation_blob") val attestationBlob: String
)

/**
 * Successful response from POST /api/v1/enroll returning the signed device certificate PEM.
 */
@Serializable
data class EnrollResponse(
    @SerialName("cert") val cert: String
)

/**
 * Response from GET /api/v1/limit/recommendation returning the AI-recommended spend limit.
 */
@Serializable
data class LimitRecommendationResponse(
    @SerialName("recommended_cap") val recommendedCap: String? = null,
    @SerialName("cap") val cap: String? = null
) {
    val effectiveCap: String get() = recommendedCap ?: cap ?: "200000"
}

/**
 * Request payload for POST /api/v1/purse/load.
 */
@Serializable
data class PurseLoadRequest(
    @SerialName("value") val value: String
)

/**
 * Response from POST /api/v1/purse/load returning the issued purse token PEM.
 */
@Serializable
data class PurseLoadResponse(
    @SerialName("purse_token") val purseToken: String
)

/**
 * Request payload for POST /api/v1/purse/topup.
 */
@Serializable
data class PurseTopUpRequest(
    @SerialName("token_id") val tokenId: String,
    @SerialName("amount") val amount: String
)

/**
 * Response from POST /api/v1/purse/topup.
 */
@Serializable
data class PurseTopUpResponse(
    @SerialName("purse_token") val purseToken: String? = null,
    @SerialName("status") val status: String? = null
)

/**
 * Request payload for POST /api/v1/sync/transactions.
 */
@Serializable
data class SyncTransactionsRequest(
    @SerialName("transactions") val transactions: List<SparkTransaction>
)

@Serializable
data class SettleResult(
    @SerialName("tx_id") val txId: String,
    @SerialName("status") val status: String, // "SETTLED" or "REJECTED"
    @SerialName("rejection_reason") val rejectionReason: String? = null
)

@Serializable
data class SettleIncident(
    @SerialName("incident_id") val incidentId: String? = null,
    @SerialName("type") val type: String,
    @SerialName("tx_id") val txId: String? = null
)

/**
 * Response from POST /api/v1/sync/transactions.
 */
@Serializable
data class SyncTransactionsResponse(
    @SerialName("results") val results: List<SettleResult> = emptyList(),
    @SerialName("incidents") val incidents: List<SettleIncident> = emptyList()
)

@Serializable
data class DisasterFlag(
    @SerialName("kind") val kind: String = "disaster",
    @SerialName("type") val type: String = "EMERGENCY",
    @SerialName("region_geo") val regionGeo: String? = null,
    @SerialName("active") val active: Boolean = true,
    @SerialName("higher_cap") val higherCap: String? = null,
    @SerialName("essential_only") val essentialOnly: Boolean = false,
    @SerialName("started_at") val startedAt: Long = 0L,
    @SerialName("ended_at") val endedAt: Long? = null
)

/**
 * Response from GET /api/v1/sync/updates.
 */
@Serializable
data class SyncUpdatesResponse(
    @SerialName("crl") val crl: List<String> = emptyList(),
    @SerialName("crl_cursor") val crlCursor: Long = 0L,
    @SerialName("escrow_settlements") val escrowSettlements: List<SparkTransaction> = emptyList(),
    @SerialName("escrow_settlements_cursor") val escrowSettlementsCursor: Long = 0L,
    @SerialName("flags") val flags: List<DisasterFlag> = emptyList(),
    @SerialName("recommended_cap") val recommendedCap: String? = null,
    @SerialName("trust_attestations") val trustAttestations: List<String> = emptyList()
)

/**
 * Standard error response structure from SPARK backend.
 */
@Serializable
data class ApiErrorResponse(
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null
)

/**
 * Retrofit interface for SPARK backend REST endpoints.
 */
interface SparkApiService {

    @POST("enroll")
    suspend fun enroll(
        @Body request: EnrollRequest
    ): Response<EnrollResponse>

    @GET("limit/recommendation")
    suspend fun getLimitRecommendation(): Response<LimitRecommendationResponse>

    @POST("purse/load")
    suspend fun loadPurse(
        @Body request: PurseLoadRequest
    ): Response<PurseLoadResponse>

    @POST("purse/topup")
    suspend fun topUpPurse(
        @Body request: PurseTopUpRequest
    ): Response<PurseTopUpResponse>

    @POST("sync/transactions")
    suspend fun syncTransactions(
        @Body request: SyncTransactionsRequest
    ): Response<SyncTransactionsResponse>

    @GET("sync/updates")
    suspend fun getSyncUpdates(
        @Query("since") sinceEpochSec: String? = null
    ): Response<SyncUpdatesResponse>
}
