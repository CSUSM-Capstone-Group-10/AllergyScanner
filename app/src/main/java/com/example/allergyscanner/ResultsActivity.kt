package com.example.allergyscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allergytest.R
import com.example.allergytest.databinding.ActivityResultsBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --------------------------------------------------------------------
        // 1. Retrieve data from Intent
        // --------------------------------------------------------------------
        val croppedImageUri = intent.getStringExtra("croppedImageUri")
        val recognizedText = intent.getStringExtra("recognizedText")
        // NEW: if this is "true", we skip re-saving to history
        val isFromHistory = intent.getBooleanExtra("isFromHistory", false)

        // --------------------------------------------------------------------
        // 2. Display Cropped Image
        // --------------------------------------------------------------------
        if (!croppedImageUri.isNullOrEmpty()) {
            val imageUri = Uri.parse(croppedImageUri)
            binding.resultImage.setImageURI(imageUri)
            //apply visibility to image on results page
            binding.resultImage.visibility = android.view.View.VISIBLE
            //removes noPhotoWarning in case it was visible
            binding.noPhotoWarning.visibility = android.view.View.GONE
        // --------------------------------------------------------------------
        // 3. Display recognized text, or let user know if none found
        // --------------------------------------------------------------------
            if (recognizedText.isNullOrEmpty()) {
                binding.detectedTextView.text = "No text recognized."
                binding.detectedTextView.visibility = android.view.View.VISIBLE
            } else {
                binding.detectedTextView.text = "\n$recognizedText"
                binding.detectedTextView.visibility = android.view.View.VISIBLE
                binding.detectedAllergensTitle.visibility = android.view.View.VISIBLE
            }
        // --------------------------------------------------------------------
        // 4. Load and display user-selected allergens
        // --------------------------------------------------------------------
            val selectedAllergens = SelectionManager.loadSelections(this)

            // Check if no allergens were selected
            if (selectedAllergens.isEmpty()) {
                binding.allergenWarning.visibility = android.view.View.GONE

            }
            else {
                // --------------------------------------------------------------------
                // 5. Compare recognized text to allergens (simple substring check)
                //    This also works for history items because recognizedText is set
                //    from the intent, whether new or old.
                // --------------------------------------------------------------------
                val foundAllergens = mutableListOf<String>()
                if (!recognizedText.isNullOrEmpty()) {
                    for (allergen in selectedAllergens) {
                        if (recognizedText.contains(allergen, ignoreCase = true)) {
                            foundAllergens.add(allergen)
                        }
                    }
                }
                // --------------------------------------------------------------------
                // 6. Show warning if we found anything
                // --------------------------------------------------------------------
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
            // --------------------------------------------------------------------
            // 7. Save this scan to history ONLY if it's a brand-new scan
            //    (not loaded from history).
            // --------------------------------------------------------------------
            if (!isFromHistory) {
                saveScanToHistory(
                    croppedUri = croppedImageUri ?: "",
                    recognized = recognizedText ?: ""
                )
            }
        }

        //No photo has been taken, display appropriate errors
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


        // --------------------------------------------------------------------
        // 8. Show history bottom sheet if user taps the history icon
        // --------------------------------------------------------------------
        binding.historyIcon.setOnClickListener {
            showHistoryBottomSheet()
        }

        // --------------------------------------------------------------------
        // 9. Navigation buttons
        // --------------------------------------------------------------------
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

    /**
     * Saves the current scan to SharedPreferences, retaining only the last 10.
     */
    private fun saveScanToHistory(croppedUri: String, recognized: String) {
        try {
            val prefs = getSharedPreferences("scan_history_prefs", MODE_PRIVATE)
            val oldJson = prefs.getString("scan_history", "[]") ?: "[]"

            // Convert existing JSON into a MutableList
            val type = object : TypeToken<MutableList<ScanHistoryItem>>() {}.type
            val historyList: MutableList<ScanHistoryItem> = Gson().fromJson(oldJson, type)

            Log.d("saveScanToHistory", "Saving image: $croppedUri, text: $recognized")
            // Build new item
            val newItem = ScanHistoryItem(
                dateTime = System.currentTimeMillis(),
                imageUri = croppedUri,
                recognizedText = recognized
            )

            // Insert at front
            historyList.add(0, newItem)

            // Keep only up to 10 items
            while (historyList.size > 10) {
                historyList.removeAt(historyList.lastIndex)
            }

            // Save back to SharedPreferences
            val updatedJson = Gson().toJson(historyList)
            prefs.edit().putString("scan_history", updatedJson).apply()
            Log.d("saveScanToHistory", "Updated history: $updatedJson")
        } catch (e: Exception) {
            Log.e("ResultsActivity", "Error saving scan to history", e)
        }

    }

    /**
     * Opens a BottomSheetDialog to display the last 10 scans and let the user load an old scan.
     */
    private fun showHistoryBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_history_bottomsheet, null)
        bottomSheetDialog.setContentView(view)

        val recyclerView = view.findViewById<RecyclerView>(R.id.historyRecyclerView)

        // Load from SharedPreferences
        val prefs = getSharedPreferences("scan_history_prefs", MODE_PRIVATE)
        val json = prefs.getString("scan_history", "[]") ?: "[]"
        val type = object : TypeToken<List<ScanHistoryItem>>() {}.type
        val historyList: List<ScanHistoryItem> = Gson().fromJson(json, type)

        // Create adapter
        val adapter = HistoryAdapter(historyList) { selectedItem ->
            // user taps on an old scan => reload the ResultsActivity
            loadOldScan(selectedItem)
            bottomSheetDialog.dismiss()
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        bottomSheetDialog.show()
    }

    /**
     * Reload a previously saved scan in the same ResultsActivity.
     * We'll set "isFromHistory" to true so we don't re-save this to the list.
     */
    private fun loadOldScan(item: ScanHistoryItem) {
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("croppedImageUri", item.imageUri)
            putExtra("recognizedText", item.recognizedText)
            // This flag indicates it's an old scan, so skip save.
            putExtra("isFromHistory", true)
        }
        startActivity(intent)
        finish()
    }

    // ---------------------------------------------------------------------------------------
    // Data Classes & Adapter for History
    // ---------------------------------------------------------------------------------------

    /**
     * Represents a past scan (time, image, recognized text).
     */
    data class ScanHistoryItem(
        val dateTime: Long,
        val imageUri: String,
        val recognizedText: String
    ) : Serializable

    /**
     * Simple RecyclerView Adapter to show each ScanHistoryItem.
     */
    inner class HistoryAdapter(
        private val items: List<ScanHistoryItem>,
        private val onItemClick: (ScanHistoryItem) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): HistoryViewHolder {
            val view = layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            return HistoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size

        inner class HistoryViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            private val text1: android.widget.TextView = itemView.findViewById(android.R.id.text1)
            private val text2: android.widget.TextView = itemView.findViewById(android.R.id.text2)

            fun bind(item: ScanHistoryItem) {
                // Format date/time
                val dateTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(item.dateTime))

                text1.text = dateTimeStr

                // Show snippet of recognized text
                val snippet = if (item.recognizedText.length > 50) {
                    item.recognizedText.substring(0, 50) + "..."
                } else {
                    item.recognizedText
                }
                text2.text = snippet
            }
        }
    }

    // Creates a bubble around warning (allergy detected/not detected)
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
