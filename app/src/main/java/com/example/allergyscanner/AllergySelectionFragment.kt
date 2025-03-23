package com.example.allergyscanner

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.allergytest.R
import com.example.allergytest.databinding.FragmentAllergySelectionBinding

class AllergySelectionFragment : Fragment() {
    private var _binding: FragmentAllergySelectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AllergenAdapter
    private var allergens = listOf(
        AllergenCategory(
            "Dairy",
            mutableListOf(
                AllergenItem("Butter"),
                AllergenItem("Butter Fat"),
                AllergenItem("Butter Oil"),
                AllergenItem("Buttermilk"),
                AllergenItem("Artificial Butter Flavor"),
                AllergenItem("Casein"),
                AllergenItem("Caseinates"),
                AllergenItem("Cheese"),
                AllergenItem("Cream"),
                AllergenItem("Cottage Cheese"),
                AllergenItem("Curds"),
                AllergenItem("Custard"),
                AllergenItem("Ghee"),
                AllergenItem("Half & Half"),
                AllergenItem("Hydrolysates"),
                AllergenItem("Lactalbumin"),
                AllergenItem("Lactalbumin Phosphate"),
                AllergenItem("Lactoglobulin"),
                AllergenItem("Lactose"),
                AllergenItem("Lactulose"),
                AllergenItem("Milk"),
                AllergenItem("Nougat"),
                AllergenItem("Pudding"),
                AllergenItem("Rennet Casein"),
                AllergenItem("Sour Cream"),
                AllergenItem("Sour Cream Solids"),
                AllergenItem("Whey"),
                AllergenItem("Yogurt")
            )
        ),
        AllergenCategory(
            "Eggs",
            mutableListOf(
                AllergenItem("Albumin"),
                AllergenItem("Egg"),
                AllergenItem("Egg Substitutes"),
                AllergenItem("Eggnog"),
                AllergenItem("Globulin"),
                AllergenItem("Livetin"),
                AllergenItem("Lysozyme"),
                AllergenItem("Mayonnaise"),
                AllergenItem("Meringue"),
                AllergenItem("Ovalbumin"),
                AllergenItem("Ovomucoid"),
                AllergenItem("Ovovitellin"),
                AllergenItem("Simplesse"),
                AllergenItem("Surimi"),
                AllergenItem("Lecithin"),
                AllergenItem("Marzipan"),
                AllergenItem("Marshmallows"),
                AllergenItem("Pasta"),
                AllergenItem("Egg Flavors")
            )
        ),
        AllergenCategory(
            "Peanuts",
            mutableListOf(
                AllergenItem("Beer Nuts"),
                AllergenItem("Peanut Oil"),
                AllergenItem("Ground Nuts"),
                AllergenItem("Mixed Nuts"),
                AllergenItem("Monkey Nuts"),
                AllergenItem("Nu-Nuts Flavored Nuts"),
                AllergenItem("Nut Pieces"),
                AllergenItem("Peanut"),
                AllergenItem("Peanut Butter"),
                AllergenItem("Peanut Flour"),
                AllergenItem("Peanut Protein"),
                AllergenItem("Hydrolyzed Peanut Protein"),
                AllergenItem("Egg Rolls"),
                AllergenItem("Marzipan"),
                AllergenItem("Nougat"),
                AllergenItem("Sunflower Seeds"),
            )
        ),
        AllergenCategory(
            "Soybeans",
            mutableListOf(
                AllergenItem("Hydrolyzed Soy Protein"),
                AllergenItem("Miso"),
                AllergenItem("Shoyu Sauce"),
                AllergenItem("Soy"),
                AllergenItem("Soya"),
                AllergenItem("Soybean"),
                AllergenItem("Soy Protein"),
                AllergenItem("Soy Sauce"),
                AllergenItem("Tamari"),
                AllergenItem("Tempeh"),
                AllergenItem("Textured Vegetable Protein (TVP)"),
                AllergenItem("Tofu")
            )
        ),
        AllergenCategory(
            "Tree Nuts",
            mutableListOf(
                AllergenItem("Almonds"),
                AllergenItem("Brazil Nuts"),
                AllergenItem("Caponata"),
                AllergenItem("Cashews"),
                AllergenItem("Chestnuts"),
                AllergenItem("Hazelnut"),
                AllergenItem("Gianduja"),
                AllergenItem("Hickory Nuts"),
                AllergenItem("Macadamia Nuts"),
                AllergenItem("Marzipan"),
                AllergenItem("Nougat"),
                AllergenItem("Nu-Nut"),
                AllergenItem("Nut Meal"),
                AllergenItem("Nut Oil"),
                AllergenItem("Nut Paste"),
                AllergenItem("Nut Pieces"),
                AllergenItem("Pecans"),
                AllergenItem("Pesto"),
                AllergenItem("Pine Nuts"),
                AllergenItem("Pistachios"),
                AllergenItem("Walnuts")
            )
        ),
        AllergenCategory(
            "Wheat",
            mutableListOf(
                AllergenItem("Bran"),
                AllergenItem("Bread Crumbs"),
                AllergenItem("Bulgar"),
                AllergenItem("Cereal Extract"),
                AllergenItem("Couscous"),
                AllergenItem("Cracker Meal"),
                AllergenItem("Durum"),
                AllergenItem("Durum Flour"),
                AllergenItem("Enriched Flour"),
                AllergenItem("Farina"),
                AllergenItem("Flour"),
                AllergenItem("Gluten"),
                AllergenItem("Kamut"),
                AllergenItem("Seitan"),
                AllergenItem("Semolina"),
                AllergenItem("Spelt"),
                AllergenItem("Vital Gluten"),
                AllergenItem("Wheat"),
                AllergenItem("Whole Wheat Berries"),
                AllergenItem("Whole Wheat Flour")
            )
        ),
        AllergenCategory(
            "Crustaceans and Shellfish",
            mutableListOf(
                AllergenItem("Crab"),
                AllergenItem("Crawfish"),
                AllergenItem("Lobster"),
                AllergenItem("Prawns"),
                AllergenItem("Shrimp"),
                AllergenItem("Snails"),
                AllergenItem("Abalone"),
                AllergenItem("Clams"),
                AllergenItem("Mussels"),
                AllergenItem("Oysters"),
                AllergenItem("Scallops")
            )
        ),
        AllergenCategory(
            "Fish",
            mutableListOf(
                AllergenItem("Bass"),
                AllergenItem("Catfish"),
                AllergenItem("Cod"),
                AllergenItem("Halibut"),
                AllergenItem("Herring"),
                AllergenItem("Mackerel"),
                AllergenItem("Octopus"),
                AllergenItem("Pollock"),
                AllergenItem("Sardines"),
                AllergenItem("Salmon"),
                AllergenItem("Snapper"),
                AllergenItem("Squid"),
                AllergenItem("Swordfish"),
                AllergenItem("Tilapia"),
                AllergenItem("Trout"),
                AllergenItem("Tuna")
            )
        ),
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllergySelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load previously saved selections
        loadSelectedAllergens()

        // Setup allergen adapter
        adapter = AllergenAdapter(requireContext(), allergens.toMutableList(), binding.allergenExpandableList)
        binding.allergenExpandableList.setAdapter(adapter)

        // Set up Save Button functionality
        val saveButton: Button = view.findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            saveSelectedAllergens()
        }

        // Search bar functionality
        val searchBar: EditText = view.findViewById(R.id.search_bar)
        searchBar.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterAllergens(s.toString()) // Calls filter allergens function when text is changed in search bar
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks
    }

    // Save Selected Allergens to SharedPreferences
    private fun saveSelectedAllergens() {
        val sharedPreferences = requireActivity().getSharedPreferences("AllergenPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val selectedAllergens = allergens
            .flatMap { it.items }
            .filter { it.isSelected }
            .map { it.name }

        editor.putStringSet("selectedAllergens", selectedAllergens.toSet())
        editor.apply()

        // Show a confirmation message
        Toast.makeText(requireContext(), "Allergen selection saved!", Toast.LENGTH_SHORT).show()
    }

    // Load Previously Saved Allergens
    private fun loadSelectedAllergens() {
        val sharedPreferences = requireActivity().getSharedPreferences("AllergenPrefs", Context.MODE_PRIVATE)
        val savedAllergens = sharedPreferences.getStringSet("selectedAllergens", emptySet()) ?: emptySet()

        allergens.forEach { category ->
            category.items.forEach { subItem ->
                subItem.isSelected = savedAllergens.contains(subItem.name)
            }
            category.isSelected = category.items.all { it.isSelected } // If all subitems are selected, check category
        }
    }

    private fun filterAllergens(query: String) {
        val filteredAllergens = allergens.map { category ->
            val filteredItems = category.items.filter { it.name.contains(query, ignoreCase = true) }
                .toMutableList()
            AllergenCategory(category.name, filteredItems)
        }.filter { it.items.isNotEmpty() }

        adapter.updateData(filteredAllergens)
        // Expands view of allergen list when searching, so user can see results
        for (i in 0 until adapter.groupCount) {
            binding.allergenExpandableList.expandGroup(i)
        }
    }
}