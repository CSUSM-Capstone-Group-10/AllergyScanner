package com.example.allergyscanner

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace.Model
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
    val TAG = "ImageProcessor"
    private val EasyocrDetector by lazy { EasyocrDetector(context) }
    private val EasyocrRecognizer by lazy { EasyocrRecognizer(context) }
    private val ModelUtilityFunctions by lazy { ModelUtilityFunctions() }

    var isInitialized = false
        private set

    @Throws(IOException::class)
    fun initializeInterpreters()
    {
        EasyocrDetector.initializeInterpreter()
        EasyocrRecognizer.initializeInterpreter()
        Log.d(TAG, "Initialized detector and recognizer")
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
                val croppedRegion = ModelUtilityFunctions.cropTextRegion(EasyocrDetector.preprocessedInput.grayscaleBitmap, region)
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
}