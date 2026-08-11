package com.spark.wallet.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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
}
