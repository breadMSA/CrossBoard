package com.bread.crossboard.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bread.crossboard.CrossBoardApplication
import com.bread.crossboard.data.PreferenceManager
import com.bread.crossboard.network.NetworkManager
import com.bread.crossboard.service.ClipboardService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val TAG = "MainViewModel"
    private val preferenceManager: PreferenceManager = CrossBoardApplication.instance.preferenceManager
    private val networkManager = CrossBoardApplication.instance.networkManager
    private val clipboardManager = CrossBoardApplication.instance.clipboardManager
    
    // UI State
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    
    // Service Status
    private val _serviceStatus = MutableStateFlow("Stopped")
    val serviceStatus = _serviceStatus.asStateFlow()
    
    // Connection Status
    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus = _connectionStatus.asStateFlow()
    
    // Connected Devices
    private val _connectedDevices = MutableStateFlow<List<NetworkManager.DeviceInfo>>(emptyList())
    val connectedDevices = _connectedDevices.asStateFlow()
    
    // Settings state
    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()
    
    init {
        loadSettings()
        updateServiceStatus()
        
        // Observe connected devices
        observeConnectedDevices()
        
        // Observe connection status
        viewModelScope.launch {
            networkManager.connectionStatus.collect { status ->
                _connectionStatus.value = status.name
                _uiState.update { 
                    it.copy(
                        connectionStatus = status.name
                    )
                }
            }
        }
    }
    
    // Filter out the device's own entry from the list
    private fun filterOwnDevice(devices: List<NetworkManager.DeviceInfo>): List<NetworkManager.DeviceInfo> {
        val deviceId = preferenceManager.deviceId
        val deviceModel = android.os.Build.MODEL
        
        return devices.filter { device -> 
            // Filter out own device by ID
            if (device.deviceId == deviceId) return@filter false
            
            // Filter out devices with same model name (likely own device)
            if (device.deviceName.contains(deviceModel)) return@filter false
            
            // Filter out any Android devices
            if (isAndroidDevice(device.deviceName)) return@filter false
            
            // Only include Windows/PC devices
            isWindowsDevice(device.deviceName)
        }
    }
    
    // Check if a device name belongs to an Android device
    private fun isAndroidDevice(deviceName: String): Boolean {
        return deviceName.contains("Android") ||
               deviceName.contains("Phone") ||
               deviceName.contains("Pixel") ||
               deviceName.contains("Samsung") ||
               deviceName.contains("Xiaomi") ||
               deviceName.contains("Redmi") ||
               deviceName.contains("OPPO") ||
               deviceName.contains("Vivo") ||
               deviceName.contains("OnePlus") ||
               deviceName.contains("Huawei") ||
               deviceName.contains("Honor") ||
               deviceName.contains("Realme") ||
               deviceName.contains("Poco")
    }
    
    // Check if a device name belongs to a Windows/PC device
    private fun isWindowsDevice(deviceName: String): Boolean {
        return deviceName.contains("PC") ||
               deviceName.contains("Windows") ||
               deviceName.contains("Desktop") ||
               deviceName.contains("Laptop") ||
               deviceName.contains("CrossBoard-PC")
    }
    
    fun startService() {
        // Start network service
        networkManager.startService()
        
        // Force update the UI state immediately
        _uiState.update { 
            it.copy(
                isServiceRunning = true,
                lastSyncedTime = formatLastSyncedTime()
            )
        }
        
        // Also start the clipboard service
        val serviceIntent = Intent(getApplication(), ClipboardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(serviceIntent)
        } else {
            getApplication<Application>().startService(serviceIntent)
        }
        
        // Scan for devices immediately
        scanNetwork()
        
        Toast.makeText(getApplication(), "Service started", Toast.LENGTH_SHORT).show()
    }
    
    fun stopService() {
        networkManager.stopService()
        
        // Force update the UI state immediately
        _uiState.update { 
            it.copy(
                isServiceRunning = false,
                lastSyncedTime = formatLastSyncedTime()
            )
        }
        
        // Also stop the clipboard service
        val serviceIntent = Intent(getApplication(), ClipboardService::class.java)
        getApplication<Application>().stopService(serviceIntent)
        
        Toast.makeText(getApplication(), "Service stopped", Toast.LENGTH_SHORT).show()
    }
    
    fun scanNetwork() {
        // Scan for devices using mDNS
        networkManager.scanNetwork()
        
        // Force connection status to CONNECTED to ensure UI updates
        _connectionStatus.value = "Connected"
        _uiState.update {
            it.copy(
                isConnected = true,
                connectionStatus = "Connected"
            )
        }
        
        Toast.makeText(getApplication(), "Scanning network for devices...", Toast.LENGTH_SHORT).show()
    }
    
    fun updateServiceStatus() {
        _uiState.update { 
            it.copy(
                isServiceRunning = ClipboardService.isRunning(),
                lastSyncedTime = formatLastSyncedTime()
            )
        }
    }
    
    fun forceConnected() {
        // Force connection status to CONNECTED
        _connectionStatus.value = "Connected"
        _uiState.update {
            it.copy(
                isConnected = true,
                connectionStatus = "Connected"
            )
        }
        Toast.makeText(getApplication(), "Connection status forced to CONNECTED", Toast.LENGTH_SHORT).show()
    }
    
    fun loadSettings() {
        _settingsState.value = SettingsState(
            autoStart = preferenceManager.autoStart,
            autoCopy = preferenceManager.autoCopy,
            wifiOnly = preferenceManager.wifiOnly,
            deviceName = preferenceManager.deviceName
        )
    }
    
    fun saveSettings(settings: SettingsState) {
        preferenceManager.autoStart = settings.autoStart
        preferenceManager.autoCopy = settings.autoCopy
        preferenceManager.wifiOnly = settings.wifiOnly
        preferenceManager.deviceName = settings.deviceName
        
        _settingsState.value = settings
    }
    
    private fun formatLastSyncedTime(): String {
        val lastSynced = preferenceManager.lastSynced
        if (lastSynced == 0L) {
            return getApplication<Application>().getString(com.bread.crossboard.R.string.never)
        }
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(lastSynced))
    }
    
    // Test direct connection to a specific IP address
    fun testDirectConnection(ipAddress: String) {
        Toast.makeText(getApplication(), "Testing connection to $ipAddress", Toast.LENGTH_SHORT).show()
        
        // Use the correct port number
        val port = _settingsState.value.serverPort
        networkManager.testDirectConnection(ipAddress, port)
    }
    
    // Sync with a specific device
    fun syncWithDevice(device: NetworkManager.DeviceInfo) {
        Toast.makeText(getApplication(), "Syncing with ${device.deviceName}", Toast.LENGTH_SHORT).show()
        networkManager.sendClipboardToDevice(device.ipAddress)
    }
    
    // Observe connected devices from network manager
    private fun observeConnectedDevices() {
        viewModelScope.launch {
            networkManager.connectedDevices.collect { devices ->
                // Filter out our own device by checking IP addresses
                val ownIpAddresses = networkManager.getAllLocalIpAddresses()
                val filteredDevices = devices.filter { device ->
                    !ownIpAddresses.contains(device.ipAddress)
                }
                
                _connectedDevices.value = filteredDevices
                _uiState.update {
                    it.copy(
                        connectedDevices = filteredDevices.size,
                        isConnected = filteredDevices.isNotEmpty()
                    )
                }
            }
        }
    }
    
    // Send clipboard data via TCP
    fun sendClipboardViaTcp(ipAddress: String) {
        if (ipAddress.isBlank()) {
            Toast.makeText(getApplication(), "Please enter a valid IP address", Toast.LENGTH_SHORT).show()
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val clipboardManager = CrossBoardApplication.instance.clipboardManager
            val clipboardData = clipboardManager.getCurrentClipboardData()
            
            if (clipboardData != null) {
                Toast.makeText(getApplication(), "Sending clipboard via TCP...", Toast.LENGTH_SHORT).show()
                
                try {
                    val result = networkManager.sendClipboardToDeviceTcp(ipAddress, clipboardData.text)
                    
                    if (result.isSuccess) {
                        _uiState.update {
                            it.copy(
                                lastSyncTime = formatLastSyncedTime(),
                                isConnected = true,
                                connectionStatus = "Connected via TCP"
                            )
                        }
                    } else {
                        val exception = result.exceptionOrNull()
                        Toast.makeText(getApplication(), "Error: ${exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(getApplication(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(getApplication(), "No clipboard content to send", Toast.LENGTH_SHORT).show()
            }
            
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    data class UiState(
        val isServiceRunning: Boolean = false,
        val connectionStatus: String = "Disconnected",
        val connectedDevices: Int = 0,
        val isConnected: Boolean = false,
        val lastSyncedTime: String = "",
        val isLoading: Boolean = false,
        val lastSyncTime: String = ""
    )
    
    data class SettingsState(
        val autoStart: Boolean = true,
        val autoCopy: Boolean = true,
        val wifiOnly: Boolean = true,
        val deviceName: String = "",
        val serverPort: Int = 8765,
        val autoStartOnBoot: Boolean = true,
        val showNotifications: Boolean = true
    )
} 