package com.bread.crossboard.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

class PreferenceManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "crossboard_prefs", Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_AUTO_COPY = "auto_copy"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNCED = "last_synced"
        private const val KEY_SYNC_DIRECTION = "sync_direction"
    }
    
    // Auto-start on boot
    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()
    
    // Auto-copy received content
    var autoCopy: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COPY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_COPY, value).apply()
    
    // Use WiFi only
    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    
    // Device name
    var deviceName: String
        get() {
            val defaultName = "${Build.MANUFACTURER} ${Build.MODEL}"
            return prefs.getString(KEY_DEVICE_NAME, defaultName) ?: defaultName
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()
    
    // Unique device ID
    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }
    
    // Last synced timestamp
    var lastSynced: Long
        get() = prefs.getLong(KEY_LAST_SYNCED, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNCED, value).apply()
    
    var syncDirection: SyncDirection
        get() {
            val value = prefs.getString(KEY_SYNC_DIRECTION, SyncDirection.BIDIRECTIONAL.name) ?: SyncDirection.BIDIRECTIONAL.name
            return SyncDirection.valueOf(value)
        }
        set(value) = prefs.edit().putString(KEY_SYNC_DIRECTION, value.name).apply()
} 