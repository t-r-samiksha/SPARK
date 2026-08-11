package com.spark.wallet.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
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
}
