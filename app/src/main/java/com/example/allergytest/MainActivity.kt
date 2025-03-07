package com.example.allergytest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.allergytest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var allergens = listOf(
        AllergenCategory("Dairy", mutableListOf(AllergenItem("Milk"), AllergenItem("Cheese"), AllergenItem("Butter"))),
        AllergenCategory("Nuts", mutableListOf(AllergenItem("Peanuts"), AllergenItem("Walnuts"), AllergenItem("Almonds"))),
        AllergenCategory("Seafood", mutableListOf(AllergenItem("Shrimp"), AllergenItem("Crab"), AllergenItem("Salmon")))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Load previously saved selections
        loadSelectedAllergens()

        // ✅ Keep existing allergen selection functionality
        val adapter = AllergenAdapter(this, allergens, binding.allergenExpandableList)
        binding.allergenExpandableList.setAdapter(adapter)

        // ✅ Set up Save Button functionality
        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            saveSelectedAllergens()
        }

        // ✅ Bottom Navigation Setup
        val navAllergens: ImageView = findViewById(R.id.navAllergens)
        val navCamera: ImageView = findViewById(R.id.navCamera)
        val navResults: ImageView = findViewById(R.id.navResults)

        navAllergens.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        navCamera.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        navResults.setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
    }

    // ✅ Save Selected Allergens to SharedPreferences
    private fun saveSelectedAllergens() {
        val sharedPreferences = getSharedPreferences("AllergenPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val selectedAllergens = allergens
            .flatMap { it.items }
            .filter { it.isSelected }
            .map { it.name }

        editor.putStringSet("selectedAllergens", selectedAllergens.toSet())
        editor.apply()

        // ✅ Show a confirmation message
        Toast.makeText(this, "Allergen selection saved!", Toast.LENGTH_SHORT).show()
    }

    // ✅ Load Previously Saved Allergens
    private fun loadSelectedAllergens() {
        val sharedPreferences = getSharedPreferences("AllergenPrefs", Context.MODE_PRIVATE)
        val savedAllergens = sharedPreferences.getStringSet("selectedAllergens", emptySet()) ?: emptySet()

        allergens.forEach { category ->
            category.items.forEach { subItem ->
                subItem.isSelected = savedAllergens.contains(subItem.name)
            }
            category.isSelected = category.items.all { it.isSelected } // ✅ If all subitems are selected, check category
        }
    }
}
