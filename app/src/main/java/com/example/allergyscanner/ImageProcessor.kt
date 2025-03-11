package com.example.allergyscanner

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class ImageProcessor(private val context: Context)
{
    private val EasyocrDetector by lazy { EasyocrDetector(context) }
    private val EasyocrRecognizer by lazy { EasyocrRecognizer(context) }

    var isInitialized = false
        private set

    @Throws(IOException::class)
    fun initializeInterpreters()
    {
        EasyocrDetector.initializeInterpreter()
        EasyocrRecognizer.initializeInterpreter()
        Log.d(TAG, "Initialized detectors and recognizers")
    }


    fun processImage(bitmap: Bitmap): String
    {
        check(EasyocrRecognizer.isInitialized && EasyocrDetector.isInitialized) { "Interpreters not initialized" }

        try
        {
            val detectedTextRegions = EasyocrDetector.runModel(bitmap)

            // If no text regions are detected, have the OCR attempt extracting text from the entire image
            if (detectedTextRegions.isEmpty())
            {
                Log.d(TAG, "No text regions detected, attempting to extract text from entire image")
                val fallbackText = EasyocrRecognizer.runModelFallback(EasyocrDetector.preprocessedInput.grayscaleBitmap)
                Log.d(TAG, "Text extracted from full image: $fallbackText")
                return "Full image: $fallbackText"
            }

            // Assuming now that there are detected text regions, attempt to recognize text in each detected region
            val results = mutableListOf<String>()

            // iterate over each detected text region/box
            for ((index, region) in detectedTextRegions.withIndex())
            {
                val croppedRegion = cropTextRegion(EasyocrDetector.preprocessedInput.grayscaleBitmap, region)
                // saveBitmapToCache(croppedRegion, context, "cropped_region$index.png") // This looks OK


                val recognizerOutputText = EasyocrRecognizer.runModel(croppedRegion, index)
                val cleanedOutputText = EasyocrRecognizer.cleanupRecognitionResult(recognizerOutputText)
                Log.d(TAG, "Region $index: $recognizerOutputText (cleaned: $cleanedOutputText)")

                if (cleanedOutputText.isNotEmpty())
                {
                    results.add(cleanedOutputText)
                    Log.d(TAG, "Region $index: $cleanedOutputText (raw: $recognizerOutputText)")
                }
            }

            val resultText = results.joinToString("\n")
            Log.d(TAG, "Final results: $resultText")
            return resultText

        }
        catch (e: Exception)
        {
            Log.e(TAG, "Error in image processing pipeline", e)
            return "Error: ${e.message ?: "Unknown error"}"
        }
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

    // TODO DEBUG function: for viewing the bounding boxes generate by model onto image
    private fun drawRegionsOnImage(bitmap: Bitmap, regions: List<RectF>): Bitmap
    {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }

        for (region in regions)
        {
            canvas.drawRect(region, paint)
        }

        return bitmap
    }


    /**
     * Crops a region from the input bitmap based on the specified bounding box.
     */
    private fun cropTextRegion(bitmap: Bitmap, region: RectF): Bitmap
    {
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

        try
        {
            // Create a cropped bitmap from the region
            return Bitmap.createBitmap(bitmap, x, y, width, height)
        }
        catch (e: Exception)
        {
            Log.e(TAG, "Error cropping region: $region", e)
            return bitmap
        }
    }

    companion object
    {
        const val TAG = "ImageProcessor"
        private const val FLOAT_TYPE_SIZE = 4
    }
}