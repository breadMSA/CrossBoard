package com.bread.crossboard.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.bread.crossboard.CrossBoardApplication
import com.bread.crossboard.model.ClipboardData
import com.bread.crossboard.model.ClipboardType

class ClipboardManager(private val context: Context) {
    private val TAG = "ClipboardManager"
    
    private val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    // Get the current clipboard content
    fun getCurrentClipboardContent(): ClipboardData? {
        try {
            if (!systemClipboard.hasPrimaryClip()) {
                return null
            }
            
            val clipData = systemClipboard.primaryClip ?: return null
            
            if (clipData.itemCount == 0) {
                return null
            }
            
            val item = clipData.getItemAt(0)
            
            // Handle text content
            val text = item.text?.toString()
            if (!text.isNullOrEmpty()) {
                return ClipboardData(
                    text = text,
                    type = ClipboardType.TEXT,
                    timestamp = System.currentTimeMillis()
                )
            }
            
            // Handle URI content
            val uri = item.uri
            if (uri != null) {
                return ClipboardData(
                    text = uri.toString(),
                    type = ClipboardType.FILE,
                    timestamp = System.currentTimeMillis()
                )
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting clipboard content", e)
            return null
        }
    }
    
    // Get the current clipboard data with device information
    fun getCurrentClipboardData(): ClipboardData? {
        val content = getCurrentClipboardContent() ?: return null
        
        // Add device information
        val deviceId = CrossBoardApplication.instance.preferenceManager.deviceId
        val deviceName = CrossBoardApplication.instance.preferenceManager.deviceName
        
        return content.copy(
            sourceDeviceId = deviceId,
            sourceDeviceName = deviceName
        )
    }
    
    // Set clipboard content
    fun setClipboardContent(clipboardData: ClipboardData) {
        try {
            val clipData = when (clipboardData.type) {
                ClipboardType.TEXT -> ClipData.newPlainText("CrossBoard Text", clipboardData.text)
                ClipboardType.IMAGE -> ClipData.newPlainText("CrossBoard Image", clipboardData.text)
                ClipboardType.FILE -> ClipData.newPlainText("CrossBoard File", clipboardData.text)
            }
            
            systemClipboard.setPrimaryClip(clipData)
            Log.d(TAG, "Clipboard content set: ${clipboardData.text.take(50)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting clipboard content", e)
        }
    }
} 