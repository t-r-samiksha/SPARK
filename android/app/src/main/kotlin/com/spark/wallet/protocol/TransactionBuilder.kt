package com.spark.wallet.protocol

import com.spark.wallet.security.KeyStoreManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Base64
import java.util.UUID

@Serializable
data class Party(
    @SerialName("device_id") val deviceId: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("cert") val cert: String
)

@Serializable
data class SparkTransaction(
    @SerialName("tx_id") val txId: String,
    @SerialName("token_id") val tokenId: String,
    @SerialName("amount") val amount: String,
    @SerialName("payer") val payer: Party,
    @SerialName("payee") val payee: Party,
    @SerialName("device_counter") val deviceCounter: Long,
    @SerialName("prev_tx_hash") val prevTxHash: String?,
    @SerialName("timestamp") val timestamp: Long,
    @SerialName("signature") val signature: String
) {
    /**
     * Returns the canonical UTF-8 bytes to be signed (excluding `signature`).
     */
    fun toCanonicalSigningBytes(): ByteArray {
        return CanonicalSerializer.canonicalize(this)
    }
}

class TransactionBuilder {

    private var txId: String = UUID.randomUUID().toString()
    private var tokenId: String? = null
    private var amountPaise: Long = 0
    private var payer: Party? = null
    private var payee: Party? = null
    private var deviceCounter: Long = 0
    private var prevTxHash: String? = null
    private var timestamp: Long = System.currentTimeMillis() / 1000

    fun setTxId(id: String) = apply { this.txId = id }
    fun setTokenId(id: String) = apply { this.tokenId = id }
    fun setAmountPaise(amount: Long) = apply { this.amountPaise = amount }
    fun setAmount(amountStr: String) = apply { this.amountPaise = amountStr.toLong() }
    fun setPayer(payer: Party) = apply { this.payer = payer }
    fun setPayee(payee: Party) = apply { this.payee = payee }
    fun setDeviceCounter(counter: Long) = apply { this.deviceCounter = counter }
    fun setPrevTxHash(hash: String?) = apply { this.prevTxHash = hash }
    fun setTimestamp(epochSec: Long) = apply { this.timestamp = epochSec }

    /**
     * Builds canonical bytes to be signed by the payer device.
     */
    fun buildUnsignedCanonicalBytes(): ByteArray {
        requireNotNull(tokenId) { "tokenId is required" }
        requireNotNull(payer) { "payer is required" }
        requireNotNull(payee) { "payee is required" }

        val tx = SparkTransaction(
            txId = txId,
            tokenId = tokenId!!,
            amount = amountPaise.toString(),
            payer = payer!!,
            payee = payee!!,
            deviceCounter = deviceCounter,
            prevTxHash = prevTxHash,
            timestamp = timestamp,
            signature = ""
        )

        return CanonicalSerializer.canonicalize(tx)
    }

    /**
     * Completes and returns the signed transaction object.
     */
    fun buildSigned(signatureBase64Url: String): SparkTransaction {
        requireNotNull(tokenId) { "tokenId is required" }
        requireNotNull(payer) { "payer is required" }
        requireNotNull(payee) { "payee is required" }

        return SparkTransaction(
            txId = txId,
            tokenId = tokenId!!,
            amount = amountPaise.toString(),
            payer = payer!!,
            payee = payee!!,
            deviceCounter = deviceCounter,
            prevTxHash = prevTxHash,
            timestamp = timestamp,
            signature = signatureBase64Url
        )
    }

    companion object {
        /**
         * Canonicalizes a transaction into pre-signature UTF-8 bytes.
         */
        fun canonicalize(tx: SparkTransaction): ByteArray {
            return CanonicalSerializer.canonicalize(tx)
        }

        /**
         * Signs pre-signature bytes using KeyStoreManager with the hardware-backed key for an alias.
         * Returns base64url-encoded signature without padding.
         */
        fun sign(bytes: ByteArray, keyStoreManager: KeyStoreManager, alias: String): String {
            val rawSignature = keyStoreManager.sign(alias, bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature)
        }

        /**
         * Assembles and signs a complete SparkTransaction.
         */
        fun buildSignedTransaction(
            txId: String = UUID.randomUUID().toString(),
            tokenId: String,
            amountPaise: Long,
            payer: Party,
            payee: Party,
            deviceCounter: Long,
            prevTxHash: String?,
            timestamp: Long = System.currentTimeMillis() / 1000,
            signer: (ByteArray) -> String
        ): SparkTransaction {
            val unsignedTx = SparkTransaction(
                txId = txId,
                tokenId = tokenId,
                amount = amountPaise.toString(),
                payer = payer,
                payee = payee,
                deviceCounter = deviceCounter,
                prevTxHash = prevTxHash,
                timestamp = timestamp,
                signature = ""
            )

            val canonicalBytes = CanonicalSerializer.canonicalize(unsignedTx)
            val signature = signer(canonicalBytes)

            return unsignedTx.copy(signature = signature)
        }

        fun serialize(tx: SparkTransaction): String {
            return kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }.encodeToString(SparkTransaction.serializer(), tx)
        }

        fun deserialize(jsonString: String): SparkTransaction {
            return kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            }.decodeFromString(SparkTransaction.serializer(), jsonString)
        }
    }
}
