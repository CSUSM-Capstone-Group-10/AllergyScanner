package com.example.allergyscanner

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

        // 1. Get cropped image URI from intent
        val croppedImageUri = intent.getStringExtra("croppedImageUri")
        // 2. Get recognized text from intent
        val recognizedText = intent.getStringExtra("recognizedText")

        // Display cropped image
        if (!croppedImageUri.isNullOrEmpty()) {
            val imageUri = Uri.parse(croppedImageUri)
            binding.resultImage.setImageURI(imageUri)
        } else {
            Toast.makeText(
                this,
                "No image found. Please take a picture first.",
                Toast.LENGTH_LONG
            ).show()
        }

        // Display recognized text (or tell the user if none found)
        if (recognizedText.isNullOrEmpty()) {
            binding.detectedTextView.text = "No text recognized."
        } else {
            binding.detectedTextView.text = "Recognized Ingredients:\n$recognizedText"
        }

        // Now load the user-selected allergens
        val selectedAllergens = SelectionManager.loadSelections(this)
        if (selectedAllergens.isEmpty()) {
            binding.resultList.text = "No allergens selected."
        } else {
            binding.resultList.text = "Your Selected Allergens:\n" +
                    selectedAllergens.joinToString("\n")
        }

        // Compare recognized text to each allergen
        // (This is a simple substring check – you might want more advanced matching or tokenization.)
        val foundAllergens = mutableListOf<String>()
        if (!recognizedText.isNullOrEmpty()) {
            for (allergen in selectedAllergens) {
                if (recognizedText.contains(allergen, ignoreCase = true)) {
                    foundAllergens.add(allergen)
                }
            }
        }

        // Show a warning if we found anything
        if (foundAllergens.isNotEmpty()) {
            binding.allergenWarning.text = "WARNING! Found allergens: ${foundAllergens.joinToString(", ")}"
        } else {
            binding.allergenWarning.text = "No selected allergens found in text."
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

