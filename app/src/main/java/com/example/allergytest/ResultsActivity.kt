package com.example.allergytest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.allergytest.databinding.ActivityResultsBinding

class ResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get cropped image URI from intent
        val croppedImageUri = intent.getStringExtra("croppedImageUri")

        if (!croppedImageUri.isNullOrEmpty()) {
            val imageUri = Uri.parse(croppedImageUri)
            binding.resultImage.setImageURI(imageUri) //Correctly display cropped image
        } else {
            Toast.makeText(this, "No image found. Please take a picture first.", Toast.LENGTH_LONG).show()
        }

        //Load selected allergens
        val selectedAllergens = SelectionManager.loadSelections(this)
        if (selectedAllergens.isEmpty()) {
            binding.resultList.text = "No allergens selected."
        } else {
            binding.resultList.text = "Selected Allergens:\n" + selectedAllergens.joinToString("\n")
        }

        //Set up navigation buttons
        binding.navAllergens.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.navCamera.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        binding.navResults.setOnClickListener {
            Toast.makeText(this, "You are already on the Results page", Toast.LENGTH_SHORT).show()
        }
    }
}
