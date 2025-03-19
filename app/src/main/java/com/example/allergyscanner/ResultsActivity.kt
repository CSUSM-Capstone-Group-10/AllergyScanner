package com.example.allergyscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allergytest.R
import com.example.allergytest.databinding.ActivityResultsBinding
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
        } else {
            Toast.makeText(
                this,
                "No image found. Please take a picture first.",
                Toast.LENGTH_LONG
            ).show()
        }

        // --------------------------------------------------------------------
        // 3. Display recognized text, or let user know if none found
        // --------------------------------------------------------------------
        if (recognizedText.isNullOrEmpty()) {
            binding.detectedTextView.text = "No text recognized."
        } else {
            binding.detectedTextView.text = "Recognized Ingredients:\n$recognizedText"
        }

        // --------------------------------------------------------------------
        // 4. Load and display user-selected allergens
        // --------------------------------------------------------------------
        val selectedAllergens = SelectionManager.loadSelections(this)
        if (selectedAllergens.isEmpty()) {
            binding.resultList.text = "No allergens selected."
        } else {
            binding.resultList.text = "Your Selected Allergens:\n" +
                    selectedAllergens.joinToString("\n")
        }

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
                "WARNING! Found allergens: ${foundAllergens.joinToString(", ")}"
        } else {
            binding.allergenWarning.text = "No selected allergens found in text."
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

        // --------------------------------------------------------------------
        // 8. Show history bottom sheet if user taps the history icon
        // --------------------------------------------------------------------
        binding.historyIcon.setOnClickListener {
            showHistoryBottomSheet()
        }

        // --------------------------------------------------------------------
        // 9. Navigation buttons
        // --------------------------------------------------------------------
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
}
