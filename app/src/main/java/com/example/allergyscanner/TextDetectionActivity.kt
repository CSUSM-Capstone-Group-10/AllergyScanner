package com.example.allergyscanner

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.allergytest.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextDetectionActivity : AppCompatActivity() {

    private lateinit var imageProcessor: ImageProcessor
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Creates a loading screen for the user
        setContentView(R.layout.activity_text_detection)

        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusTextView)

        // 1. Get the croppedImageUri from CropActivity
        val croppedImageUriStr = intent.getStringExtra("croppedImageUri")
        if (croppedImageUriStr.isNullOrEmpty()) {
            Log.e("TextDetectionActivity", "No croppedImageUri passed!")
            finish()
            return
        }

        // 2. Convert URI String -> Bitmap
        val croppedImageUri = Uri.parse(croppedImageUriStr)

        // Run the image processing in a background thread using Coroutines
        lifecycleScope.launch(Dispatchers.Main) {
            progressBar.visibility = View.VISIBLE  // Show progress bar while processing
            statusText.text = "Processing Image..."  // Update status message

            val bitmap: Bitmap = withContext(Dispatchers.IO) {
                MediaStore.Images.Media.getBitmap(contentResolver, croppedImageUri)
            }

            // 3. Initialize ImageProcessor
            imageProcessor = ImageProcessor(this@TextDetectionActivity)
            imageProcessor.initializeInterpreters()

            // 4. Run your OCR pipeline
            val recognizedText = imageProcessor.processImage(bitmap)
            Log.d("TextDetectionActivity", "OCR result: $recognizedText")

            // 5. Send the recognizedText + same croppedImageUri on to ResultsActivity
            val intent = Intent(this@TextDetectionActivity, ResultsActivity::class.java).apply {
                putExtra("croppedImageUri", croppedImageUriStr)
                putExtra("recognizedText", recognizedText)
            }
            startActivity(intent)
            finish() // optional: close TextDetectionActivity so user can't go back here

            // Hide progress bar after processing is complete
            progressBar.visibility = View.GONE
            statusText.text = "Text detection complete"
        }
    }
}
