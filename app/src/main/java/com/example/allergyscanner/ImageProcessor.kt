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
        modelInputSize = FLOAT_TYPE_SIZE * 3 * 608 * 800 // inputs obtained from analyzing tflite file on netron.app

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
        Log.d(TAG, "Starting processImage")
        check(isInitialized) { "TF Lite Interpreter is not initialized yet." }

        Log.d(TAG, "Begin resizing image")
        // Pre-processing: resize the input image to match the model input shape.
        val resizedImage = Bitmap.createScaledBitmap(
            bitmap,
            800,
            608,
            true
        )
        Log.d(TAG, "Image resize complete")

        Log.d(TAG, "byteBuffer var definition begin")
        val byteBuffer = convertBitmapToByteBuffer(resizedImage)
        Log.d(TAG, "byteBuffer var definition end")

        // Define the output size based on the model's output shape [1, 304, 400, 2]
        val outputSize = 304 * 400 * 2
        val output = FloatArray(outputSize)
        Log.d(TAG, "output var definition end")

        Log.d(TAG, "Begin interpreter run")
        // Run inference with the input data.
        interpreter?.run(byteBuffer, output)
        Log.d(TAG, "Interpreter run complete")

        // Post-processing: find the class with the highest probability for each pixel
        val prediction = StringBuilder()

        try {
            for (i in 0 until 304) {
                for (j in 0 until 400) {
                    // Calculate flat index for (i, j, class)
                    for (classIndex in 0 until 2) {
                        val idx = (i * 400 + j) * 2 + classIndex
                        val confidence = output[idx]

                        prediction.append("($i, $j, Class $classIndex): Confidence: $confidence\n")
                    }
                }
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
            Log.e(TAG, "ArrayIndexOutOfBoundsException: ${e.message}")
            return "Error: Index out of bounds in output tensor"
        }

        return prediction.toString()
    }


    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        Log.d(TAG, "Starting convertBitmapToByteBuffer")

        // Define the correct input dimensions for the model
        val expectedWidth = 800
        val expectedHeight = 608

        // Log the bitmap's actual dimensions
        Log.d(TAG, "Bitmap width: ${bitmap.width}, height: ${bitmap.height}")

        val byteBuffer = ByteBuffer.allocateDirect(expectedWidth * expectedHeight * 3 * FLOAT_TYPE_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())

        // Resize the bitmap to match the model's input size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, expectedWidth, expectedHeight, true)

        val pixels = IntArray(expectedWidth * expectedHeight)
        resizedBitmap.getPixels(pixels, 0, expectedWidth, 0, 0, expectedWidth, expectedHeight)

        Log.d(TAG, "Bitmap pixels fetched. Total pixels: ${pixels.size}")

        var pixelCount = 0
        for (pixelValue in pixels) {
            val r = (pixelValue shr 16 and 0xFF)
            val g = (pixelValue shr 8 and 0xFF)
            val b = (pixelValue and 0xFF)

            // Convert RGB to grayscale and normalize pixel value to [0..1].
            val normalizedPixelValue = (r + g + b) / 3.0f / 255.0f

            // Log each pixel value conversion
            if (pixelCount % 100 == 0) {
                Log.d(TAG, "Pixel $pixelCount - r: $r, g: $g, b: $b, normalized: $normalizedPixelValue")
            }

            byteBuffer.putFloat(normalizedPixelValue)
            pixelCount++
        }

        Log.d(TAG, "convertBitmapToByteBuffer complete. ByteBuffer size: ${byteBuffer.position()} bytes")
        return byteBuffer
    }



    companion object {
        private const val TAG = "ImageProcessor"

        private const val FLOAT_TYPE_SIZE = 4
        private const val PIXEL_SIZE = 1

        private const val OUTPUT_CLASSES_COUNT = 10
    }
}