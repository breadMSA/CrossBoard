package com.bread.crossboard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.bread.crossboard.data.PreferenceManager
import com.bread.crossboard.network.NetworkManager
import com.bread.crossboard.clipboard.ClipboardManager
import com.google.gson.Gson

class CrossBoardApplication : Application() {

    companion object {
        lateinit var instance: CrossBoardApplication
            private set
    }

    lateinit var preferenceManager: PreferenceManager
        private set
    
    lateinit var networkManager: NetworkManager
        private set
        
    lateinit var clipboardManager: ClipboardManager
        private set
        
    val gson = Gson()

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferenceManager = PreferenceManager(this)
        networkManager = NetworkManager(this)
        clipboardManager = ClipboardManager(this)
        
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create service notification channel
            val serviceChannel = NotificationChannel(
                "clipboard_service_channel",
                getString(R.string.clipboard_service_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.clipboard_service_notification_channel_description)
                setShowBadge(false)
            }

            // Create clipboard sync notification channel
            val syncChannel = NotificationChannel(
                "clipboard_sync_channel",
                getString(R.string.clipboard_sync_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.clipboard_sync_notification_channel_description)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannels(listOf(serviceChannel, syncChannel))
        }
    }
} 