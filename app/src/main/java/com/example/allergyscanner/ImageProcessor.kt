package com.example.allergyscanner

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class ImageProcessor(private val context: Context) {
    private var detectorInterpreter: Interpreter? = null
    private var recognizerInterpreter: Interpreter? = null

    // Detector dimensions (from specifications)
    private val detectorBatch = 1
    private val detectorChannels = 3
    private val detectorHeight = 608
    private val detectorWidth = 800

    // Recognizer dimensions (from specifications)
    private val recognizerBatch = 1
    private val recognizerChannels = 1
    private val recognizerHeight = 64
    private val recognizerWidth = 1000

    // Detector post-processing parameters (matching DETECTOR_ARGS in Python)
    private val detectorConfig = DetectorConfig(
        textThreshold = 0.7f,
        linkThreshold = 0.4f,
        lowText = 0.4f,
        poly = false,
        minSize = 20,
        slopeThreshold = 0.1f,
        yCenterThreshold = 0.5f,
        heightThreshold = 0.5f,
        widthThreshold = 0.5f,
        addMargin = 0.1f
    )

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
    fun initializeInterpreter() {
        // Load both models
        val assetManager = context.assets

        // Initialize detector
        val detectorModel = loadModelFile(assetManager, "easyocr_detector.tflite")
        val detectorOptions = Interpreter.Options().apply {
            setNumThreads(4)
        }
        detectorInterpreter = Interpreter(detectorModel, detectorOptions)

        // Initialize recognizer
        val recognizerModel = loadModelFile(assetManager, "easyocr_recognizer.tflite")
        val recognizerOptions = Interpreter.Options().apply {
            setNumThreads(4)
        }
        recognizerInterpreter = Interpreter(recognizerModel, recognizerOptions)

        // Verify input/output shapes
        val detectorInputShape = detectorInterpreter!!.getInputTensor(0).shape()
        val recognizerInputShape = recognizerInterpreter!!.getInputTensor(0).shape()

        Log.d(TAG, "Detector input shape: [${detectorInputShape[0]}, ${detectorInputShape[1]}, ${detectorInputShape[2]}, ${detectorInputShape[3]}]")
        Log.d(TAG, "Recognizer input shape: [${recognizerInputShape[0]}, ${recognizerInputShape[1]}, ${recognizerInputShape[2]}, ${recognizerInputShape[3]}]")

        val detectorOutputShape = detectorInterpreter!!.getOutputTensor(0).shape()
        val recognizerOutputShape = recognizerInterpreter!!.getOutputTensor(0).shape()

        Log.d(TAG, "Detector output shape: ${detectorOutputShape.contentToString()}")
        Log.d(TAG, "Recognizer output shape: ${recognizerOutputShape.contentToString()}")

        isInitialized = true
        Log.d(TAG, "Initialized detectors and recognizers")
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        try {
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } finally {
            inputStream.close()
            fileDescriptor.close()
        }
    }

    fun processImage(bitmap: Bitmap): String {
        check(isInitialized) { "Interpreters not initialized" }

        try {
            // Step 1: Pre-process the image for better text visibility
            val enhancedBitmap = enhanceTextVisibility(bitmap)
            Log.d(TAG, "Enhanced image for better visibility")

            // Step 2: Convert to grayscale for OCR processing
            val grayscaleBitmap = convertToGrayscale(enhancedBitmap)
            Log.d(TAG, "Converted to grayscale")

            // Step 3: Detect text regions using the detector model
            val textRegions = detectTextRegions(enhancedBitmap)
            Log.d(TAG, "Detected ${textRegions.size} text regions")

            if (textRegions.isEmpty()) {
                Log.d(TAG, "No text regions detected")
                // Fallback to full image processing
                val recognizerInput = prepareRecognizerInput(grayscaleBitmap)
                val recognizerOutput = runRecognizer(recognizerInput)
                val fallbackText = processRecognizerOutput(recognizerOutput)
                Log.d(TAG, "Fallback recognition result: $fallbackText")
                return "Full image: $fallbackText"
            }

            // Step 4: Recognize text in each detected region
            val results = mutableListOf<String>()

            for ((index, region) in textRegions.withIndex()) {
                val croppedRegion = cropTextRegion(grayscaleBitmap, region)
                val recognizerInput = prepareRecognizerInput(croppedRegion)
                val recognizerOutput = runRecognizer(recognizerInput)
                val recognizedText = processRecognizerOutput(recognizerOutput)

                val cleanedText = cleanupRecognitionResult(recognizedText)
                if (cleanedText.isNotEmpty()) {
                    results.add(cleanedText)
                    Log.d(TAG, "Region $index: $cleanedText (raw: $recognizedText)")
                }
            }

            val resultText = results.joinToString("\n")
            Log.d(TAG, "Final results: $resultText")
            return resultText

        } catch (e: Exception) {
            Log.e(TAG, "Error in image processing pipeline", e)
            return "Error: ${e.message ?: "Unknown error"}"
        }
    }

    /**
     * Enhances text visibility in the image through contrast adjustment.
     */
    private fun enhanceTextVisibility(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(result)

        // Apply contrast enhancement using ColorMatrix
        val colorMatrix = ColorMatrix().apply {
            // Increase contrast
            setSaturation(1.3f)

            // Adjust brightness and contrast
            val scale = 1.2f
            val translate = -15f
            postConcat(ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        val enhancedPaint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        canvas.drawBitmap(bitmap, 0f, 0f, enhancedPaint)
        return result
    }

    /**
     * Converts a bitmap to grayscale.
     */
    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
            })
        }

        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /**
     * Detects text regions in the input image using the detector model.
     */
    private fun detectTextRegions(bitmap: Bitmap): List<RectF> {
        // Prepare input for detector
        val detectorInput = prepareDetectorInput(bitmap)

        // Run detector model
        val detectorOutput = runDetector(detectorInput)

        // Post-process detector output to get text regions
        return postProcessDetectorOutput(detectorOutput, bitmap.width, bitmap.height)
    }

    /**
     * Crops a region from the input bitmap based on the specified bounding box.
     */
    private fun cropTextRegion(bitmap: Bitmap, region: RectF): Bitmap {
        // Ensure coordinates are within bitmap bounds
        val x = max(0, region.left.toInt())
        val y = max(0, region.top.toInt())
        val width = min(bitmap.width - x, region.width().toInt())
        val height = min(bitmap.height - y, region.height().toInt())

        // Skip invalid regions
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "Invalid crop region: $region")
            return bitmap
        }

        try {
            // Create a cropped bitmap from the region
            return Bitmap.createBitmap(bitmap, x, y, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping region: $region", e)
            return bitmap
        }
    }

    /**
     * Prepares input for the detector model.
     */
    private fun prepareDetectorInput(bitmap: Bitmap): ByteBuffer {
        // Resize bitmap to detector dimensions
        val resized = Bitmap.createScaledBitmap(bitmap, detectorWidth, detectorHeight, true)

        // Create buffer with correct size (NCHW format)
        val bufferSize = detectorBatch * detectorChannels * detectorHeight * detectorWidth * FLOAT_TYPE_SIZE
        val buffer = ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }

        // Get pixel data
        val pixels = IntArray(detectorWidth * detectorHeight)
        resized.getPixels(pixels, 0, detectorWidth, 0, 0, detectorWidth, detectorHeight)

        // Convert to RGB and normalize (NCHW format - organize by channel)
        // Normalization values from EasyOCR
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        // Red channel
        for (y in 0 until detectorHeight) {
            for (x in 0 until detectorWidth) {
                val pixel = pixels[y * detectorWidth + x]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                buffer.putFloat((r - mean[0]) / std[0])
            }
        }

        // Green channel
        for (y in 0 until detectorHeight) {
            for (x in 0 until detectorWidth) {
                val pixel = pixels[y * detectorWidth + x]
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                buffer.putFloat((g - mean[1]) / std[1])
            }
        }

        // Blue channel
        for (y in 0 until detectorHeight) {
            for (x in 0 until detectorWidth) {
                val pixel = pixels[y * detectorWidth + x]
                val b = (pixel and 0xFF) / 255.0f
                buffer.putFloat((b - mean[2]) / std[2])
            }
        }

        buffer.rewind()
        return buffer
    }

    /**
     * Runs the detector model on the prepared input.
     */
    private fun runDetector(input: ByteBuffer): ByteBuffer {
        // Get output tensor shape
        val outputShape = detectorInterpreter!!.getOutputTensor(0).shape()

        // Create output buffer
        val outputSize = outputShape.fold(1, Int::times) * FLOAT_TYPE_SIZE
        val outputBuffer = ByteBuffer.allocateDirect(outputSize).apply {
            order(ByteOrder.nativeOrder())
        }

        // Run detection
        detectorInterpreter!!.run(input, outputBuffer)
        outputBuffer.rewind()

        return outputBuffer
    }

    /**
     * Post-processes detector output to extract text region bounding boxes.
     * This implements similar logic to the Python EasyOCR's detector_postprocess method.
     */
    private fun postProcessDetectorOutput(output: ByteBuffer, originalWidth: Int, originalHeight: Int): List<RectF> {
        // Get tensor dimensions
        val outputShape = detectorInterpreter!!.getOutputTensor(0).shape()
        val height = outputShape[1]
        val width = outputShape[2]

        // Extract text score map and link score map
        val scoreText = FloatArray(height * width)
        val scoreLink = FloatArray(height * width)

        // Read text score (channel 0) and link score (channel 1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                scoreText[index] = output.getFloat()
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                scoreLink[index] = output.getFloat()
            }
        }

        // Simple threshold-based detection for proof of concept
        // Find connected components above text threshold
        val regions = mutableListOf<RectF>()
        val visited = Array(height) { BooleanArray(width) }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (scoreText[index] > detectorConfig.textThreshold && !visited[y][x]) {
                    // Start a new region
                    val region = findConnectedRegion(x, y, scoreText, scoreLink, width, height, visited)

                    // Scale region to original image dimensions
                    val scaleX = originalWidth / width.toFloat()
                    val scaleY = originalHeight / height.toFloat()

                    region.left *= scaleX
                    region.top *= scaleY
                    region.right *= scaleX
                    region.bottom *= scaleY

                    // Apply minimum size filtering
                    if (region.width() > detectorConfig.minSize && region.height() > detectorConfig.minSize) {
                        // Add margin
                        val marginX = region.width() * detectorConfig.addMargin
                        val marginY = region.height() * detectorConfig.addMargin

                        region.left = max(0f, region.left - marginX)
                        region.top = max(0f, region.top - marginY)
                        region.right = min(originalWidth.toFloat(), region.right + marginX)
                        region.bottom = min(originalHeight.toFloat(), region.bottom + marginY)

                        regions.add(region)
                    }
                }
            }
        }

        // Group nearby regions (simplified version of group_text_box function)
        return mergeOverlappingRegions(regions)
    }

    /**
     * Finds a connected region starting from the given point.
     */
    private fun findConnectedRegion(
        startX: Int,
        startY: Int,
        scoreText: FloatArray,
        scoreLink: FloatArray,
        width: Int,
        height: Int,
        visited: Array<BooleanArray>
    ): RectF {
        val queue = mutableListOf(Pair(startX, startY))
        visited[startY][startX] = true

        var minX = startX
        var minY = startY
        var maxX = startX
        var maxY = startY

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeAt(0)

            // Update region bounds
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)

            // Check 4 neighbors
            val neighbors = listOf(
                Pair(x+1, y), Pair(x-1, y), Pair(x, y+1), Pair(x, y-1)
            )

            for ((nx, ny) in neighbors) {
                if (nx in 0 until width && ny in 0 until height && !visited[ny][nx]) {
                    val index = ny * width + nx

                    // Connect if text score or link score is high enough
                    if (scoreText[index] > detectorConfig.lowText ||
                        scoreLink[index] > detectorConfig.linkThreshold) {
                        queue.add(Pair(nx, ny))
                        visited[ny][nx] = true
                    }
                }
            }
        }

        return RectF(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat())
    }

    /**
     * Merges overlapping regions to create fewer, larger text boxes.
     */
    private fun mergeOverlappingRegions(regions: List<RectF>): List<RectF> {
        if (regions.isEmpty()) return regions

        val result = mutableListOf<RectF>()
        val merged = BooleanArray(regions.size) { false }

        for (i in regions.indices) {
            if (merged[i]) continue

            val current = RectF(regions[i])
            merged[i] = true

            var mergedAny = true
            while (mergedAny) {
                mergedAny = false

                for (j in regions.indices) {
                    if (merged[j]) continue

                    if (RectF.intersects(current, regions[j])) {
                        // Merge overlapping regions
                        current.left = min(current.left, regions[j].left)
                        current.top = min(current.top, regions[j].top)
                        current.right = max(current.right, regions[j].right)
                        current.bottom = max(current.bottom, regions[j].bottom)

                        merged[j] = true
                        mergedAny = true
                    }
                }
            }

            result.add(current)
        }

        return result
    }

    /**
     * Prepares input for the recognizer model.
     */
    private fun prepareRecognizerInput(bitmap: Bitmap): ByteBuffer {
        // Resize bitmap to recognizer dimensions
        val resized = Bitmap.createScaledBitmap(bitmap, recognizerWidth, recognizerHeight, true)

        // Create buffer with correct size (NCHW format)
        val bufferSize = recognizerBatch * recognizerChannels * recognizerHeight * recognizerWidth * FLOAT_TYPE_SIZE
        val buffer = ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }

        // Get pixel data
        val pixels = IntArray(recognizerWidth * recognizerHeight)
        resized.getPixels(pixels, 0, recognizerWidth, 0, 0, recognizerWidth, recognizerHeight)

        // Convert to grayscale and normalize (NCHW format)
        for (y in 0 until recognizerHeight) {
            for (x in 0 until recognizerWidth) {
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
    private fun runRecognizer(input: ByteBuffer): ByteBuffer {
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
        for (value in floatArray) {
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
        for (i in 0 until sequenceLength) {
            for (j in 0 until numClasses) {
                probs[i][j] = outputBuffer.getFloat()
            }
        }

        // Extended character set for EasyOCR (does NOT include a blank token)
        // Assume that index 0 is reserved as the blank.
        val charset = "0123456789!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ €ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

        // CTC decoding with a threshold; treat index 0 as blank.
        val result = StringBuilder()
        var previousIndex = 0 // Start with blank

        for (timestep in probs) {
            // Apply softmax to convert raw logits to probabilities
            val probsAtTimestep = softmax(timestep)

            // Find the index with the maximum probability for this timestep
            val maxIndex = probsAtTimestep.indices.maxByOrNull { probsAtTimestep[it] } ?: continue
            val maxProb = probsAtTimestep[maxIndex]
            //Log.d(TAG, "Max probability at this timestep: $maxProb")

            // If the max index is 0, it's blank; skip it.
            if (maxIndex == 0) {
                previousIndex = maxIndex
                continue
            }

            // Only consider predictions above the threshold
            if (maxProb > 0.1) {
                // Only append if this timestep's prediction is different from the previous one
                if (maxIndex != previousIndex) {
                    // Adjust index: since index 0 is blank, the actual character mapping is maxIndex - 1.
                    val charIndex = maxIndex - 1
                    if (charIndex in charset.indices) {
                        result.append(charset[charIndex])
                    }
                }
            }
            previousIndex = maxIndex
        }

        return result.toString()
    }

    // Most max probabillity values are between 8 and 25. This function converts these values to actual probability values between 0 and 1.
    private fun softmax(logits: FloatArray, temperature: Float = 1.0f): FloatArray {
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
    private fun cleanupRecognitionResult(text: String): String {
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
     * Configuration for detector parameters.
     */
    data class DetectorConfig(
        val textThreshold: Float,
        val linkThreshold: Float,
        val lowText: Float,
        val poly: Boolean,
        val minSize: Int,
        val slopeThreshold: Float,
        val yCenterThreshold: Float,
        val heightThreshold: Float,
        val widthThreshold: Float,
        val addMargin: Float
    )

    /**
     * Configuration for recognizer parameters.
     */
    data class RecognizerConfig(
        val beamWidth: Int,
        val contrastThreshold: Float,
        val adjustContrast: Float,
        val filterThreshold: Float
    )

    companion object {
        private const val TAG = "ImageProcessor"
        private const val FLOAT_TYPE_SIZE = 4
    }
}