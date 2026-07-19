package com.nebula.browser

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nebula.browser.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val nav = findNavController(R.id.nav_host)
        binding.bottomNav.setupWithNavController(nav)
    }

    override fun onBackPressed() {
        val nav = findNavController(R.id.nav_host)
        if (!nav.popBackStack()) super.onBackPressed()
    }
}
