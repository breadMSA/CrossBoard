package com.bread.crossboard.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bread.crossboard.CrossBoardApplication
import com.bread.crossboard.MainActivity
import com.bread.crossboard.R
import com.bread.crossboard.model.ClipboardData
import com.bread.crossboard.model.ClipboardType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClipboardService : Service() {
    private val TAG = "ClipboardService"
    private lateinit var clipboardManager: ClipboardManager
    private var lastClipboardText: String? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val clipboardChecker = object : Runnable {
        override fun run() {
            checkClipboard()
            handler.postDelayed(this, 500) // Check more frequently - every 500ms
        }
    }
    
    companion object {
        private var isServiceRunning = false
        private const val NOTIFICATION_ID = 1001
        
        // Action constants for notification buttons
        const val ACTION_SCAN_DEVICES = "com.bread.crossboard.SCAN_DEVICES"
        const val ACTION_SYNC_CLIPBOARD = "com.bread.crossboard.SYNC_CLIPBOARD"
        
        fun isRunning(): Boolean {
            return isServiceRunning
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ClipboardService onCreate")
        
        // Initialize clipboard manager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        // Add a direct clipboard change listener
        clipboardManager.addPrimaryClipChangedListener {
            Log.d(TAG, "Clipboard changed via listener")
            processClipboardChange()
        }
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start clipboard monitoring with timer as backup
        handler.post(clipboardChecker)
        
        // Register for clipboard data from network
        val networkManager = CrossBoardApplication.instance.networkManager
        networkManager.clipboardReceiver.setClipboardListener { clipboardData ->
            Log.d(TAG, "Received clipboard from network: ${clipboardData.text}")
            
            // Update last clipboard text to avoid sending it back
            lastClipboardText = clipboardData.text
            
            // Set to clipboard
            CrossBoardApplication.instance.clipboardManager.setClipboardContent(clipboardData)
            
            // Show toast notification
            showToast("Received clipboard: ${clipboardData.text.take(20)}${if (clipboardData.text.length > 20) "..." else ""}")
        }
        
        isServiceRunning = true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ClipboardService onStartCommand")
        
        // Handle actions from notification buttons
        when (intent?.action) {
            ACTION_SCAN_DEVICES -> {
                Log.d(TAG, "Scanning for devices from notification action")
                showToast("Scanning for devices...")
                CrossBoardApplication.instance.networkManager.scanNetwork()
            }
            ACTION_SYNC_CLIPBOARD -> {
                Log.d(TAG, "Manual clipboard sync from notification action")
                val clipboardData = CrossBoardApplication.instance.clipboardManager.getCurrentClipboardContent()
                if (clipboardData != null) {
                    // Add source device info
                    clipboardData.sourceDeviceId = CrossBoardApplication.instance.preferenceManager.deviceId
                    clipboardData.sourceDeviceName = CrossBoardApplication.instance.preferenceManager.deviceName
                    
                    // Send to connected devices
                    coroutineScope.launch {
                        showToast("Manually syncing clipboard...")
                        CrossBoardApplication.instance.networkManager.broadcastClipboardData(clipboardData)
                    }
                } else {
                    showToast("No clipboard content to sync")
                }
            }
        }
        
        // Register the mDNS service for discovery
        CrossBoardApplication.instance.networkManager.registerService()
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.d(TAG, "ClipboardService onDestroy")
        handler.removeCallbacks(clipboardChecker)
        isServiceRunning = false
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun checkClipboard() {
        try {
            // Get current clipboard content
            val clipboardData = CrossBoardApplication.instance.clipboardManager.getCurrentClipboardContent()
            
            if (clipboardData != null && clipboardData.text != lastClipboardText) {
                Log.d(TAG, "Clipboard changed detected by timer: ${clipboardData.text}")
                processClipboardChange()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking clipboard", e)
        }
    }
    
    private fun processClipboardChange() {
        try {
            val clipboardData = CrossBoardApplication.instance.clipboardManager.getCurrentClipboardContent()
            
            if (clipboardData != null && clipboardData.text != lastClipboardText) {
                Log.d(TAG, "Processing clipboard change: ${clipboardData.text}")
                
                // Update last clipboard text
                lastClipboardText = clipboardData.text
                
                // Add source device info
                clipboardData.sourceDeviceId = CrossBoardApplication.instance.preferenceManager.deviceId
                clipboardData.sourceDeviceName = CrossBoardApplication.instance.preferenceManager.deviceName
                
                // Send to connected devices
                coroutineScope.launch {
                    Log.d(TAG, "Broadcasting clipboard to connected devices")
                    CrossBoardApplication.instance.networkManager.broadcastClipboardData(clipboardData)
                }
                
                // Show toast notification
                showToast("Sending clipboard: ${clipboardData.text.take(20)}${if (clipboardData.text.length > 20) "..." else ""}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing clipboard change", e)
        }
    }
    
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        // Create scan devices action
        val scanIntent = Intent(this, ClipboardService::class.java).apply {
            action = ACTION_SCAN_DEVICES
        }
        val scanPendingIntent = PendingIntent.getService(
            this,
            1,
            scanIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        // Create sync clipboard action
        val syncIntent = Intent(this, ClipboardService::class.java).apply {
            action = ACTION_SYNC_CLIPBOARD
        }
        val syncPendingIntent = PendingIntent.getService(
            this,
            2,
            syncIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        // Build the notification with actions
        return NotificationCompat.Builder(this, "clipboard_service_channel")
            .setContentTitle("CrossBoard")
            .setContentText("Clipboard service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_search, "Scan", scanPendingIntent)
            .addAction(android.R.drawable.ic_menu_send, "Sync", syncPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
} 