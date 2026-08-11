package com.spark.wallet.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Wrapper for loading and running TensorFlow Lite models on-device (e.g. risk score / anomaly detection).
 */
class TFLiteEngine(private val context: Context) {

    private var interpreter: Interpreter? = null

    /**
     * Initializes the TFLite interpreter from an assets model file.
     */
    fun loadModel(modelFileName: String) {
        val buffer = loadModelFile(modelFileName)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(buffer, options)
    }

    /**
     * Runs inference on input features and returns the risk score / prediction.
     */
    fun runInference(inputFeatures: FloatArray): FloatArray {
        val currentInterpreter = interpreter
            ?: throw IllegalStateException("TFLite interpreter has not been initialized.")

        val output = Array(1) { FloatArray(inputFeatures.size) }
        currentInterpreter.run(arrayOf(inputFeatures), output)
        return output[0]
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun loadModelFile(modelFileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
