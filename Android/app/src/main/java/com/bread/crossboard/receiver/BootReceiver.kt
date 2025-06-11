package com.bread.crossboard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.bread.crossboard.CrossBoardApplication
import com.bread.crossboard.service.ClipboardService

/**
 * Receiver that starts the ClipboardService when the device boots up
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking if service should be started")
            
            // Check if auto-start is enabled
            val preferenceManager = CrossBoardApplication.instance.preferenceManager
            if (preferenceManager.autoStart) {
                Log.d(TAG, "Auto-start is enabled, starting service")
                
                // Start the clipboard service
                val serviceIntent = Intent(context, ClipboardService::class.java)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
} 