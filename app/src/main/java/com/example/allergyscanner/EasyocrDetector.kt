package com.example.allergyscanner

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class EasyocrDetector(private val context: Context)
{
    private var detectorInterpreter: Interpreter? = null

    // Detector dimensions (from specifications)
    private val detectorBatch = 1
    private val detectorChannels = 3
    private val detectorHeight = 608
    private val detectorWidth = 800

    lateinit var preprocessedInput: PreprocessedData

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

    var isInitialized = false
        private set

    @Throws(IOException::class)
    fun initializeInterpreter()
    {
        // Load model
        val assetManager = context.assets

        // Initialize detector
        val detectorModel = loadModelFile(assetManager, "easyocr_detector.tflite")
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

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): ByteBuffer
    {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        try
        {
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
        finally
        {
            inputStream.close()
            fileDescriptor.close()
        }
    }

    fun runModel(bitmap: Bitmap): List<RectF>
    {
        // Preprocess the image and prepare input for the model
        this.preprocessedInput = preprocessDetector(bitmap)

        // Run the detector
        val detectorOutput = runDetector(preprocessedInput.detectorInput)

        // Postprocess the output. This contains the text regions in the image.
        val postprocessedResult = postProcessDetectorOutput(detectorOutput, bitmap.width, bitmap.height)

        Log.d(ImageProcessor.TAG, "Detected ${postprocessedResult.size} text regions")
        saveBitmapToCache(drawRegionsOnImage(preprocessedInput.grayscaleBitmap, postprocessedResult), context, "image_with_regions.png")

        return postprocessedResult
    }


    /**
     * Enhances text visibility and applies grayscaling to the image, then prepares the input for the model.
     */
    private fun preprocessDetector(bitmap: Bitmap) : PreprocessedData
    {
        val enhancedMap = enhanceTextVisibility(bitmap)
        val grayscaleBitmap = convertToGrayscale(enhancedMap)
        //val savedFile = saveBitmapToCache(grayscaleBitmap, context)
        //Log.d("Debug Image", "Processed image saved to: ${savedFile.absolutePath}")
        val preparedInput = prepareDetectorInput(bitmap)
        return PreprocessedData(bitmap, preparedInput)
    }

    /**
     * Enhances text visibility in the image through contrast adjustment.
     */
    private fun enhanceTextVisibility(bitmap: Bitmap): Bitmap
    {
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
    private fun convertToGrayscale(bitmap: Bitmap): Bitmap
    {
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

    // TODO DEBUG function: Saves bitmap image to emulator/device cache to verify output
    fun saveBitmapToCache(bitmap: Bitmap, context: Context, filename: String): File
    {
        // Create a file in the app's cache directory
        val file = File(context.cacheDir, filename)

        try
        {
            val outputStream = FileOutputStream(file)
            // Compress the bitmap as PNG and write to the file
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
        }
        catch (e: IOException)
        {
            e.printStackTrace()
        }
        return file
    }

    /**
     * Prepares input for the detector model.
     */
    private fun prepareDetectorInput(bitmap: Bitmap): ByteBuffer
    {
        // Resize bitmap to detector dimensions
        val resized = Bitmap.createScaledBitmap(bitmap, detectorWidth, detectorHeight, true)

        // Debug: saves resized image to device cache
        saveBitmapToCache(resized, context, "resized_image.png")

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
        for (y in 0 until detectorHeight)
        {
            for (x in 0 until detectorWidth)
            {
                val pixel = pixels[y * detectorWidth + x]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                buffer.putFloat((r - mean[0]) / std[0])
            }
        }

        // Green channel
        for (y in 0 until detectorHeight)
        {
            for (x in 0 until detectorWidth)
            {
                val pixel = pixels[y * detectorWidth + x]
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                buffer.putFloat((g - mean[1]) / std[1])
            }
        }

        // Blue channel
        for (y in 0 until detectorHeight)
        {
            for (x in 0 until detectorWidth)
            {
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
        val height = outputShape[1]
        val width = outputShape[2]

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

    // TODO DEBUG function: for viewing the bounding boxes generate by model onto image
    private fun drawRegionsOnImage(bitmap: Bitmap, regions: List<RectF>): Bitmap
    {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }

        for (region in regions)
        {
            canvas.drawRect(region, paint)
        }

        return mutableBitmap
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

    data class PreprocessedData
    (
        val grayscaleBitmap: Bitmap,
        val detectorInput: ByteBuffer
    )

    companion object
    {
        private const val TAG = "ImageProcessor"
        private const val FLOAT_TYPE_SIZE = 4
    }
}