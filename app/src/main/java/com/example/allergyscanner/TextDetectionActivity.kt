package com.example.allergyscanner

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TextDetectionActivity : AppCompatActivity() {

    private lateinit var imageProcessor: ImageProcessor
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate a basic layout with a progress bar and text
        // or create one if you need. For example:
        // setContentView(R.layout.activity_text_detection)

        // Suppose in activity_text_detection.xml you have:
        // <ProgressBar android:id="@+id/progressBar" ... />
        // <TextView android:id="@+id/statusTextView" ... />

        // If you have these views, you’d do:
        // progressBar = findViewById(R.id.progressBar)
        // statusText = findViewById(R.id.statusTextView)

        // 1. Get the croppedImageUri from CropActivity
        val croppedImageUriStr = intent.getStringExtra("croppedImageUri")
        if (croppedImageUriStr.isNullOrEmpty()) {
            Log.e("TextDetectionActivity", "No croppedImageUri passed!")
            finish()
            return
        }

        // 2. Convert URI String -> Bitmap
        val croppedImageUri = Uri.parse(croppedImageUriStr)
        val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, croppedImageUri)

        // 3. Initialize ImageProcessor (which loads your TFLite models)
        imageProcessor = ImageProcessor(this)
        imageProcessor.initializeInterpreters()

        // 4. Run your OCR pipeline
        val recognizedText = imageProcessor.processImage(bitmap)
        Log.d("TextDetectionActivity", "OCR result: $recognizedText")

        // 5. Send the recognizedText + same croppedImageUri on to ResultsActivity
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("croppedImageUri", croppedImageUriStr)
            putExtra("recognizedText", recognizedText)
        }
        startActivity(intent)
        finish() // optional: close TextDetectionActivity so user can't go back here
    }
}
