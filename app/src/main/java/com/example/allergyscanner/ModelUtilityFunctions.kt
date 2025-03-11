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

class ModelUtilityFunctions
{
    private val TAG = "ImageProcessor"

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
        Log.d("Debug Image", "Processed image saved to: ${file.absolutePath}")
        return file
    }

    fun drawRegionsOnImage(bitmap: Bitmap, regions: List<RectF>): Bitmap
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

        return bitmap
    }

    /**
     * Crops a region from the input bitmap based on the specified bounding box.
     */
    fun cropTextRegion(bitmap: Bitmap, region: RectF): Bitmap
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

    fun loadModelFile(assetManager: AssetManager, modelPath: String): ByteBuffer
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
}