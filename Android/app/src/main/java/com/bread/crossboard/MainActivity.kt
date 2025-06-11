package com.bread.crossboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bread.crossboard.databinding.ActivityMainBinding
import com.bread.crossboard.ui.MainViewModel
import com.bread.crossboard.ui.SettingsFragment
import com.bread.crossboard.ui.StatusFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    
    private val requestPermissions = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            viewModel.startService()
        } else {
            Toast.makeText(
                this,
                "Permissions are required for proper functionality",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        // Set up ViewPager and TabLayout
        val pagerAdapter = TabPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Status"
                1 -> "Settings"
                else -> ""
            }
        }.attach()
        
        // Handle tab selection
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.viewPager.currentItem = tab.position
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.updateServiceStatus()
        
        // Register mDNS service for discovery
        CrossBoardApplication.instance.networkManager.registerService()
    }
    
    fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationPermission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, notificationPermission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(notificationPermission)
            }
        }
        
        // Check for location permissions (required for WiFi scanning on Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
            val coarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION
            
            if (ContextCompat.checkSelfPermission(this, fineLocationPermission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(fineLocationPermission)
            }
            
            if (ContextCompat.checkSelfPermission(this, coarseLocationPermission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(coarseLocationPermission)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            // All permissions are granted, start the service
            viewModel.startService()
        }
    }
    
    // ViewPager adapter for tabs
    private inner class TabPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> StatusFragment()
                1 -> SettingsFragment()
                else -> throw IllegalArgumentException("Invalid position $position")
            }
        }
    }
}