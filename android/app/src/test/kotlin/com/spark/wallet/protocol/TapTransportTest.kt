package com.spark.wallet.protocol

import com.spark.wallet.data.CertificateFormat
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.CertificateValidationResult
import com.spark.wallet.data.SparkCertificate
import com.spark.wallet.data.dao.CachedCertDao
import com.spark.wallet.data.dao.LocalLedgerDao
import com.spark.wallet.data.dao.LocalPurseDao
import com.spark.wallet.data.entity.CachedCert
import com.spark.wallet.data.entity.LocalLedgerEntry
import com.spark.wallet.data.entity.LocalPurse
import com.spark.wallet.engine.LocalTransactionEngine
import com.spark.wallet.security.KeyStoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

class TapTransportTest {

    private val bankRootKeyAlias = "bank_root_ca_test_key"
    private val payerKeyAlias = "payer_device_signing_key"
    private val payeeKeyAlias = "payee_device_signing_key"

    private lateinit var keyStoreManager: KeyStoreManager

    private val payerDeviceId = UUID.randomUUID().toString()
    private val payeeDeviceId = UUID.randomUUID().toString()
    private val payerAccountId = "00000000-0000-4000-8000-000000000001"
    private val payeeAccountId = "00000000-0000-4000-8000-000000000002"

    private lateinit var payerCert: SparkCertificate
    private lateinit var payeeCert: SparkCertificate

    private lateinit var payerPurseDao: InMemoryPurseDao
    private lateinit var payerLedgerDao: InMemoryLedgerDao
    private lateinit var payerCertDao: InMemoryCachedCertDao

    private lateinit var payeePurseDao: InMemoryPurseDao
    private lateinit var payeeLedgerDao: InMemoryLedgerDao
    private lateinit var payeeCertDao: InMemoryCachedCertDao

    private lateinit var payerEngine: LocalTransactionEngine
    private lateinit var payeeEngine: LocalTransactionEngine

    private lateinit var payerCertStore: MockCertStore
    private lateinit var payeeCertStore: MockCertStore

    @Before
    fun setUp() {
        keyStoreManager = KeyStoreManager()

        // 1. Generate Bank Root CA Key and Device Keys
        keyStoreManager.generateEd25519KeyPair(alias = bankRootKeyAlias)
        val payerGen = keyStoreManager.generateEd25519KeyPair(alias = payerKeyAlias)
        val payeeGen = keyStoreManager.generateEd25519KeyPair(alias = payeeKeyAlias)

        val payerPubKeyBase64Url = CertificateFormat.encodePublicKeyRawBase64Url(payerGen.keyPair.public)
        val payeePubKeyBase64Url = CertificateFormat.encodePublicKeyRawBase64Url(payeeGen.keyPair.public)

        // 2. Issue Certificates signed by Bank Root CA
        payerCert = issueCert(payerDeviceId, payerAccountId, payerPubKeyBase64Url)
        payeeCert = issueCert(payeeDeviceId, payeeAccountId, payeePubKeyBase64Url)

        payerCertStore = MockCertStore(payerDeviceId, payerAccountId, payerCert.toPem())
        payeeCertStore = MockCertStore(payeeDeviceId, payeeAccountId, payeeCert.toPem())

        // 3. Setup DAOs
        payerPurseDao = InMemoryPurseDao()
        payerLedgerDao = InMemoryLedgerDao()
        payerCertDao = InMemoryCachedCertDao()

        payeePurseDao = InMemoryPurseDao()
        payeeLedgerDao = InMemoryLedgerDao()
        payeeCertDao = InMemoryCachedCertDao()

        // Seed Payer Purse (1,000 INR = 100,000 paise)
        runBlocking {
            payerPurseDao.insertPurse(
                LocalPurse(
                    tokenId = UUID.randomUUID().toString(),
                    cap = 100000L,
                    remaining = 100000L,
                    counterCurrent = 0L,
                    signedTokenBlob = "MOCK_TOKEN",
                    expiresAt = System.currentTimeMillis() + 86400000L
                )
            )
        }

        payerEngine = LocalTransactionEngine(
            purseDao = payerPurseDao,
            ledgerDao = payerLedgerDao,
            cachedCertDao = payerCertDao,
            certificateStore = payerCertStore,
            keyStoreManager = keyStoreManager,
            keyAlias = payerKeyAlias
        )

        payeeEngine = LocalTransactionEngine(
            purseDao = payeePurseDao,
            ledgerDao = payeeLedgerDao,
            cachedCertDao = payeeCertDao,
            certificateStore = payeeCertStore,
            keyStoreManager = keyStoreManager,
            keyAlias = payeeKeyAlias
        )
    }

    @Test
    fun testX25519EcdhAndAesGcmSessionAgreement() {
        // Party A (Payer)
        val partyAKeyPair = SessionCrypto.generateEphemeralKeyPair()
        // Party B (Payee)
        val partyBKeyPair = SessionCrypto.generateEphemeralKeyPair()

        // ECDH key agreement
        val partyAAesKey = SessionCrypto.deriveAesSessionKey(
            myPrivateKey = partyAKeyPair.keyPair.private,
            peerRawPublicKey = partyBKeyPair.rawPublicKeyBytes
        )

        val partyBAesKey = SessionCrypto.deriveAesSessionKey(
            myPrivateKey = partyBKeyPair.keyPair.private,
            peerRawPublicKey = partyAKeyPair.rawPublicKeyBytes
        )

        // Keys must be strictly identical
        assertEquals(
            Base64.getEncoder().encodeToString(partyAAesKey.encoded),
            Base64.getEncoder().encodeToString(partyBAesKey.encoded)
        )

        // Test AES-256-GCM Encryption by Party A and Decryption by Party B
        val plaintext = "SPARK_OFFLINE_SECRET_TRANSACTION_PAYLOAD".toByteArray(StandardCharsets.UTF_8)
        val encrypted = SessionCrypto.encryptAesGcm(partyAAesKey, plaintext)
        val decrypted = SessionCrypto.decryptAesGcm(partyBAesKey, encrypted)

        assertEquals(String(plaintext, StandardCharsets.UTF_8), String(decrypted, StandardCharsets.UTF_8))
    }

    @Test
    fun testFullFourStepApduHandshakeAndPaymentTransfer() = runBlocking {
        // Setup Payee HostApduService simulation
        SparkHostApduService.customCertificateStore = payeeCertStore
        SparkHostApduService.customKeyStoreManager = keyStoreManager
        SparkHostApduService.customTransactionEngine = payeeEngine
        SparkHostApduService.customKeyAlias = payeeKeyAlias

        val payeeApduService = SparkHostApduService()

        // Setup Payer Reader Mode simulation
        val payerReaderManager = NfcReaderModeManager(
            activity = null as? android.app.Activity ?: createMockActivity(),
            certificateStore = payerCertStore,
            keyStoreManager = keyStoreManager,
            transactionEngine = payerEngine,
            keyAlias = payerKeyAlias
        )

        val amountToPayPaise = 25000L // ₹250.00

        // Transceiver routing APDUs to payeeApduService
        val simulatedTransceiver: suspend (ByteArray) -> ByteArray = { apdu ->
            payeeApduService.processCommandApdu(apdu, null)
        }

        // Execute full 4-step APDU Handshake
        val result = payerReaderManager.executeApduHandshakeTransceiver(
            transceiver = simulatedTransceiver,
            amountPaise = amountToPayPaise
        )

        println("TAP TEST RESULT: $result")
        assertTrue("Tap payment should succeed: ${(result as? TapPaymentResult.Failure)?.reason}", result is TapPaymentResult.Success)
        val success = result as TapPaymentResult.Success

        assertEquals("25000", success.transaction.amount)
        assertEquals(payerDeviceId, success.transaction.payer.deviceId)
        assertEquals(payeeDeviceId, success.transaction.payee.deviceId)

        // Verify Payer local ledger and purse
        val payerPurse = payerPurseDao.getActivePurse()
        assertNotNull(payerPurse)
        assertEquals(75000L, payerPurse!!.remaining) // 100000 - 25000
        assertEquals(1L, payerPurse.counterCurrent)

        val payerLedger = payerLedgerDao.getAllEntries()
        assertEquals(1, payerLedger.size)
        assertEquals("out", payerLedger[0].direction)
        assertEquals(25000L, payerLedger[0].amount)

        // Verify Payee local ledger
        val payeeLedger = payeeLedgerDao.getAllEntries()
        assertEquals(1, payeeLedger.size)
        assertEquals("in", payeeLedger[0].direction)
        assertEquals(25000L, payeeLedger[0].amount)
        assertEquals(success.transactionHash, payeeLedger[0].hash)
    }

    @Test
    fun testQrFallbackInvoiceAndPaymentRoundtrip() = runBlocking {
        val amountPaise = 15000L // ₹150.00
        val payeeEphemeral = SessionCrypto.generateEphemeralKeyPair()
        val payeeChallenge = ByteArray(32).apply { java.security.SecureRandom().nextBytes(this) }

        // 1. Payee creates invoice
        val invoiceJson = QrTransportManager.createPayeeInvoice(
            myCertPem = payeeCert.toPem(),
            amountPaise = amountPaise,
            ephemeralKeyPair = payeeEphemeral,
            challenge = payeeChallenge
        )

        // 2. Payer processes invoice, creates payment
        val payerResult = QrTransportManager.processInvoiceAndCreatePayment(
            invoiceJson = invoiceJson,
            certificateStore = payerCertStore,
            keyStoreManager = keyStoreManager,
            transactionEngine = payerEngine,
            keyAlias = payerKeyAlias
        )
        assertTrue(payerResult.isSuccess)
        val (paymentPayloadJson, signedTx) = payerResult.getOrThrow()

        // 3. Payee verifies payment payload
        val parsedPayload = SparkApduProtocol.json.decodeFromString(
            QrPayerPayload.serializer(),
            paymentPayloadJson
        )

        val payeeDerivedAesKey = SessionCrypto.deriveAesSessionKey(
            myPrivateKey = payeeEphemeral.keyPair.private,
            peerRawPublicKey = Base64.getUrlDecoder().decode(parsedPayload.ephemeralX25519PubBase64Url)
        )

        val decryptedTxBytes = SessionCrypto.decryptAesGcm(
            payeeDerivedAesKey,
            Base64.getUrlDecoder().decode(parsedPayload.encryptedTxBase64Url)
        )
        val receivedTx = TransactionBuilder.deserialize(String(decryptedTxBytes, StandardCharsets.UTF_8))
        assertEquals(signedTx.txId, receivedTx.txId)
        assertEquals(amountPaise.toString(), receivedTx.amount)
    }

    private fun issueCert(deviceId: String, accountId: String, publicKeyBase64Url: String): SparkCertificate {
        val unsigned = SparkCertificate(
            deviceId = deviceId,
            accountId = accountId,
            devicePublicKey = publicKeyBase64Url,
            serialNumber = "SPARK-CERT-${UUID.randomUUID()}",
            notBefore = "2026-08-01T00:00:00Z",
            notAfter = "2027-08-01T00:00:00Z",
            signature = ""
        )
        val signingBytes = unsigned.toCanonicalSigningBytes()
        val sigBytes = keyStoreManager.sign(bankRootKeyAlias, signingBytes)
        val sigBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes)
        return unsigned.copy(signature = sigBase64Url)
    }

    private fun createMockActivity(): android.app.Activity {
        return object : android.app.Activity() {}
    }

    class MockCertStore(
        private val deviceId: String,
        private val accountId: String,
        private val certPem: String
    ) : CertificateStore(null) {
        override fun getDeviceId(): String = deviceId
        override fun getAccountId(): String = accountId
        override fun getDeviceCertificatePem(): String = certPem

        override fun verifyCertificateOffline(certPemToVerify: String, expectedDeviceId: String?): CertificateValidationResult {
            return try {
                val cert = CertificateFormat.parseDeviceCertificate(certPemToVerify)
                CertificateValidationResult.Valid(cert)
            } catch (e: Exception) {
                CertificateValidationResult.Invalid("Cert parsing failed: ${e.message}")
            }
        }
    }

    class InMemoryPurseDao : LocalPurseDao {
        private val map = mutableMapOf<String, LocalPurse>()
        override suspend fun insertPurse(purse: LocalPurse) { map[purse.tokenId] = purse }
        override suspend fun updatePurse(purse: LocalPurse) { map[purse.tokenId] = purse }
        override suspend fun getPurseByTokenId(tokenId: String): LocalPurse? = map[tokenId]
        override suspend fun getActivePurse(): LocalPurse? = map.values.firstOrNull { it.remaining > 0 }
        override fun getAllPursesFlow(): Flow<List<LocalPurse>> = flowOf(map.values.toList())
        override suspend fun getAllPurses(): List<LocalPurse> = map.values.toList()
        override suspend fun updateRemainingAndCounter(tokenId: String, remaining: Long, counter: Long) {
            val existing = map[tokenId]
            if (existing != null) {
                map[tokenId] = existing.copy(remaining = remaining, counterCurrent = counter)
            }
        }
        override suspend fun deletePurse(tokenId: String) { map.remove(tokenId) }
        override suspend fun clearAll() { map.clear() }
    }

    class InMemoryLedgerDao : LocalLedgerDao {
        private val list = mutableListOf<LocalLedgerEntry>()
        override suspend fun insertEntry(entry: LocalLedgerEntry) { list.add(entry) }
        override suspend fun insertAll(entries: List<LocalLedgerEntry>) { list.addAll(entries) }
        override suspend fun getEntryById(txId: String): LocalLedgerEntry? = list.firstOrNull { it.txId == txId }
        override suspend fun getLatestEntry(): LocalLedgerEntry? = list.lastOrNull()
        override suspend fun getAllEntries(): List<LocalLedgerEntry> = list.toList()
        override fun getAllEntriesFlow(): Flow<List<LocalLedgerEntry>> = flowOf(list.toList())
        override suspend fun getUnsyncedEntries(): List<LocalLedgerEntry> = list.filter { !it.synced }
        override suspend fun markSynced(txId: String) {
            val idx = list.indexOfFirst { it.txId == txId }
            if (idx != -1) list[idx] = list[idx].copy(synced = true)
        }
        override suspend fun markAllSynced(txIds: List<String>) {
            for (i in list.indices) {
                if (list[i].txId in txIds) list[i] = list[i].copy(synced = true)
            }
        }
        override suspend fun getEntriesForCounterparty(counterpartyId: String): List<LocalLedgerEntry> =
            list.filter { it.counterpartyId == counterpartyId }
        override suspend fun clearAll() { list.clear() }
    }

    class InMemoryCachedCertDao : CachedCertDao {
        private val map = mutableMapOf<String, CachedCert>()
        override suspend fun insertCert(cert: CachedCert) { map[cert.deviceId] = cert }
        override suspend fun insertAll(certs: List<CachedCert>) { certs.forEach { map[it.deviceId] = it } }
        override suspend fun getCertByDeviceId(deviceId: String): CachedCert? = map[deviceId]
        override suspend fun getAllCerts(): List<CachedCert> = map.values.toList()
        override fun getAllCertsFlow(): Flow<List<CachedCert>> = flowOf(map.values.toList())
        override suspend fun deleteCert(deviceId: String) { map.remove(deviceId) }
        override suspend fun deleteExpiredCerts(currentTime: Long): Int {
            val before = map.size
            map.entries.removeIf { it.value.expiresAt <= currentTime }
            return before - map.size
        }
        override suspend fun clearAll() { map.clear() }
    }
}
