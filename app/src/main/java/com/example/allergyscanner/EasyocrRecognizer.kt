package com.example.allergyscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class EasyocrRecognizer(private val context: Context) {

    private var recognizerInterpreter: Interpreter? = null
    private val ModelUtilityFunctions by lazy { ModelUtilityFunctions() }

    // Recognizer dimensions (from specifications)
    private val recognizerBatch = 1
    private val recognizerChannels = 1
    private val recognizerHeight = 64
    private val recognizerWidth = 1000

    // Recognize post-processing parameters
    private val recognizerConfig = RecognizerConfig(
        beamWidth = 5,
        contrastThreshold = 0.1f,
        adjustContrast = 0.5f,
        filterThreshold = 0.003f
    )

    var isInitialized = false
        private set

    @Throws(IOException::class)
    fun initializeInterpreter()
    {
        // Load both models
        val assetManager = context.assets


        // Initialize recognizer
        val recognizerModel = ModelUtilityFunctions.loadModelFile(assetManager, "easyocr_recognizer.tflite")
        val recognizerOptions = Interpreter.Options().apply {
            setNumThreads(4)
        }
        recognizerInterpreter = Interpreter(recognizerModel, recognizerOptions)

        // Verify input/output shapes
        val recognizerInputShape = recognizerInterpreter!!.getInputTensor(0).shape()
        Log.d(TAG, "Recognizer input shape: [${recognizerInputShape[0]}, ${recognizerInputShape[1]}, ${recognizerInputShape[2]}, ${recognizerInputShape[3]}]")

        val recognizerOutputShape = recognizerInterpreter!!.getOutputTensor(0).shape()
        Log.d(TAG, "Recognizer output shape: ${recognizerOutputShape.contentToString()}")

        isInitialized = true
        Log.d(TAG, "Initialized recognizer")
    }

    fun runModel(bitmap : Bitmap, index : Int) : String
    {
        val recognizerInput = prepareRecognizerInput(bitmap, index)
        val recognizerOutput = runRecognizer(recognizerInput)
        val recognizedText = processRecognizerOutput(recognizerOutput)
        return recognizedText
    }

    fun runModelFallback(bitmap : Bitmap) : String
    {
        val recognizerInput = prepareRecognizerInput(bitmap, 0)
        val recognizerOutput = runRecognizer(recognizerInput)
        return processRecognizerOutput(recognizerOutput)
    }

    /**
     * Prepares input for the recognizer model.
     */
    private fun prepareRecognizerInput(bitmap: Bitmap, index: Int): ByteBuffer
    {
        // Get the original width and height of the bitmap
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // Calculate the aspect ratio
        val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()

        // Determine if the image is wider or taller compared to the target dimensions
        val targetWidth = recognizerWidth
        val targetHeight = recognizerHeight

        var newWidth = targetWidth
        var newHeight = targetHeight

        // Resize while maintaining the aspect ratio
        if (aspectRatio > 1)
        {
            // If the image is wide, fit by width and scale the height accordingly
            newWidth = targetWidth
            newHeight = (newWidth / aspectRatio).toInt()
        }
        else
        {
            // If the image is tall, fit by height and scale the width accordingly
            newHeight = targetHeight
            newWidth = (newHeight * aspectRatio).toInt()
        }

        // Resize the image while maintaining the aspect ratio
        val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        // Save the resized image for debugging
        ModelUtilityFunctions.saveBitmapToCache(resized, context, "prepareRecognizerResizedRegion$index.png")

        // Create a blank (white) canvas with the target dimensions
        val paddedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paddedBitmap)
        val paint = Paint()

        // Fill the canvas with a white background (or you can choose black or transparent)
        canvas.drawColor(Color.WHITE)

        // Calculate the position to center the resized image on the canvas
        val left = 0
        val top = 0

        // Draw the resized image onto the canvas
        canvas.drawBitmap(resized, left.toFloat(), top.toFloat(), paint)

        // Save the padded image (for debugging)
        ModelUtilityFunctions.saveBitmapToCache(paddedBitmap, context, "prepareRecognizerPaddedRegion$index.png")

        // Create buffer with correct size (NCHW format)
        val bufferSize = recognizerBatch * recognizerChannels * recognizerHeight * recognizerWidth * FLOAT_TYPE_SIZE
        val buffer = ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }

        // Get pixel data
        val pixels = IntArray(recognizerWidth * recognizerHeight)
        paddedBitmap.getPixels(pixels, 0, recognizerWidth, 0, 0, recognizerWidth, recognizerHeight)

        // Convert to grayscale and normalize (NCHW format)
        for (y in 0 until recognizerHeight)
        {
            for (x in 0 until recognizerWidth)
            {
                val pixel = pixels[y * recognizerWidth + x]

                // Convert to grayscale using standard weights
                val gray = (
                        (((pixel shr 16) and 0xFF) * 0.299f) +
                                (((pixel shr 8) and 0xFF) * 0.587f) +
                                ((pixel and 0xFF) * 0.114f)
                        ) / 255.0f

                // Normalize to [-1, 1] range as commonly used in OCR models
                buffer.putFloat((gray - 0.5f) / 0.5f)
            }
        }

        buffer.rewind()
        return buffer
    }

    /**
     * Runs the recognizer model on the prepared input.
     */
    private fun runRecognizer(input: ByteBuffer): ByteBuffer
    {
        // Get output tensor shape and size
        val outputShape = recognizerInterpreter!!.getOutputTensor(0).shape()
        val outputSize = outputShape.fold(1, Int::times) * FLOAT_TYPE_SIZE

        // Create output buffer
        val outputBuffer = ByteBuffer.allocateDirect(outputSize).apply {
            order(ByteOrder.nativeOrder())
        }

        // Run recognition
        recognizerInterpreter!!.run(input, outputBuffer)
        outputBuffer.rewind()

        // Debug: Check if output has non-zero values
        val floatArray = FloatArray(outputSize / FLOAT_TYPE_SIZE)
        outputBuffer.asFloatBuffer().get(floatArray)

        var nonZeroCount = 0
        var maxValue = 0f

        for (value in floatArray)
        {
            if (value > 0.01f) nonZeroCount++
            maxValue = max(maxValue, value)
        }

        Log.d(TAG, "Recognition output stats: $nonZeroCount/${floatArray.size} non-zero values, max: $maxValue")

        outputBuffer.rewind()
        return outputBuffer
    }

    /**
     * Processes recognizer output to extract text using CTC decoding.
     */
    private fun processRecognizerOutput(outputBuffer: ByteBuffer): String {
        // Get output shape from recognizer (assumed [batch, sequence_length, num_classes])
        val outputShape = recognizerInterpreter!!.getOutputTensor(0).shape()
        val sequenceLength = outputShape[1]
        val numClasses = outputShape[2]

        // Extract probabilities from buffer
        val probs = Array(sequenceLength) { FloatArray(numClasses) }
        for (i in 0 until sequenceLength)
        {
            for (j in 0 until numClasses)
            {
                probs[i][j] = outputBuffer.getFloat()
            }
        }

        // Extended character set for EasyOCR (does NOT include a blank token)
        // Assume that index 0 is reserved as the blank.
        val charset = "0123456789!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ €ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

        // CTC decoding with a threshold; treat index 0 as blank.
        val result = StringBuilder()
        var previousIndex = 0 // Start with blank

        for (timestep in probs)
        {
            // Apply softmax to convert raw logits to probabilities
            val probsAtTimestep = softmax(timestep)

            // Find the index with the maximum probability for this timestep
            val maxIndex = probsAtTimestep.indices.maxByOrNull { probsAtTimestep[it] } ?: continue
            val maxProb = probsAtTimestep[maxIndex]
            //Log.d(TAG, "Max probability at this timestep: $maxProb")

            // If the max index is 0, it's blank; skip it.
            if (maxIndex == 0)
            {
                previousIndex = maxIndex
                continue
            }

            // Only consider predictions above the threshold
            if (maxProb > 0.1)
            {
                // Only append if this timestep's prediction is different from the previous one
                if (maxIndex != previousIndex)
                {
                    // Adjust index: since index 0 is blank, the actual character mapping is maxIndex - 1.
                    val charIndex = maxIndex - 1

                    if (charIndex in charset.indices)
                    {
                        result.append(charset[charIndex])
                    }
                }
            }
            previousIndex = maxIndex
        }
        return result.toString()
    }

    // Most max probabillity values are between 8 and 25. This function converts these values to actual probability values between 0 and 1.
    private fun softmax(logits: FloatArray, temperature: Float = 1.0f): FloatArray
    {
        // Apply temperature scaling: divide logits by the temperature
        val scaledLogits = logits.map { it / temperature }.toFloatArray()

        // Calculate the softmax of the scaled logits
        val expVals = scaledLogits.map { Math.exp(it.toDouble()).toFloat() }.toFloatArray()
        val sum = expVals.sum()
        return expVals.map { it / sum }.toFloatArray()
    }


    /**
     * Cleans up OCR recognition results by handling common recognition errors.
     */
    fun cleanupRecognitionResult(text: String): String
    {
        // Remove consecutive duplicates (common OCR issue)
        val withoutDuplicates = text.replace(Regex("(.)\\1{3,}"), "$1$1")

        // Replace commonly confused characters
        val corrected = withoutDuplicates
            .replace(Regex("(?<![0-9])0(?![0-9])"), "o")  // Replace isolated 0s with o
            .replace(Regex("(?<![0-9])1(?![0-9])"), "l")  // Replace isolated 1s with l

        // Remove isolated digits
        val cleaned = corrected.replace(Regex("\\b\\d\\b"), "")

        // Additional processing for ingredients list context
        // Convert known patterns that might appear in ingredients lists
        val ingredientsFormatted = cleaned
            .replace(Regex("(?i)\\bingredi[e0o]nts\\b"), "Ingredients")
            .replace(Regex("(?i)\\bc[o0]ntains\\b"), "Contains")

        return ingredientsFormatted.trim()
    }

    /**
     * Configuration for recognizer parameters.
     */
    data class RecognizerConfig
    (
        val beamWidth: Int,
        val contrastThreshold: Float,
        val adjustContrast: Float,
        val filterThreshold: Float
    )

    companion object
    {
        private const val TAG = "ImageProcessor"
        private const val FLOAT_TYPE_SIZE = 4
    }

}