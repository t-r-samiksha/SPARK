package com.spark.wallet.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * High-level sealed result wrapping network responses with detailed failure classifications.
 */
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(
        val code: Int? = null,
        val errorMessage: String,
        val isNetworkError: Boolean = false,
        val isAttestationError: Boolean = false,
        val isConflict: Boolean = false
    ) : NetworkResult<Nothing>()
}

/**
 * Network client configuring OkHttp, Retrofit, and SPARK API calls.
 */
class NetworkClient(
    baseUrl: String = DEFAULT_BASE_URL,
    customService: SparkApiService? = null
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://spark-m1pt.onrender.com/api/v1/"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val apiService: SparkApiService by lazy {
        customService ?: createRetrofit(baseUrl).create(SparkApiService::class.java)
    }

    private fun createRetrofit(baseUrl: String): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()

        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    /**
     * Executes device enrollment via POST /api/v1/enroll.
     */
    suspend fun enrollDevice(
        accountId: String,
        publicKey: String,
        attestationBlob: String
    ): NetworkResult<EnrollResponse> {
        val request = EnrollRequest(
            accountId = accountId,
            publicKey = publicKey,
            attestationBlob = attestationBlob
        )

        return try {
            val response = apiService.enroll(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                val statusCode = response.code()
                val errorBodyString = response.errorBody()?.string()
                val parsedError = errorBodyString?.let { parseError(it) }
                val errorMsg = parsedError?.error ?: parsedError?.message ?: "Enrollment failed with status code $statusCode"

                val isAttestationError = errorMsg.contains("attestation", ignoreCase = true)
                val isConflict = statusCode == 409 || errorMsg.contains("already enrolled", ignoreCase = true)

                NetworkResult.Error(
                    code = statusCode,
                    errorMessage = errorMsg,
                    isNetworkError = false,
                    isAttestationError = isAttestationError,
                    isConflict = isConflict
                )
            }
        } catch (e: IOException) {
            NetworkResult.Error(
                code = null,
                errorMessage = "Network connection failed. Please check your internet connection: ${e.localizedMessage}",
                isNetworkError = true
            )
        } catch (e: Exception) {
            NetworkResult.Error(
                code = null,
                errorMessage = "Unexpected error during enrollment: ${e.localizedMessage ?: e.javaClass.simpleName}",
                isNetworkError = false
            )
        }
    }

    private fun parseError(jsonString: String): ApiErrorResponse? {
        return try {
            json.decodeFromString(ApiErrorResponse.serializer(), jsonString)
        } catch (_: Exception) {
            null
        }
    }
}
