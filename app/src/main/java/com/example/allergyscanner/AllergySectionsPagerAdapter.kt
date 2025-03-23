package com.example.allergyscanner

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * A FragmentStateAdapter that manages the fragments for the top navigation tabs.
 * This adapter handles the "Allergy Selection" and "General Information" tabs.
 */
class AllergySectionsPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    // We have two tabs: Allergy Selection and General Information
    override fun getItemCount(): Int = 2

    // Create and return the appropriate fragment based on position
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AllergySelectionFragment()
            1 -> GeneralInformationFragment()
            else -> AllergySelectionFragment() // Default case, should never happen with just 2 tabs
        }
    }
}