package com.example.allergyscanner

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class ImageProcessor(private val context: Context) {
    private var inputImageWidth: Int = 0
    private var inputImageHeight: Int = 0
    private var modelInputSize: Int = 0

    private var interpreter: Interpreter? = null
    var isInitialized = false
        private set

    // Load the TFlite model from file and initialize an interpreter
    @Throws(IOException::class)
    fun initializeInterpreter() {
        // Load model from assets folder
        val assetManager = context.assets
        val model = loadModelFile(assetManager, "easyOCR.tflite")
        val interpreter = Interpreter(model)

        // Read input shape from model file.
        val inputShape = interpreter.getInputTensor(0).shape()
        inputImageWidth = inputShape[1]
        inputImageHeight = inputShape[2]
        modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * PIXEL_SIZE

        // Finish interpreter initialization
        this.interpreter = interpreter
        isInitialized = true
        Log.d(TAG, "Initialized TFLite interpreter.")

    }


    // Load TFlite model file into a bytebuffer
    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager, modelPath: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val start = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, start, declaredLength)
    }

    fun processImage(bitmap: Bitmap): String {
        check(isInitialized) { "TF Lite Interpreter is not initialized yet." }

        // Pre-processing: resize the input image to match the model input shape.
        val resizedImage = Bitmap.createScaledBitmap(
            bitmap,
            inputImageWidth,
            inputImageHeight,
            true
        )

        val byteBuffer = convertBitmapToByteBuffer(resizedImage)
        // Define an array to store the model output.
        val output = Array(1) { FloatArray(OUTPUT_CLASSES_COUNT) }

        // Run inference with the input data.
        interpreter?.run(byteBuffer, output)

        // Post-processing: find the digit that has the highest probability
        // and return it a human-readable string.
        val result = output[0]
        val maxIndex = result.indices.maxByOrNull { result[it] } ?: -1
        val resultString =
            "Prediction Result: %d\nConfidence: %2f"
                .format(maxIndex, result[maxIndex])

        return resultString
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(modelInputSize)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputImageWidth * inputImageHeight)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixelValue in pixels) {
            val r = (pixelValue shr 16 and 0xFF)
            val g = (pixelValue shr 8 and 0xFF)
            val b = (pixelValue and 0xFF)

            // Convert RGB to grayscale and normalize pixel value to [0..1].
            val normalizedPixelValue = (r + g + b) / 3.0f / 255.0f
            byteBuffer.putFloat(normalizedPixelValue)
        }

        return byteBuffer
    }

    companion object {
        private const val TAG = "ImageProcessor"

        private const val FLOAT_TYPE_SIZE = 4
        private const val PIXEL_SIZE = 1

        private const val OUTPUT_CLASSES_COUNT = 10
    }
}