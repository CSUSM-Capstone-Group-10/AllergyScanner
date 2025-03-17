package com.example.allergyscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.yalantis.ucrop.UCrop
import java.io.File

class CropActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the image URI from CameraActivity
        val imageUriString = intent.getStringExtra("imageUri")
        if (imageUriString == null) {
            Log.e("CropActivity", "No image path received!")
            finish()
            return
        }

        val imageUri = Uri.parse(imageUriString)
        if (imageUri == null) {
            Log.e("CropActivity", "Image file does not exist: $imageUriString")
            finish()
            return
        }

        Log.d("CropActivity", "Received image path: $imageUri")

        //Set up UCrop
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_image.jpg"))
        // Create UCrop options
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true) // Enable free-style crop
            setShowCropFrame(true) // Show crop frame
            setShowCropGrid(true) // Show crop grid
            setHideBottomControls(false) // Show bottom controls for better UX
        }

        // Start UCrop with our options
        UCrop.of(imageUri, destinationUri)
            .withOptions(options)
            .withMaxResultSize(1080, 1080) // Max resolution, but maintains aspect ratio of crop
            .start(this)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            if (resultUri != null) {
                Log.d("CropActivity", "Cropped image URI: $resultUri")

                // Instead of going to ResultsActivity directly,
                // we now go to TextDetectionActivity
                val intent = Intent(this, TextDetectionActivity::class.java)
                intent.putExtra("croppedImageUri", resultUri.toString())
                startActivity(intent)
                finish() // Close CropActivity
            }
        } else if (requestCode == UCrop.REQUEST_CROP) {
            Log.e("CropActivity", "Cropping canceled!")
            finish() // If crop was canceled, return to previous screen
        }
    }
}
