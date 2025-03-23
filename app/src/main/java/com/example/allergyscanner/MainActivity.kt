package com.example.allergyscanner

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.allergytest.R
import com.example.allergytest.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up ViewPager with Fragments
        viewPager = binding.viewPager
        viewPager.adapter = AllergySectionsPagerAdapter(this)

        // Set up TabLayout with ViewPager
        tabLayout = binding.tabLayout
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Allergy Selection"
                1 -> "Information"
                else -> null
            }
        }.attach()

        // Bottom Navigation Setup
        val bottomNavigationView: BottomNavigationView = binding.bottomNav
        bottomNavigationView.selectedItemId = R.id.navAllergens

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navAllergens -> {
                    // Already on Allergens screen
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
}