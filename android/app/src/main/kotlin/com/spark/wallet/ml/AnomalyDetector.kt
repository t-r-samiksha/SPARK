package com.spark.wallet.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Wrapper for Member C's exported TFLite anomaly detection model.
 *
 * NOTE: This local ML inference provides advisory anomaly hints and limit
 * suggestions to the user interface only.
 *
 * The authoritative cap and limits are strictly enforced by the signed
 * purse token provisioned by the backend, NOT this local inference engine.
 */
class AnomalyDetector(context: Context) {

    private var interpreter: Interpreter? = null

    init {
        try {
            val modelBuffer = loadModelFile(context, "anomaly_model.tflite")
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            // Model not present or failed to load
        }
    }

    /**
     * Runs inference on the given transaction features to detect anomalies.
     * Returns a risk score between 0.0 (safe) and 1.0 (high risk).
     */
    fun detectAnomaly(features: FloatArray): Float {
        val tflite = interpreter ?: return 0.0f
        
        val input = arrayOf(features)
        val output = Array(1) { FloatArray(1) }
        
        tflite.run(input, output)
        return output[0][0]
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }
}
