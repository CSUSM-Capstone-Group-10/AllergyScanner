package com.example.allergyscanner

import android.content.Context

object SelectionManager {
    //Load selected allergens from SharedPreferences
    fun loadSelections(context: Context): List<String> {
        val sharedPreferences = context.getSharedPreferences("AllergenPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getStringSet("selectedAllergens", emptySet())?.toList() ?: emptyList()
    }
}
