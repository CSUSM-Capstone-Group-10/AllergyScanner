package com.example.allergytest

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

        // ✅ Get the image path from CameraActivity
        val imagePath = intent.getStringExtra("imagePath")
        if (imagePath == null) {
            Log.e("CropActivity", "No image path received!")
            finish()
            return
        }

        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            Log.e("CropActivity", "Image file does not exist: $imagePath")
            finish()
            return
        }

        Log.d("CropActivity", "Received image path: $imagePath")

        // ✅ Set up UCrop
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_image.jpg"))
        UCrop.of(Uri.fromFile(imageFile), destinationUri)
            .withAspectRatio(1f, 1f) // Set desired crop ratio
            .withMaxResultSize(500, 500) // Max cropped image size
            .start(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            if (resultUri != null) {
                Log.d("CropActivity", "Cropped image URI: $resultUri")

                // ✅ Send the cropped image to ResultsActivity
                val intent = Intent(this, ResultsActivity::class.java)
                intent.putExtra("croppedImageUri", resultUri.toString())
                startActivity(intent)
                finish() // ✅ Close CropActivity after cropping
            }
        } else if (requestCode == UCrop.REQUEST_CROP) {
            Log.e("CropActivity", "Cropping canceled!")
            finish() // If crop was canceled, return to previous screen
        }
    }
}
