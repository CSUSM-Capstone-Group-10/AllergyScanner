package com.example.allergyscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class EasyocrDetector(private val context: Context)
    {
    private var detectorInterpreter: Interpreter? = null
    private val ModelUtilityFunctions by lazy { ModelUtilityFunctions() }
    private val TAG = "ImageProcessor"

    // Detector dimensions (from specifications)
    private val detectorBatch = 1
    private val detectorChannels = 3
    private val detectorHeight = 608
    private val detectorWidth = 800

    // Detector post-processing parameters (matching DETECTOR_ARGS in Python)
    private val detectorConfig = DetectorConfig(
        textThreshold = 0.5f,
        linkThreshold = 0.2f,
        lowText = 0.3f,
        poly = false,
        minSize = 30,
        slopeThreshold = 0.1f,
        yCenterThreshold = 0.5f,
        heightThreshold = 0.7f,
        widthThreshold = 0.2f,
        addMargin = 0.05f
    )


        var isInitialized = false
        private set

    @Throws(IOException::class)
    fun initializeInterpreter()
    {
        // Load model
        val assetManager = context.assets

        // Initialize detector
        val detectorModel = ModelUtilityFunctions.loadModelFile(assetManager, "easyocr_detector.tflite")
        val detectorOptions = Interpreter.Options().apply {
            setNumThreads(4)
        }
        detectorInterpreter = Interpreter(detectorModel, detectorOptions)

        // Verify input/output shapes
        val detectorInputShape = detectorInterpreter!!.getInputTensor(0).shape()
        Log.d(TAG, "Detector input shape: [${detectorInputShape[0]}, ${detectorInputShape[1]}, ${detectorInputShape[2]}, ${detectorInputShape[3]}]")

        val detectorOutputShape = detectorInterpreter!!.getOutputTensor(0).shape()
        Log.d(TAG, "Detector output shape: ${detectorOutputShape.contentToString()}")

        isInitialized = true
        Log.d(TAG, "Initialized detector")
    }

    fun runModel(bitmap: Bitmap): List<RectF>
    {
        // Preprocess the image and prepare input for the model
        val preparedInput =  prepareDetectorInput(bitmap)

        // Run the detector
        val detectorOutput = runDetector(preparedInput.first)

        // Postprocess the output. This contains the text regions in the image.
        val postprocessedResult = postProcessDetectorOutput(detectorOutput, detectorWidth, detectorHeight)

        Log.d(TAG, "Detected ${postprocessedResult.size} text regions")
        ModelUtilityFunctions.saveBitmapToCache(ModelUtilityFunctions.drawRegionsOnImage(preparedInput.second, postprocessedResult), context, "image_with_regions.png")

        return postprocessedResult
    }

    /**
     * Prepares input for the detector model.
     */
    private fun prepareDetectorInput(bitmap: Bitmap): Pair<ByteBuffer, Bitmap>
    {
        // Resize image with aspect ratio padding
        val resized = resizeWithPadding(bitmap, detectorWidth, detectorHeight)

        // Debugging: Save padded image to check quality
        ModelUtilityFunctions.saveBitmapToCache(resized, context, "resized_image_with_padding.png")

        // Create ByteBuffer for detector input
        val bufferSize = detectorBatch * detectorChannels * detectorHeight * detectorWidth * FLOAT_TYPE_SIZE
        val buffer = ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }

        // Get pixel data
        val pixels = IntArray(detectorWidth * detectorHeight)
        resized.getPixels(pixels, 0, detectorWidth, 0, 0, detectorWidth, detectorHeight)

        // Normalization values
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        // Fill buffer in NCHW format
        for (c in 0 until 3) { // RGB channels
            for (y in 0 until detectorHeight) {
                for (x in 0 until detectorWidth) {
                    val pixel = pixels[y * detectorWidth + x]
                    val value = when (c) {
                        0 -> ((pixel shr 16) and 0xFF) / 255.0f // Red
                        1 -> ((pixel shr 8) and 0xFF) / 255.0f  // Green
                        2 -> (pixel and 0xFF) / 255.0f         // Blue
                        else -> 0.0f
                    }
                    buffer.putFloat((value - mean[c]) / std[c])
                }
            }
        }

        buffer.rewind()
        return Pair(buffer, resized)
    }

        private fun resizeWithPadding(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val aspectRatio = bitmap.width.toFloat() / bitmap.height
            val targetAspectRatio = targetWidth.toFloat() / targetHeight

            val scaleWidth: Int
            val scaleHeight: Int
            if (aspectRatio > targetAspectRatio) {
                scaleWidth = targetWidth
                scaleHeight = (targetWidth / aspectRatio).toInt()
            } else {
                scaleHeight = targetHeight
                scaleWidth = (targetHeight * aspectRatio).toInt()
            }

            val resized = Bitmap.createScaledBitmap(bitmap, scaleWidth, scaleHeight, true)
            val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)
            canvas.drawColor(Color.WHITE) // Padding color
            canvas.drawBitmap(resized, ((targetWidth - scaleWidth) / 2).toFloat(), ((targetHeight - scaleHeight) / 2).toFloat(), null)

            return outputBitmap
        }


        /**
     * Runs the detector model on the prepared input.
     */
    private fun runDetector(input: ByteBuffer): ByteBuffer
    {
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
    private fun postProcessDetectorOutput(output: ByteBuffer, originalWidth: Int, originalHeight: Int): List<RectF>
    {
        // Get tensor dimensions
        val outputShape = detectorInterpreter!!.getOutputTensor(0).shape()
        val height = outputShape[1] // 304px
        val width = outputShape[2] // 400px

        // Extract text score map and link score map
        val scoreText = FloatArray(height * width)
        val scoreLink = FloatArray(height * width)

        // Read text score (channel 0) and link score (channel 1)
        for (y in 0 until height)
        {
            for (x in 0 until width)
            {
                val index = y * width + x
                scoreText[index] = output.getFloat()
            }
        }

        for (y in 0 until height)
        {
            for (x in 0 until width)
            {
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
                    val scaleX = originalWidth.toFloat() / width.toFloat()
                    val scaleY = originalHeight.toFloat() / height.toFloat()

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

        return regions
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
    ):
            RectF
    {
        val queue = mutableListOf(Pair(startX, startY))
        visited[startY][startX] = true

        var minX = startX
        var minY = startY
        var maxX = startX
        var maxY = startY

        while (queue.isNotEmpty())
        {
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

            for ((nx, ny) in neighbors)
            {
                if (nx in 0 until width && ny in 0 until height && !visited[ny][nx])
                {
                    val index = ny * width + nx

                    // Connect if text score or link score is high enough
                    if (scoreText[index] > detectorConfig.lowText || scoreLink[index] > detectorConfig.linkThreshold)
                    {
                        queue.add(Pair(nx, ny))
                        visited[ny][nx] = true
                    }
                }
            }
        }

        return RectF(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat())
    }

    /**
     * Configuration for detector parameters.
     */
    data class DetectorConfig
    (
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


    companion object
    {
        private const val TAG = "ImageProcessor"
        private const val FLOAT_TYPE_SIZE = 4
    }
}