package com.pnu.orbit.ui.main

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.pnu.orbit.R
import com.pnu.orbit.ui.planner.TravelPlannerFragment
import com.pnu.orbit.ui.record.TravelRecordFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Keep the nav above the system gesture/3-button bar: the bar's own bottom padding absorbs
        // the inset so its background extends behind the system bar while its items sit above it.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            bottomNavigation.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigationRecord -> {
                    showFragment(TravelRecordFragment())
                    true
                }
                R.id.navigationPlanner -> {
                    showFragment(TravelPlannerFragment())
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.navigationRecord
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
