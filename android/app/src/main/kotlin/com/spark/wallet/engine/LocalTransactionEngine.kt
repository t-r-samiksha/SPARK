package com.spark.wallet.engine

import com.spark.wallet.data.AppDatabase
import com.spark.wallet.data.CertificateStore
import com.spark.wallet.data.CertificateValidationResult
import com.spark.wallet.data.dao.CachedCertDao
import com.spark.wallet.data.dao.LocalLedgerDao
import com.spark.wallet.data.dao.LocalPurseDao
import com.spark.wallet.data.entity.CachedCert
import com.spark.wallet.data.entity.LocalLedgerEntry
import com.spark.wallet.data.entity.LocalPurse
import com.spark.wallet.protocol.CanonicalSerializer
import com.spark.wallet.protocol.Party
import com.spark.wallet.protocol.SparkTransaction
import com.spark.wallet.security.KeyStoreManager
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.UUID

sealed class TransactionException(message: String) : Exception(message) {
    class InsufficientFunds(val available: Long, val requested: Long) :
        TransactionException("Insufficient purse balance: available $available paise, requested $requested paise")

    class NoActivePurse : TransactionException("No active purse token found with available funds")
    class PurseExpired(val expiry: Long) : TransactionException("Purse token has expired at $expiry")
    class TimestampOutsideWindow(val txTimestamp: Long, val currentTimestamp: Long, val window: Long) :
        TransactionException("Transaction timestamp $txTimestamp is outside allowed replay window ($window s) relative to clock $currentTimestamp")

    class StaleOrReplayedCounter(val receivedCounter: Long, val lastSeenCounter: Long) :
        TransactionException("Transaction device_counter ($receivedCounter) is not strictly greater than last seen counter ($lastSeenCounter)")

    class InvalidCertificate(val reason: String) : TransactionException("Payer device certificate is invalid: $reason")
    class InvalidSignature : TransactionException("Transaction signature verification failed")
}

data class ReceiveOutcome(
    val transaction: SparkTransaction,
    val transactionHash: String,
    val isAccepted: Boolean = true
)

/**
 * Local Transaction Engine managing offline spend, receive, signature generation,
 * cryptographic hash chaining, and replay protection.
 */
class LocalTransactionEngine(
    private val purseDao: LocalPurseDao,
    private val ledgerDao: LocalLedgerDao,
    private val cachedCertDao: CachedCertDao,
    private val certificateStore: CertificateStore,
    private val keyStoreManager: KeyStoreManager,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val replayWindowSeconds: Long = DEFAULT_REPLAY_WINDOW_SECONDS
) {
    constructor(
        database: AppDatabase,
        certificateStore: CertificateStore,
        keyStoreManager: KeyStoreManager,
        keyAlias: String = DEFAULT_KEY_ALIAS,
        replayWindowSeconds: Long = DEFAULT_REPLAY_WINDOW_SECONDS
    ) : this(
        purseDao = database.purseDao(),
        ledgerDao = database.ledgerDao(),
        cachedCertDao = database.cachedCertDao(),
        certificateStore = certificateStore,
        keyStoreManager = keyStoreManager,
        keyAlias = keyAlias,
        replayWindowSeconds = replayWindowSeconds
    )

    companion object {
        const val DEFAULT_KEY_ALIAS = "spark_device_signing_key"
        const val DEFAULT_REPLAY_WINDOW_SECONDS = 300L // 5 minutes
    }

    /**
     * Stored next prev_tx_hash to use in-memory / cache to avoid recomputing lazily later.
     */
    @Volatile
    private var nextPrevTxHashToUse: String? = null

    /**
     * Builds, signs, and records an outgoing payment from the device's local purse.
     *
     * Invariants:
     * 1. `prev_tx_hash` for transaction N+1 is SHA-256 of the canonical JSON of transaction N
     *    INCLUDING its signature field.
     * 2. Atomic decrement of `local_purse.remaining` and `counter_current` at spend time.
     * 3. Append to `local_ledger` as direction "out".
     * 4. Precomputes and stores `nextPrevTxHashToUse` immediately for the next transaction.
     */
    suspend fun buildTransaction(
        amountPaise: Long,
        payee: Party,
        tokenIdOverride: String? = null,
        timestampEpochSeconds: Long = System.currentTimeMillis() / 1000
    ): Result<SparkTransaction> {
        return try {
            // 1. Fetch active purse
            val purse = if (tokenIdOverride != null) {
                purseDao.getPurseByTokenId(tokenIdOverride) ?: throw TransactionException.NoActivePurse()
            } else {
                purseDao.getActivePurse() ?: throw TransactionException.NoActivePurse()
            }

            // 2. Validate balance and expiration
            if (purse.expiresAt <= System.currentTimeMillis()) {
                throw TransactionException.PurseExpired(purse.expiresAt)
            }
            if (purse.remaining < amountPaise) {
                throw TransactionException.InsufficientFunds(purse.remaining, amountPaise)
            }

            // 3. Increment counter and determine prev_tx_hash
            val newCounter = purse.counterCurrent + 1
            val prevTxHash = if (nextPrevTxHashToUse != null) {
                nextPrevTxHashToUse
            } else {
                // Check latest outgoing transaction in ledger for this token
                val latestTx = ledgerDao.getLatestEntry()
                latestTx?.hash
            }

            // 4. Construct payer Party
            val payerDeviceId = certificateStore.getDeviceId() ?: purse.tokenId
            val payerAccountId = certificateStore.getAccountId() ?: "00000000-0000-4000-8000-000000000001"
            val payerCertPem = certificateStore.getDeviceCertificatePem() ?: purse.signedTokenBlob

            val payerParty = Party(
                deviceId = payerDeviceId,
                accountId = payerAccountId,
                cert = payerCertPem
            )

            // 5. Build unsigned transaction
            val txId = UUID.randomUUID().toString()
            val unsignedTx = SparkTransaction(
                txId = txId,
                tokenId = purse.tokenId,
                amount = amountPaise.toString(),
                payer = payerParty,
                payee = payee,
                deviceCounter = newCounter,
                prevTxHash = prevTxHash,
                timestamp = timestampEpochSeconds,
                signature = ""
            )

            // 6. Sign pre-signature canonical bytes (MINUS signature field)
            val signingBytes = CanonicalSerializer.canonicalizeForSigning(unsignedTx)
            val signatureBase64Url = keyStoreManager.sign(keyAlias, signingBytes).let {
                Base64.getUrlEncoder().withoutPadding().encodeToString(it)
            }

            val signedTx = unsignedTx.copy(signature = signatureBase64Url)

            // 7. Compute chaining hash of complete signed transaction (INCLUDING signature field)
            val thisTxHash = CanonicalSerializer.computeTransactionHash(signedTx)

            // 8. Atomic Database Updates: Decrement purse and append to ledger
            val newRemaining = purse.remaining - amountPaise
            purseDao.updateRemainingAndCounter(purse.tokenId, newRemaining, newCounter)

            val ledgerEntry = LocalLedgerEntry(
                txId = signedTx.txId,
                direction = "out",
                counterpartyId = payee.deviceId,
                amount = amountPaise,
                counter = newCounter,
                prevHash = prevTxHash,
                hash = thisTxHash,
                signature = signedTx.signature,
                timestamp = timestampEpochSeconds,
                synced = false
            )
            ledgerDao.insertEntry(ledgerEntry)

            // 9. Store thisTxHash as next prev_tx_hash immediately
            nextPrevTxHashToUse = thisTxHash

            Result.success(signedTx)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Receives, validates, and records an incoming payment from a counterparty.
     *
     * Invariants:
     * 1. Replay protection: Rejects if timestamp is outside replayWindowSeconds.
     * 2. Counter replay protection: Rejects if device_counter is not strictly greater than
     *    the last seen counter for this payer.
     * 3. Offline Bank Root CA trust verification on payer certificate.
     * 4. Ed25519 signature verification against payer's public key.
     * 5. Append to `local_ledger` as direction "in".
     */
    suspend fun receiveTransaction(
        tx: SparkTransaction,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000
    ): Result<ReceiveOutcome> {
        return try {
            // 1. Replay Protection: Timestamp window check
            val clockDifference = Math.abs(nowEpochSeconds - tx.timestamp)
            if (clockDifference > replayWindowSeconds) {
                throw TransactionException.TimestampOutsideWindow(
                    txTimestamp = tx.timestamp,
                    currentTimestamp = nowEpochSeconds,
                    window = replayWindowSeconds
                )
            }

            // 2. Replay Protection: Monotonic device_counter check per payer
            val payerEntries = ledgerDao.getEntriesForCounterparty(tx.payer.deviceId)
                .filter { it.direction == "in" }
            val lastSeenCounter = payerEntries.maxOfOrNull { it.counter } ?: 0L

            if (payerEntries.isNotEmpty() && tx.deviceCounter <= lastSeenCounter) {
                throw TransactionException.StaleOrReplayedCounter(
                    receivedCounter = tx.deviceCounter,
                    lastSeenCounter = lastSeenCounter
                )
            }

            // 3. Offline Certificate Trust Verification
            val certVerification = certificateStore.verifyCertificateOffline(tx.payer.cert, expectedDeviceId = tx.payer.deviceId)
            val certObj = when (certVerification) {
                is CertificateValidationResult.Valid -> certVerification.certificate
                is CertificateValidationResult.Invalid -> throw TransactionException.InvalidCertificate(certVerification.reason)
            }

            // 4. Ed25519 Signature Verification
            val signingBytes = CanonicalSerializer.canonicalizeForSigning(tx)
            val sigBytes = Base64.getUrlDecoder().decode(tx.signature)
            val isSignatureValid = verifyEd25519Signature(
                publicKeyBase64Url = certObj.devicePublicKey,
                data = signingBytes,
                signatureBytes = sigBytes
            )

            if (!isSignatureValid) {
                throw TransactionException.InvalidSignature()
            }

            // 5. Compute this transaction hash
            val thisTxHash = CanonicalSerializer.computeTransactionHash(tx)

            // 6. Record to local ledger and cache certificate
            val ledgerEntry = LocalLedgerEntry(
                txId = tx.txId,
                direction = "in",
                counterpartyId = tx.payer.deviceId,
                amount = tx.amount.toLong(),
                counter = tx.deviceCounter,
                prevHash = tx.prevTxHash,
                hash = thisTxHash,
                signature = tx.signature,
                timestamp = tx.timestamp,
                synced = false
            )
            ledgerDao.insertEntry(ledgerEntry)

            val expiresAtEpochMs = try {
                Instant.parse(certObj.notAfter).toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis() + 86400000L * 30
            }

            cachedCertDao.insertCert(
                CachedCert(
                    deviceId = certObj.deviceId,
                    publicKey = certObj.devicePublicKey,
                    certBlob = tx.payer.cert,
                    expiresAt = expiresAtEpochMs
                )
            )

            Result.success(
                ReceiveOutcome(
                    transaction = tx,
                    transactionHash = thisTxHash,
                    isAccepted = true
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resets the cached next prev_tx_hash (e.g. on new purse load or sync).
     */
    fun resetNextPrevTxHash(hash: String? = null) {
        nextPrevTxHashToUse = hash
    }

    /**
     * Gets the currently staged next prev_tx_hash to use.
     */
    fun getNextPrevTxHash(): String? = nextPrevTxHashToUse

    private fun verifyEd25519Signature(
        publicKeyBase64Url: String,
        data: ByteArray,
        signatureBytes: ByteArray
    ): Boolean {
        return try {
            val rawPubKeyBytes = Base64.getUrlDecoder().decode(publicKeyBase64Url)
            val derHeader = byteArrayOf(
                0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
            )
            val spec = X509EncodedKeySpec(derHeader + rawPubKeyBytes)
            val kf = try { KeyFactory.getInstance("Ed25519") } catch (_: Exception) { KeyFactory.getInstance("EdDSA") }
            val publicKey = kf.generatePublic(spec)

            val verifier = try { Signature.getInstance("Ed25519") } catch (_: Exception) { Signature.getInstance("EdDSA") }
            verifier.initVerify(publicKey)
            verifier.update(data)
            verifier.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }
}
