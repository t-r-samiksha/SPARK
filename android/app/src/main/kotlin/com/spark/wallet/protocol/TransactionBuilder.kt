package com.spark.wallet.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
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
)

class TransactionBuilder {

    private var txId: String = UUID.randomUUID().toString()
    private var tokenId: String? = null
    private var amountPaise: Long = 0
    private var payer: Party? = null
    private var payee: Party? = null
    private var deviceCounter: Long = 0
    private var prevTxHash: String? = null
    private var timestamp: Long = System.currentTimeMillis() / 1000

    fun setTokenId(id: String) = apply { this.tokenId = id }
    fun setAmountPaise(amount: Long) = apply { this.amountPaise = amount }
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

        val jsonObject = Json.encodeToJsonElement(
            SparkTransaction(
                txId = txId,
                tokenId = tokenId!!,
                amount = amountPaise.toString(),
                payer = payer!!,
                payee = payee!!,
                deviceCounter = deviceCounter,
                prevTxHash = prevTxHash,
                timestamp = timestamp,
                signature = "" // placeholder, excluded during canonicalization
            )
        ).jsonObject

        return CanonicalSerializer.canonicalBytes(jsonObject, excludeSignature = true)
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
}
