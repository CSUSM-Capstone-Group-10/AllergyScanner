package com.example.allergyscanner

data class AllergenCategory(
    val name: String,
    val items: MutableList<AllergenItem>,
    var isSelected: Boolean = false
)

data class AllergenItem(
    val name: String,
    var isSelected: Boolean = false
)
