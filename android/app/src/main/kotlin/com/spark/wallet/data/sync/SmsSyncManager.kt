package com.spark.wallet.data.sync

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.spark.wallet.protocol.SparkTransaction
import com.spark.wallet.protocol.TransactionBuilder
import java.util.Base64

/**
 * SMS Fallback Transport:
 * Encodes offline transactions into compact SMS messages and dispatches via SmsManager
 * when cellular voice/SMS signal is present without an active packet-data connection.
 *
 * PROTOCOL SPECIFICATION (consumed by Member B's SMS Gateway simulator):
 * Format:
 * Single part:
 *   "SPARK_TX:v1:1/1:<base64url_canonical_json>"
 * Multi part (if payload > 130 chars):
 *   "SPARK_TX:v1:1/N:<chunk_1_base64url>"
 *   "SPARK_TX:v1:2/N:<chunk_2_base64url>"
 *   ...
 *   "SPARK_TX:v1:N/N:<chunk_N_base64url>"
 */
object SmsSyncManager {

    private const val TAG = "SmsSyncManager"
    const val SMS_PREFIX = "SPARK_TX:v1:"
    const val DEFAULT_GATEWAY_PHONE = "+919876543210"
    private const val MAX_CHUNK_PAYLOAD_SIZE = 120

    /**
     * Encodes a [SparkTransaction] into one or more SMS payload strings.
     */
    fun encodeTransaction(tx: SparkTransaction): List<String> {
        val txJson = TransactionBuilder.serialize(tx)
        val base64UrlPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(txJson.toByteArray(Charsets.UTF_8))

        if (base64UrlPayload.length <= MAX_CHUNK_PAYLOAD_SIZE) {
            return listOf("${SMS_PREFIX}1/1:$base64UrlPayload")
        }

        val chunks = base64UrlPayload.chunked(MAX_CHUNK_PAYLOAD_SIZE)
        val totalChunks = chunks.size
        return chunks.mapIndexed { index, chunk ->
            "${SMS_PREFIX}${index + 1}/$totalChunks:$chunk"
        }
    }

    /**
     * Decodes one or more SMS payload strings into a [SparkTransaction].
     */
    fun decodeTransaction(messages: List<String>): SparkTransaction {
        require(messages.isNotEmpty()) { "Cannot decode empty SMS list" }

        val sortedPayloads = messages.map { msg ->
            require(msg.startsWith(SMS_PREFIX)) { "Invalid SMS header: $msg" }
            val withoutPrefix = msg.removePrefix(SMS_PREFIX)
            val parts = withoutPrefix.split(":", limit = 2)
            require(parts.size == 2) { "Invalid chunk format: $msg" }

            val indexParts = parts[0].split("/")
            require(indexParts.size == 2) { "Invalid chunk index format: ${parts[0]}" }
            val chunkIndex = indexParts[0].toInt()
            val totalChunks = indexParts[1].toInt()
            val chunkData = parts[1]

            Triple(chunkIndex, totalChunks, chunkData)
        }.sortedBy { it.first }

        val totalExpected = sortedPayloads.first().second
        require(sortedPayloads.size == totalExpected) {
            "Incomplete chunk set: received ${sortedPayloads.size} of $totalExpected chunks"
        }

        val fullBase64Url = sortedPayloads.joinToString("") { it.third }
        val decodedJsonBytes = Base64.getUrlDecoder().decode(fullBase64Url)
        val txJson = String(decodedJsonBytes, Charsets.UTF_8)
        return TransactionBuilder.deserialize(txJson)
    }

    /**
     * Sends an offline transaction via standard SMS to the bank's SMS gateway.
     */
    fun sendTransactionViaSms(
        context: Context,
        transaction: SparkTransaction,
        destinationAddress: String = DEFAULT_GATEWAY_PHONE
    ): Result<Int> = runCatching {
        val smsManager: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val encodedMessages = encodeTransaction(transaction)
        Log.i(TAG, "Dispatching ${encodedMessages.size} SMS part(s) to $destinationAddress for tx: ${transaction.txId}")

        encodedMessages.forEach { msg ->
            smsManager.sendTextMessage(destinationAddress, null, msg, null, null)
        }

        encodedMessages.size
    }
}
