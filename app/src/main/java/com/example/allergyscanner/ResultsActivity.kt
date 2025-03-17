package com.example.allergyscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.allergytest.R
import com.example.allergytest.databinding.ActivityResultsBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

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
            //apply visibility to image on results page
            binding.resultImage.visibility = android.view.View.VISIBLE
            //removes noPhotoWarning in case it was visible
            binding.noPhotoWarning.visibility = android.view.View.GONE

            // Display recognized text (or tell the user if none found)
            if (recognizedText.isNullOrEmpty()) {
                binding.detectedTextView.text = "No text recognized."
                binding.detectedTextView.visibility = android.view.View.VISIBLE
            } else {
                binding.detectedTextView.text = "\n$recognizedText"
                binding.detectedTextView.visibility = android.view.View.VISIBLE
                binding.detectedAllergensTitle.visibility = android.view.View.VISIBLE
            }

            // Now load the user-selected allergens
            val selectedAllergens = SelectionManager.loadSelections(this)

            // Check if no allergens were selected
            if (selectedAllergens.isEmpty()) {
                binding.allergenWarning.visibility = android.view.View.GONE

            }
            // Allergens were selected
            else {
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
                    binding.allergenWarning.text =
                        "${foundAllergens.joinToString("\n")}"
                    binding.allergenWarning.visibility = android.view.View.VISIBLE
                    updateAllergyStatusBubble(foundAllergens)
                } else {
                    binding.allergenWarning.text = "No selected allergens found in the image."
                    binding.allergenWarning.visibility = android.view.View.VISIBLE
                    updateAllergyStatusBubble(emptyList())
                }
            }
        }
        else {
            //show no photo taken warning
            binding.noPhotoWarning.text = "No photo to detect!"
            binding.noPhotoWarning.visibility = android.view.View.VISIBLE
            Toast.makeText(
                this,
                "No image found. Please take a picture first.",
                Toast.LENGTH_LONG
            ).apply {
                setGravity(Gravity.TOP, 0, 0) // This centers the toast on the screen
                show()
            }

            // If no image, hide allergen warning
            binding.allergenWarning.visibility = android.view.View.GONE
        }

        // Bottom Navigation Setup
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottomNav)
        bottomNavigationView.selectedItemId = R.id.navResults
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navAllergens -> {
                    // Handle "Allergens" selection
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.navCamera -> {
                    // Handle "Camera" selection
                    startActivity(Intent(this, CameraActivity::class.java))
                    true
                }
                R.id.navResults -> {
                    // Handle "Results" selection
                    startActivity(Intent(this, ResultsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }
    private fun updateAllergyStatusBubble(foundAllergens: List<String>) {
        // Find the bubble view (TextView)
        val bubbleTextView = binding.allergyStatusBubble

        // If allergens are found, update the bubble to red with a warning
        if (foundAllergens.isNotEmpty()) {
            bubbleTextView.text = "Allergens Detected!"
            bubbleTextView.setBackgroundResource(R.drawable.bubble_shape_red)
            bubbleTextView.setTextColor(resources.getColor(android.R.color.white))
            bubbleTextView.visibility = android.view.View.VISIBLE
        } else {
            // No allergens detected, update bubble to green with a check
            bubbleTextView.text = "No Allergens Detected"
            bubbleTextView.setBackgroundResource(R.drawable.bubble_shape_green)
            bubbleTextView.setTextColor(resources.getColor(android.R.color.white))
            bubbleTextView.visibility = android.view.View.VISIBLE
        }
    }
}


