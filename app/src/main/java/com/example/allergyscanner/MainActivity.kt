package com.example.allergyscanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.allergytest.R
import com.example.allergytest.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AllergenAdapter
    private var allergens = listOf(
        AllergenCategory("Dairy", mutableListOf(AllergenItem("Milk"), AllergenItem("Cheese"), AllergenItem("Butter"))),
        AllergenCategory("Nuts", mutableListOf(AllergenItem("Peanuts"), AllergenItem("Walnuts"), AllergenItem("Almonds"))),
        AllergenCategory("Seafood", mutableListOf(AllergenItem("Shrimp"), AllergenItem("Crab"), AllergenItem("Salmon")))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Load previously saved selections
        loadSelectedAllergens()

        //Keep existing allergen selection functionality
        adapter = AllergenAdapter(this, allergens.toMutableList(), binding.allergenExpandableList)
        binding.allergenExpandableList.setAdapter(adapter)

        //Set up Save Button functionality
        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            saveSelectedAllergens()
        }


        //search bar functionality
        val searchBar: EditText = findViewById(R.id.search_bar)
        searchBar.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterAllergens(s.toString()) //calls filter allergens function when text is changed in search bar
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        // Bottom Navigation Setup
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottomNav)
        bottomNavigationView.selectedItemId = R.id.navAllergens

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

    //Save Selected Allergens to SharedPreferences
    private fun saveSelectedAllergens() {
        val sharedPreferences = getSharedPreferences("AllergenPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val selectedAllergens = allergens
            .flatMap { it.items }
            .filter { it.isSelected }
            .map { it.name }

        editor.putStringSet("selectedAllergens", selectedAllergens.toSet())
        editor.apply()

        // Show a confirmation message
        Toast.makeText(this, "Allergen selection saved!", Toast.LENGTH_SHORT).show()
    }

    //Load Previously Saved Allergens
    private fun loadSelectedAllergens() {
        val sharedPreferences = getSharedPreferences("AllergenPrefs", Context.MODE_PRIVATE)
        val savedAllergens = sharedPreferences.getStringSet("selectedAllergens", emptySet()) ?: emptySet()

        allergens.forEach { category ->
            category.items.forEach { subItem ->
                subItem.isSelected = savedAllergens.contains(subItem.name)
            }
            category.isSelected = category.items.all { it.isSelected } //If all subitems are selected, check category
        }
    }

    private fun filterAllergens(query: String) {
        val filteredAllergens = allergens.map { category ->
            val filteredItems = category.items.filter { it.name.contains(query, ignoreCase = true) }
                .toMutableList()
            AllergenCategory(category.name, filteredItems)
        }.filter { it.items.isNotEmpty() }

        adapter.updateData(filteredAllergens)
        //expands view of allergen list when searching, so user can see results
        for (i in 0 until adapter.groupCount) {
            binding.allergenExpandableList.expandGroup(i)
        }
    }
}

