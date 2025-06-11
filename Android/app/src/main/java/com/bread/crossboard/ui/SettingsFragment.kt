package com.bread.crossboard.ui

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.bread.crossboard.CrossBoardApplication
import com.bread.crossboard.R
import com.bread.crossboard.data.PreferenceManager
import com.bread.crossboard.data.SyncDirection

class SettingsFragment : PreferenceFragmentCompat() {
    
    private lateinit var preferenceManager: PreferenceManager
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        
        preferenceManager = CrossBoardApplication.instance.preferenceManager
        
        // Device name preference
        val deviceNamePref = findPreference<EditTextPreference>("device_name")
        deviceNamePref?.let {
            it.text = preferenceManager.deviceName
            it.setOnPreferenceChangeListener { _, newValue ->
                preferenceManager.deviceName = newValue.toString()
                true
            }
        }
        
        // Sync direction preference
        val syncDirectionPref = findPreference<ListPreference>("sync_direction")
        syncDirectionPref?.let {
            it.value = when (preferenceManager.syncDirection) {
                SyncDirection.BIDIRECTIONAL -> "bidirectional"
                SyncDirection.RECEIVE_ONLY -> "receive_only"
                SyncDirection.SEND_ONLY -> "send_only"
            }
            
            it.setOnPreferenceChangeListener { _, newValue ->
                val direction = when (newValue.toString()) {
                    "bidirectional" -> SyncDirection.BIDIRECTIONAL
                    "receive_only" -> SyncDirection.RECEIVE_ONLY
                    "send_only" -> SyncDirection.SEND_ONLY
                    else -> SyncDirection.BIDIRECTIONAL
                }
                preferenceManager.syncDirection = direction
                true
            }
        }
        
        // Auto copy preference
        val autoCopyPref = findPreference<SwitchPreference>("auto_copy")
        autoCopyPref?.let {
            it.isChecked = preferenceManager.autoCopy
            it.setOnPreferenceChangeListener { _, newValue ->
                preferenceManager.autoCopy = newValue as Boolean
                true
            }
        }
        
        // Wi-Fi only preference
        val wifiOnlyPref = findPreference<SwitchPreference>("wifi_only")
        wifiOnlyPref?.let {
            it.isChecked = preferenceManager.wifiOnly
            it.setOnPreferenceChangeListener { _, newValue ->
                preferenceManager.wifiOnly = newValue as Boolean
                true
            }
        }
        
        // Auto start preference
        val autoStartPref = findPreference<SwitchPreference>("auto_start")
        autoStartPref?.let {
            it.isChecked = preferenceManager.autoStart
            it.setOnPreferenceChangeListener { _, newValue ->
                preferenceManager.autoStart = newValue as Boolean
                true
            }
        }
        
        // Version preference
        val versionPref = findPreference<Preference>("version")
        versionPref?.let {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            it.summary = packageInfo.versionName
        }
        
        // GitHub preference
        val githubPref = findPreference<Preference>("github")
        githubPref?.let {
            it.setOnPreferenceClickListener {
                // Open GitHub URL in browser
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse("https://github.com/yourusername/crossboard")
                startActivity(intent)
                true
            }
        }
    }
} 