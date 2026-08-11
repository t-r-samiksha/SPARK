package com.spark.wallet.data

import com.spark.wallet.network.EnrollRequest
import com.spark.wallet.network.EnrollResponse
import com.spark.wallet.network.NetworkClient
import com.spark.wallet.network.NetworkResult
import com.spark.wallet.network.SparkApiService
import com.spark.wallet.protocol.CanonicalSerializer
import com.spark.wallet.security.AttestationManager
import com.spark.wallet.security.KeyStoreManager
import com.spark.wallet.security.SecurityRepositoryImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

class EnrollmentFlowTest {

    private lateinit var certificateStore: CertificateStore
    private lateinit var keyAliasStore: KeyAliasStore
    private lateinit var keyStoreManager: KeyStoreManager
    private lateinit var attestationManager: AttestationManager
    private lateinit var securityRepository: SecurityRepositoryImpl

    @Before
    fun setup() {
        certificateStore = CertificateStore()
        keyAliasStore = KeyAliasStore()
        keyStoreManager = KeyStoreManager()
        attestationManager = AttestationManager(keyStoreManager)
        securityRepository = SecurityRepositoryImpl(keyStoreManager, keyAliasStore, attestationManager)
    }

    @Test
    fun testPublicKeyRawBase64UrlEncoding() {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val keyPair = kpg.generateKeyPair()

        val encodedPubKey = CertificateFormat.encodePublicKeyRawBase64Url(keyPair.public)

        assertNotNull(encodedPubKey)
        // 32 raw bytes in base64url unpadded is exactly 43 characters
        assertEquals("Base64url unpadded 32-byte key must be 43 characters", 43, encodedPubKey.length)
        assertFalse("Must not contain base64 padding '='", encodedPubKey.contains("="))
        assertFalse("Must not contain '+'", encodedPubKey.contains("+"))
        assertFalse("Must not contain '/'", encodedPubKey.contains("/"))

        val decoded = Base64.getUrlDecoder().decode(encodedPubKey)
        assertEquals(32, decoded.size)
    }

    @Test
    fun testCertificatePemWrappingAndParsing() {
        val sampleCert = SparkCertificate(
            deviceId = "1a2b3c4d-1111-4a2b-8c1d-2e3f4a5b6c7d",
            accountId = "2b3c4d5e-2222-4b3c-9d2e-3f4a5b6c7d8e",
            devicePublicKey = "luMvVjfGi9lT4_L0t1lrAeKzda6qfdamp4v_yQzpBVE",
            serialNumber = "SPARK-CERT-TEST0001",
            notBefore = "2026-08-01T00:00:00Z",
            notAfter = "2027-08-01T00:00:00Z",
            signature = "6QQtyJFvKopqPH5o7EmhbpC-mU_OwGsy1QVKWV5ZpV79R1KHXqHoVqMwBOyEQ5MOxd36b0NF4H4GAVczrxuzDw"
        )

        val pem = sampleCert.toPem()
        assertTrue(pem.startsWith("-----BEGIN SPARK DEVICE CERTIFICATE-----"))
        assertTrue(pem.endsWith("-----END SPARK DEVICE CERTIFICATE-----"))

        val parsed = CertificateFormat.parseDeviceCertificate(pem)
        assertEquals(sampleCert.deviceId, parsed.deviceId)
        assertEquals(sampleCert.accountId, parsed.accountId)
        assertEquals(sampleCert.devicePublicKey, parsed.devicePublicKey)
        assertEquals(sampleCert.serialNumber, parsed.serialNumber)
        assertEquals(sampleCert.signature, parsed.signature)
    }

    @Test
    fun testOfflineCertificateVerificationWithCachedBankRootKey() {
        // 1. Generate a mock Bank Root CA Ed25519 keypair
        val rootKpg = KeyPairGenerator.getInstance("Ed25519")
        val rootKeyPair = rootKpg.generateKeyPair()
        val rootPublicKeyBase64Url = CertificateFormat.encodePublicKeyRawBase64Url(rootKeyPair.public)

        // 2. Cache the Bank Root CA key in the local CertificateStore
        certificateStore.saveBankRootCertificate(rootPublicKeyBase64Url)
        assertEquals(rootPublicKeyBase64Url, certificateStore.getBankRootPublicKey())

        // 3. Issue a certificate signed by this Root CA
        val now = Instant.now()
        val notBefore = now.minus(1, ChronoUnit.HOURS).toString().split(".")[0] + "Z"
        val notAfter = now.plus(365, ChronoUnit.DAYS).toString().split(".")[0] + "Z"

        val deviceId = UUID.randomUUID().toString()
        val accountId = UUID.randomUUID().toString()
        val devicePubKey = "luMvVjfGi9lT4_L0t1lrAeKzda6qfdamp4v_yQzpBVE"

        val unsignedCert = SparkCertificate(
            deviceId = deviceId,
            accountId = accountId,
            devicePublicKey = devicePubKey,
            serialNumber = "SPARK-CERT-TEST1234",
            notBefore = notBefore,
            notAfter = notAfter,
            signature = ""
        )

        val canonicalBytes = unsignedCert.toCanonicalSigningBytes()
        val sigEngine = Signature.getInstance("Ed25519")
        sigEngine.initSign(rootKeyPair.private)
        sigEngine.update(canonicalBytes)
        val signatureBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(sigEngine.sign())

        val signedCert = unsignedCert.copy(signature = signatureBase64Url)
        val certPem = signedCert.toPem()

        // 4. Verify fully offline
        val result = certificateStore.verifyCertificateOffline(certPem, expectedDeviceId = deviceId)
        assertTrue("Certificate must be valid offline", result is CertificateValidationResult.Valid)

        // 5. Test verification fails if expected device ID doesn't match
        val wrongDeviceResult = certificateStore.verifyCertificateOffline(certPem, expectedDeviceId = "wrong-uuid")
        assertTrue("Verification must fail for mismatched device ID", wrongDeviceResult is CertificateValidationResult.Invalid)

        // 6. Test verification fails for tampered signature
        val tamperedCert = signedCert.copy(signature = signatureBase64Url.reversed())
        val tamperedResult = certificateStore.verifyCertificateOffline(tamperedCert.toPem(), expectedDeviceId = deviceId)
        assertTrue("Verification must fail for tampered signature", tamperedResult is CertificateValidationResult.Invalid)
    }

    @Test
    fun testEnrollmentRepositorySuccessFlow() {
        val testAccountId = "00000000-0000-4000-8000-000000000001"
        val testDeviceId = UUID.randomUUID().toString()

        // Create mock API service returning valid device cert PEM
        val mockService = object : SparkApiService {
            override suspend fun enroll(request: EnrollRequest): Response<EnrollResponse> {
                assertEquals(testAccountId, request.accountId)
                assertNotNull(request.publicKey)
                assertEquals(43, request.publicKey.length)
                assertTrue(request.attestationBlob.isNotEmpty())

                val cert = SparkCertificate(
                    deviceId = testDeviceId,
                    accountId = request.accountId,
                    devicePublicKey = request.publicKey,
                    serialNumber = "SPARK-CERT-ENROLLED001",
                    notBefore = "2026-08-01T00:00:00Z",
                    notAfter = "2027-08-01T00:00:00Z",
                    signature = "mockSignatureBase64UrlBytesValidFormatForTesting0000000000000000000"
                )
                return Response.success(EnrollResponse(cert = cert.toPem()))
            }

            override suspend fun getLimitRecommendation(): Response<com.spark.wallet.network.LimitRecommendationResponse> =
                throw UnsupportedOperationException()

            override suspend fun loadPurse(request: com.spark.wallet.network.PurseLoadRequest): Response<com.spark.wallet.network.PurseLoadResponse> =
                throw UnsupportedOperationException()

            override suspend fun topUpPurse(request: com.spark.wallet.network.PurseTopUpRequest): Response<com.spark.wallet.network.PurseTopUpResponse> =
                throw UnsupportedOperationException()
        }

        val networkClient = NetworkClient(customService = mockService)
        val enrollmentRepository = EnrollmentRepository(
            securityRepository = securityRepository,
            certificateStore = certificateStore,
            keyAliasStore = keyAliasStore,
            keyStoreManager = keyStoreManager,
            networkClient = networkClient,
            attestationManager = attestationManager
        )

        assertFalse("Before enrollment, device should not be enrolled", enrollmentRepository.isDeviceEnrolled())

        var enrollmentResult: EnrollmentResult? = null
        kotlinx.coroutines.runBlocking {
            enrollmentResult = enrollmentRepository.enrollDevice(testAccountId)
        }

        assertNotNull(enrollmentResult)
        assertTrue("Enrollment should succeed", enrollmentResult is EnrollmentResult.Success)

        val success = enrollmentResult as EnrollmentResult.Success
        assertEquals(testDeviceId, success.certificate.deviceId)
        assertEquals(testAccountId, success.certificate.accountId)

        // Verify state is stored
        assertTrue("Device should now be marked as enrolled", enrollmentRepository.isDeviceEnrolled())
        assertNotNull(certificateStore.getDeviceCertificate())
        assertEquals(testDeviceId, keyAliasStore.getDeviceId())
        assertNotNull(keyAliasStore.getKeyAlias())
    }

    @Test
    fun testEnrollmentRepositoryConflictAndErrorHandling() {
        val testAccountId = "00000000-0000-4000-8000-000000000001"

        // Mock 409 Conflict response
        val mockConflictService = object : SparkApiService {
            override suspend fun enroll(request: EnrollRequest): Response<EnrollResponse> {
                val errorBody = "{\"error\":\"device already enrolled\"}".toResponseBody("application/json".toMediaType())
                return Response.error(409, errorBody)
            }

            override suspend fun getLimitRecommendation(): Response<com.spark.wallet.network.LimitRecommendationResponse> =
                throw UnsupportedOperationException()

            override suspend fun loadPurse(request: com.spark.wallet.network.PurseLoadRequest): Response<com.spark.wallet.network.PurseLoadResponse> =
                throw UnsupportedOperationException()

            override suspend fun topUpPurse(request: com.spark.wallet.network.PurseTopUpRequest): Response<com.spark.wallet.network.PurseTopUpResponse> =
                throw UnsupportedOperationException()
        }

        val networkClient = NetworkClient(customService = mockConflictService)
        val enrollmentRepository = EnrollmentRepository(
            securityRepository = securityRepository,
            certificateStore = certificateStore,
            keyAliasStore = keyAliasStore,
            keyStoreManager = keyStoreManager,
            networkClient = networkClient,
            attestationManager = attestationManager
        )

        var result: EnrollmentResult? = null
        kotlinx.coroutines.runBlocking {
            result = enrollmentRepository.enrollDevice(testAccountId)
        }

        assertNotNull(result)
        assertTrue("Result should be Error", result is EnrollmentResult.Error)
        val error = result as EnrollmentResult.Error
        assertTrue("Must be marked as conflict", error.isConflict)
        assertTrue("Error message should contain 'already enrolled'", error.message.contains("already enrolled"))
    }
}
