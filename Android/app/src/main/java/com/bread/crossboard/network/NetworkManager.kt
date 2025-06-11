package com.bread.crossboard.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.bread.crossboard.CrossBoardApplication
import com.bread.crossboard.model.ClipboardData
import com.bread.crossboard.model.ClipboardType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.Date
import android.content.ClipData
import com.google.gson.Gson
import java.net.NetworkInterface
import java.net.Inet4Address
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.content.Intent
import com.bread.crossboard.service.ClipboardService
import java.io.OutputStream

class NetworkManager(private val context: Context) {
    
    private val TAG = "NetworkManager"
    private val PORT = 8765  // Make sure this matches the Windows port (8765)
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus
    
    private val _connectedDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val connectedDevices: StateFlow<List<DeviceInfo>> = _connectedDevices
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()
    
    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // NSD Helper for mDNS service discovery
    private val nsdHelper = NsdHelper(context)
    
    // Multicast lock to ensure mDNS works properly
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val multicastLock = wifiManager.createMulticastLock("CrossBoardMulticastLock")
    
    private var _isServiceRunning = false
    private var _multicastLock: WifiManager.MulticastLock? = null
    
    init {
        // Initialize multicast lock
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        _multicastLock = wifiManager.createMulticastLock("CrossBoardMulticastLock")
        _multicastLock?.setReferenceCounted(true)
        
        // Listen for discovered services
        coroutineScope.launch {
            nsdHelper.discoveredServices.collect { devices ->
                if (devices.isNotEmpty()) {
                    // Convert NsdHelper.DeviceInfo to NetworkManager.DeviceInfo
                    val convertedDevices = devices.map { nsdDevice ->
                        DeviceInfo(
                            deviceId = nsdDevice.deviceId,
                            deviceName = nsdDevice.deviceName,
                            ipAddress = nsdDevice.ipAddress
                        )
                    }
                    
                    // Update connected devices
                    _connectedDevices.value = convertedDevices
                    
                    // Update connection status
                    if (convertedDevices.isNotEmpty()) {
                        forceConnectionStatus(ConnectionStatus.CONNECTED)
                    }
                }
            }
        }
        
        // Set callback for resolved services
        nsdHelper.setOnServiceResolvedListener { deviceInfo ->
            showToast("NSD resolved device: ${deviceInfo.deviceName} at ${deviceInfo.ipAddress}")
            
            // Update connection status
            forceConnectionStatus(ConnectionStatus.CONNECTED)
        }
    }
    
    // Start the network service
    fun startService() {
        if (_isServiceRunning) return
        
        _isServiceRunning = true
        
        // Acquire multicast lock to ensure mDNS works
        _multicastLock?.acquire()
        
        coroutineScope.launch {
            // Start with disconnected status and update when we find devices
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            Log.d(TAG, "Network service started, status: ${_connectionStatus.value}")
            
            // Start the server
            startServer()
            
            // Register service with NSD
            nsdHelper.registerService()
            
            // Start discovering services
            nsdHelper.discoverServices()
            
            // Log local IP address
            val ipAddress = getLocalIpAddress()
            Log.d(TAG, "Local IP address: $ipAddress, Listening on port: $PORT")
            showToast("Local IP: $ipAddress:$PORT")
            
            // Scan for devices immediately
            scanNetwork()
        }
    }
    
    // Force update the connection status
    fun updateConnectionStatus(status: ConnectionStatus) {
        Log.d(TAG, "Updating connection status to: $status")
        _connectionStatus.value = status
    }
    
    // Force the connection status to CONNECTED and show toast
    fun forceConnectionStatus(status: ConnectionStatus) {
        Log.d(TAG, "FORCING connection status to: $status")
        _connectionStatus.value = status
        showToast("Connection status: $status")
    }
    
    // Check if network is available
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        val hasNetwork = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        
        Log.d(TAG, "Network available: $hasNetwork")
        return hasNetwork
    }
    
    // Stop the network service
    fun stopService() {
        if (!_isServiceRunning) return
        
        _isServiceRunning = false
        
        // Release multicast lock
        if (_multicastLock?.isHeld == true) {
            _multicastLock?.release()
        }
        
        isServerRunning = false
        serverSocket?.close()
        serverSocket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _connectedDevices.value = emptyList()
        
        // Stop NSD
        nsdHelper.tearDown()
        
        Log.d(TAG, "Network service stopped")
    }
    
    // Start the server to listen for incoming connections
    private fun startServer() {
        if (isServerRunning) {
            Log.d(TAG, "Server already running")
            return
        }
        
        try {
            // Use 0.0.0.0 to listen on all network interfaces
            serverSocket = ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))
            isServerRunning = true
            Log.d(TAG, "Server started on port $PORT listening on all interfaces")
            showToast("Server started on port $PORT")
            
            // Start the device timeout checker
            startDeviceTimeoutChecker()
            
            coroutineScope.launch {
                while (isServerRunning) {
                    try {
                        Log.d(TAG, "Waiting for connection...")
                        val clientSocket = serverSocket?.accept() ?: continue
                        Log.d(TAG, "Received connection from ${clientSocket.inetAddress.hostAddress}")
                        showToast("Connection from: ${clientSocket.inetAddress.hostAddress}")
                        
                        // Update connection status when we receive a connection
                        forceConnectionStatus(ConnectionStatus.CONNECTED)
                        
                        // Process the connection in a new coroutine
                        coroutineScope.launch {
                            processClientConnection(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (isServerRunning) {
                            Log.e(TAG, "Error accepting client connection", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting server", e)
            showToast("Error starting server: ${e.message}")
        }
    }
    
    // Scan the network for devices running CrossBoard
    fun scanNetwork() {
        showToast("Scanning network using mDNS...")
        Log.d(TAG, "Starting network scan using mDNS...")
        
        // Use NSD for discovery
        nsdHelper.discoverServices()
        
        // Also try to ping known devices
        coroutineScope.launch {
            val devices = _connectedDevices.value
            if (devices.isNotEmpty()) {
                Log.d(TAG, "Pinging ${devices.size} known devices")
                var anyConnected = false
                
                for (device in devices) {
                    try {
                        val result = testConnection(device.ipAddress)
                        if (result != null) {
                            anyConnected = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pinging device ${device.deviceName}: ${e.message}")
                    }
                }
                
                // Update connection status based on ping results
                if (anyConnected) {
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    showToast("Connected to at least one device")
                } else if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
                    // Only update if we're currently showing connected
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    showToast("No devices responding")
                }
            }
        }
    }
    
    // Get the local IP address
    fun getLocalIpAddress(): String? {
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                
                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // Skip IPv6 addresses and loopback
                    if (address.isLoopbackAddress || address !is Inet4Address) {
                        continue
                    }
                    
                    return address.hostAddress
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
        }
        
        return null
    }
    
    // Get all local IP addresses
    fun getAllLocalIpAddresses(): List<String> {
        val ipAddresses = mutableListOf<String>()
        
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                
                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // Skip IPv6 addresses and loopback
                    if (address.isLoopbackAddress || address !is Inet4Address) {
                        continue
                    }
                    
                    ipAddresses.add(address.hostAddress)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP addresses", e)
        }
        
        return ipAddresses
    }
    
    // Test connection to a specific IP address
    suspend fun testConnection(ipAddress: String): DeviceInfo? {
        return try {
            Log.d(TAG, "Testing connection to $ipAddress:$PORT")
            
            val request = Request.Builder()
                .url("http://$ipAddress:$PORT/ping")
                .build()
            
            // Use a client with shorter timeout for testing
            val testClient = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .build()
                
            val response = withContext(Dispatchers.IO) {
                testClient.newCall(request).execute()
            }
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    Log.d(TAG, "Received ping response from $ipAddress: $responseBody")
                    val jsonObject = JSONObject(responseBody)
                    val deviceId = jsonObject.getString("deviceId")
                    val deviceName = jsonObject.getString("deviceName")
                    
                    // Update connection status when we successfully ping a device
                    forceConnectionStatus(ConnectionStatus.CONNECTED)
                    
                    // Add to connected devices if not already present
                    val deviceInfo = DeviceInfo(deviceId, deviceName, ipAddress)
                    val currentDevices = _connectedDevices.value.toMutableList()
                    if (!currentDevices.any { it.deviceId == deviceId }) {
                        currentDevices.add(deviceInfo)
                        _connectedDevices.value = currentDevices
                        showToast("Found PC: ${deviceInfo.deviceName}")
                    }
                    
                    return deviceInfo
                }
            } else {
                Log.d(TAG, "Test connection to $ipAddress failed with status code: ${response.code}")
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "Error testing connection to $ipAddress: ${e.message}")
            null
        }
    }
    
    // Send clipboard data to a specific device
    fun sendClipboardData(clipboardData: ClipboardData, deviceInfo: DeviceInfo) {
        coroutineScope.launch {
            try {
                Log.d(TAG, "Sending clipboard data to ${deviceInfo.deviceName} (${deviceInfo.ipAddress})")
                Log.d(TAG, "Clipboard content: '${clipboardData.text.take(50)}${if (clipboardData.text.length > 50) "..." else ""}'")
                showToast("Sending clipboard to: ${deviceInfo.deviceName}")
                
                // Create a proper JSON object with all required fields
                val jsonObject = JSONObject().apply {
                    put("text", clipboardData.text)
                    put("type", clipboardData.type.name)
                    put("sourceDeviceId", clipboardData.sourceDeviceId)
                    put("sourceDeviceName", clipboardData.sourceDeviceName)
                    put("timestamp", clipboardData.timestamp)
                }
                
                Log.d(TAG, "JSON payload: $jsonObject")
                
                val requestBody = jsonObject.toString().toRequestBody(JSON_MEDIA_TYPE)
                
                // Create a client with longer timeout
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build()
                
                // Double check the port number
                val correctPort = 8765  // Make sure this is correct
                
                // First ping the device to check connectivity
                try {
                    val pingRequest = Request.Builder()
                        .url("http://${deviceInfo.ipAddress}:$correctPort/ping")
                        .build()
                        
                    Log.d(TAG, "Sending ping to http://${deviceInfo.ipAddress}:$correctPort/ping")
                    
                    withContext(Dispatchers.IO) {
                        client.newCall(pingRequest).execute().use { pingResponse ->
                            if (pingResponse.isSuccessful) {
                                val responseBody = pingResponse.body?.string()
                                Log.d(TAG, "Ping successful! Response: $responseBody")
                                // Update connection status on successful ping
                                _connectionStatus.value = ConnectionStatus.CONNECTED
                            } else {
                                Log.w(TAG, "Ping failed with code: ${pingResponse.code}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Ping failed: ${e.message}")
                }
                
                // Now send the actual clipboard data
                val request = Request.Builder()
                    .url("http://${deviceInfo.ipAddress}:$correctPort/clipboard")
                    .post(requestBody)
                    .build()
                
                Log.d(TAG, "Sending request to http://${deviceInfo.ipAddress}:$correctPort/clipboard")
                
                // Update last synced time immediately
                CrossBoardApplication.instance.preferenceManager.lastSynced = System.currentTimeMillis()
                    
                withContext(Dispatchers.IO) {
                    try {
                        client.newCall(request).execute().use { response ->
                            val responseCode = response.code
                            val responseBody = response.body?.string() ?: "No response body"
                            
                            if (response.isSuccessful) {
                                Log.d(TAG, "Successfully sent clipboard data to ${deviceInfo.deviceName}. Response: $responseBody")
                                showToast("Clipboard sent successfully")
                                // Update connection status on successful send
                                _connectionStatus.value = ConnectionStatus.CONNECTED
                                
                                // Make sure this device is in our list
                                val currentDevices = _connectedDevices.value.toMutableList()
                                val existingDeviceIndex = currentDevices.indexOfFirst { it.ipAddress == deviceInfo.ipAddress }
                                if (existingDeviceIndex >= 0) {
                                    // Update existing device
                                    currentDevices[existingDeviceIndex] = deviceInfo
                                } else {
                                    // Add new device
                                    currentDevices.add(deviceInfo)
                                }
                                _connectedDevices.value = currentDevices
                            } else {
                                Log.e(TAG, "Failed to send clipboard data: $responseCode. Response: $responseBody")
                                showToast("Failed to send clipboard: $responseCode")
                                
                                // Check if we should update connection status
                                checkAndUpdateConnectionStatus()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending clipboard data to ${deviceInfo.deviceName}: ${e.message}")
                        showToast("Error: ${e.message}")
                        
                        // Check if we should update connection status
                        checkAndUpdateConnectionStatus()
                        
                        // Even if sending fails, update last synced time
                        CrossBoardApplication.instance.preferenceManager.lastSynced = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing clipboard data", e)
                showToast("Error: ${e.message}")
                
                // Check if we should update connection status
                checkAndUpdateConnectionStatus()
                
                // Even if sending fails, update last synced time
                CrossBoardApplication.instance.preferenceManager.lastSynced = System.currentTimeMillis()
            }
        }
    }
    
    // Helper function to check and update connection status
    private fun checkAndUpdateConnectionStatus() {
        coroutineScope.launch {
            // Ping all known devices to see if any are still reachable
            val devices = _connectedDevices.value
            if (devices.isNotEmpty()) {
                var anyConnected = false
                
                for (device in devices) {
                    try {
                        val pingRequest = Request.Builder()
                            .url("http://${device.ipAddress}:8765/ping")
                            .build()
                            
                        val client = OkHttpClient.Builder()
                            .connectTimeout(2, TimeUnit.SECONDS)
                            .build()
                            
                        withContext(Dispatchers.IO) {
                            try {
                                client.newCall(pingRequest).execute().use { response ->
                                    if (response.isSuccessful) {
                                        anyConnected = true
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore connection errors
                            }
                        }
                        
                        if (anyConnected) {
                            break // No need to check more devices
                        }
                    } catch (e: Exception) {
                        // Ignore errors
                    }
                }
                
                // Update connection status based on ping results
                if (anyConnected) {
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                } else {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }
            } else {
                // No devices in list, definitely disconnected
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
    }
    
    // Broadcast clipboard data to all connected devices
    fun broadcastClipboardData(clipboardData: ClipboardData) {
        val ownIpAddresses = getAllLocalIpAddresses()
        val devices = _connectedDevices.value.filter { !ownIpAddresses.contains(it.ipAddress) }
        
        if (devices.isNotEmpty()) {
            Log.d(TAG, "Broadcasting clipboard data to ${devices.size} devices")
            showToast("Sending to ${devices.size} devices")
            devices.forEach { deviceInfo ->
                sendClipboardData(clipboardData, deviceInfo)
            }
        } else {
            Log.d(TAG, "No devices to broadcast clipboard data to")
            showToast("No devices to send to")
            // Force a network scan to find devices
            scanNetwork()
        }
    }
    
    // Parse clipboard data from JSON string
    private fun parseClipboardData(jsonString: String): ClipboardData? {
        return try {
            Log.d(TAG, "Parsing clipboard data: $jsonString")
            
            // Clean up the JSON string - sometimes there might be extra data at the end
            val cleanJson = if (jsonString.trim().endsWith("}")) {
                jsonString.trim()
            } else {
                val endIndex = jsonString.lastIndexOf("}") + 1
                if (endIndex > 0) {
                    jsonString.substring(0, endIndex)
                } else {
                    jsonString
                }
            }
            
            val jsonObject = JSONObject(cleanJson)
            
            // Get required text field
            val text = jsonObject.getString("Text") ?: jsonObject.getString("text")
            
            // Get optional fields with fallbacks
            val typeStr = jsonObject.optString("Type", jsonObject.optString("type", "TEXT"))
            val type = try {
                // Handle different case formats (TEXT vs Text)
                when (typeStr.uppercase()) {
                    "TEXT" -> ClipboardType.TEXT
                    "IMAGE" -> ClipboardType.IMAGE
                    "FILE" -> ClipboardType.FILE
                    else -> ClipboardType.TEXT
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unknown clipboard type: $typeStr, defaulting to TEXT")
                ClipboardType.TEXT
            }
            
            val timestamp = jsonObject.optLong("Timestamp", jsonObject.optLong("timestamp", System.currentTimeMillis()))
            val sourceDeviceId = jsonObject.optString("SourceDeviceId", jsonObject.optString("sourceDeviceId", ""))
            val sourceDeviceName = jsonObject.optString("SourceDeviceName", jsonObject.optString("sourceDeviceName", ""))
            
            Log.d(TAG, "Successfully parsed clipboard data: text=$text, type=$type, source=$sourceDeviceName")
            
            ClipboardData(
                text = text,
                type = type,
                timestamp = timestamp,
                sourceDeviceId = sourceDeviceId,
                sourceDeviceName = sourceDeviceName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing clipboard data", e)
            null
        }
    }
    
    // Clipboard receiver interface
    class ClipboardReceiverHelper {
        private var onClipboardReceivedListener: ((ClipboardData) -> Unit)? = null
        
        fun setClipboardListener(listener: (ClipboardData) -> Unit) {
            onClipboardReceivedListener = listener
        }
        
        fun onClipboardReceived(clipboardData: ClipboardData) {
            onClipboardReceivedListener?.invoke(clipboardData)
        }
    }
    
    val clipboardReceiver = ClipboardReceiverHelper()
    
    // Callback for when clipboard data is received
    private fun onClipboardReceived(clipboardData: ClipboardData) {
        // Update last synced time immediately
        CrossBoardApplication.instance.preferenceManager.lastSynced = System.currentTimeMillis()
        
        // Set the clipboard content directly
        CrossBoardApplication.instance.clipboardManager.setClipboardContent(clipboardData)
        
        // Show toast notification
        showToast("Received clipboard from ${clipboardData.sourceDeviceName}: ${clipboardData.text.take(20)}${if (clipboardData.text.length > 20) "..." else ""}")
        
        // Also notify any listeners
        clipboardReceiver.onClipboardReceived(clipboardData)
    }
    
    // Helper function to show toast messages
    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    // Test a direct connection to a specific IP address with the given port
    fun testDirectConnection(ipAddress: String, port: Int) {
        coroutineScope.launch {
            try {
                // Check if this is the device's own IP address
                val ownIpAddresses = getAllLocalIpAddresses()
                if (ownIpAddresses.contains(ipAddress)) {
                    Log.e(TAG, "Cannot connect to own IP address: $ipAddress")
                    showToast("Error: Cannot connect to your own device's IP address ($ipAddress)")
                    return@launch
                }
                
                val request = Request.Builder()
                    .url("http://$ipAddress:$port/ping")
                    .build()
                
                Log.d(TAG, "Testing connection to http://$ipAddress:$port/ping")
                showToast("Testing connection to $ipAddress:$port")
                
                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }
                
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully connected to $ipAddress:$port")
                    showToast("Successfully connected to $ipAddress:$port")
                    
                    // Add this device to our list if not already there
                    val deviceInfo = DeviceInfo(
                        deviceId = "manual_${ipAddress.replace(".", "_")}",
                        deviceName = "PC at $ipAddress",
                        ipAddress = ipAddress
                    )
                    
                    // Update connection status
                    forceConnectionStatus(ConnectionStatus.CONNECTED)
                    
                    // Add to connected devices if not already there
                    val currentDevices = _connectedDevices.value.toMutableList()
                    if (currentDevices.none { it.ipAddress == ipAddress }) {
                        currentDevices.add(deviceInfo)
                        _connectedDevices.value = currentDevices
                    }
                } else {
                    Log.e(TAG, "Failed to connect to $ipAddress:$port, status: ${response.code}")
                    showToast("Failed to connect to $ipAddress:$port, status: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to $ipAddress:$port", e)
                showToast("Error connecting to $ipAddress:$port: ${e.message}")
            }
        }
    }
    
    // Send the current clipboard content to a specific device
    fun sendClipboardToDevice(ipAddress: String) {
        coroutineScope.launch {
            try {
                // Check if this is the device's own IP address
                val ownIpAddresses = getAllLocalIpAddresses()
                if (ownIpAddresses.contains(ipAddress)) {
                    Log.e(TAG, "Cannot send clipboard to own IP address: $ipAddress")
                    showToast("Error: Cannot send clipboard to your own device ($ipAddress)")
                    return@launch
                }
                
                val clipboardManager = CrossBoardApplication.instance.clipboardManager
                val clipboardData = clipboardManager.getCurrentClipboardData()
                if (clipboardData != null) {
                    // Create a device info object for the target
                    val deviceInfo = DeviceInfo(
                        deviceId = "manual_${ipAddress.replace(".", "_")}",
                        deviceName = "PC at $ipAddress",
                        ipAddress = ipAddress
                    )
                    sendClipboardData(clipboardData, deviceInfo)
                } else {
                    showToast("No clipboard data to send")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending clipboard to $ipAddress", e)
                showToast("Error sending clipboard: ${e.message}")
            }
        }
    }
    
    // Register service with mDNS
    fun registerService() {
        nsdHelper.registerService()
    }
    
    enum class ConnectionStatus {
        CONNECTED,
        DISCONNECTED
    }
    
    data class DeviceInfo(
        val deviceId: String,
        val deviceName: String,
        val ipAddress: String,
        var lastSeen: Long = System.currentTimeMillis()
    )
    
    // Check if any devices are still active (seen in the last 30 seconds)
    private fun hasActiveDevices(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeoutThreshold = 30000 // 30 seconds
        
        val activeDevices = _connectedDevices.value.filter { device ->
            (currentTime - device.lastSeen) < timeoutThreshold
        }
        
        return activeDevices.isNotEmpty()
    }
    
    // Update connection status based on active devices
    fun updateConnectionStatusFromDevices() {
        if (hasActiveDevices()) {
            _connectionStatus.value = ConnectionStatus.CONNECTED
        } else {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }
    
    // Periodic check for device timeouts
    private fun startDeviceTimeoutChecker() {
        coroutineScope.launch {
            while (isServerRunning) {
                delay(10000) // Check every 10 seconds
                updateConnectionStatusFromDevices()
                
                // Remove devices that haven't been seen for more than 2 minutes
                val currentTime = System.currentTimeMillis()
                val timeoutThreshold = 120000 // 2 minutes
                
                val updatedDevices = _connectedDevices.value.filter { device ->
                    (currentTime - device.lastSeen) < timeoutThreshold
                }
                
                if (updatedDevices.size != _connectedDevices.value.size) {
                    _connectedDevices.value = updatedDevices
                }
            }
        }
    }
    
    // Process a client connection
    private suspend fun processClientConnection(clientSocket: Socket) {
        try {
            val inputStream = clientSocket.getInputStream()
            val buffer = ByteArray(8192) // Use larger buffer for bigger clipboard content
            val bytesRead = inputStream.read(buffer)
            
            if (bytesRead > 0) {
                val request = String(buffer, 0, bytesRead)
                Log.d(TAG, "Received request: ${request.split("\n")[0]}")
                
                // Check if it's a POST /clipboard request
                if (request.startsWith("POST /clipboard")) {
                    val bodyStart = request.indexOf("\r\n\r\n") + 4
                    if (bodyStart > 0 && bytesRead > bodyStart) {
                        val jsonString = request.substring(bodyStart)
                        Log.d(TAG, "Received clipboard data: $jsonString")
                        
                        val clipboardData = parseClipboardData(jsonString)
                        if (clipboardData != null) {
                            onClipboardReceived(clipboardData)
                            
                            // Send HTTP 200 OK response
                            val response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nOK"
                            clientSocket.getOutputStream().write(response.toByteArray())
                            Log.d(TAG, "Sent 200 OK response")
                            
                            // Update device info in connected devices list
                            val deviceIp = clientSocket.inetAddress.hostAddress
                            updateDeviceLastSeen(deviceIp)
                        } else {
                            // Send error response if we couldn't parse the clipboard data
                            val errorResponse = "HTTP/1.1 400 Bad Request\r\nContent-Type: text/plain\r\nContent-Length: 22\r\n\r\nInvalid clipboard data"
                            clientSocket.getOutputStream().write(errorResponse.toByteArray())
                            Log.e(TAG, "Failed to parse clipboard data")
                        }
                    } else {
                        // Send error response if we couldn't find the body
                        val errorResponse = "HTTP/1.1 400 Bad Request\r\nContent-Type: text/plain\r\nContent-Length: 13\r\n\r\nNo body found"
                        clientSocket.getOutputStream().write(errorResponse.toByteArray())
                        Log.e(TAG, "No body found in clipboard request")
                    }
                }
                // Check if it's a GET /ping request
                else if (request.startsWith("GET /ping")) {
                    val deviceInfo = JSONObject().apply {
                        put("deviceId", CrossBoardApplication.instance.preferenceManager.deviceId)
                        put("deviceName", CrossBoardApplication.instance.preferenceManager.deviceName)
                    }
                    
                    val responseStr = deviceInfo.toString()
                    val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${responseStr.length}\r\n\r\n$responseStr"
                    clientSocket.getOutputStream().write(response.toByteArray())
                    Log.d(TAG, "Responded to ping request with: $deviceInfo")
                    
                    // Update device info in connected devices list
                    val deviceIp = clientSocket.inetAddress.hostAddress
                    updateDeviceLastSeen(deviceIp)
                } else {
                    // Send 404 for unsupported requests
                    val errorResponse = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: 9\r\n\r\nNot Found"
                    clientSocket.getOutputStream().write(errorResponse.toByteArray())
                    Log.d(TAG, "Unsupported request: ${request.split("\n")[0]}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing client socket", e)
            }
        }
    }
    
    // Update the last seen timestamp for a device
    private fun updateDeviceLastSeen(ipAddress: String) {
        val currentDevices = _connectedDevices.value.toMutableList()
        val deviceIndex = currentDevices.indexOfFirst { it.ipAddress == ipAddress }
        
        if (deviceIndex >= 0) {
            // Update existing device
            val device = currentDevices[deviceIndex]
            device.lastSeen = System.currentTimeMillis()
            currentDevices[deviceIndex] = device
            _connectedDevices.value = currentDevices
        } else {
            // This is a new device, add it to the list
            val deviceInfo = DeviceInfo(
                deviceId = "device_${ipAddress.replace(".", "_")}",
                deviceName = "PC at $ipAddress",
                ipAddress = ipAddress,
                lastSeen = System.currentTimeMillis()
            )
            currentDevices.add(deviceInfo)
            _connectedDevices.value = currentDevices
            
            // Update connection status
            forceConnectionStatus(ConnectionStatus.CONNECTED)
        }
    }
    
    // Send clipboard data directly via TCP socket
    suspend fun sendClipboardToDeviceTcp(deviceIp: String, text: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            var socket: Socket? = null
            var outputStream: OutputStream? = null
            try {
                Log.d(TAG, "Connecting to $deviceIp:65432 via TCP")
                showToast("Connecting to $deviceIp via TCP...")
                
                // 1. Create socket and connect to server
                socket = Socket(deviceIp, 65432) // Port must match Windows server
                
                // 2. Get output stream and send UTF-8 encoded text
                outputStream = socket.getOutputStream()
                outputStream.write(text.toByteArray(Charsets.UTF_8))
                outputStream.flush() // Make sure all data is sent
                
                Log.d(TAG, "Successfully sent data via TCP")
                showToast("Clipboard sent via TCP")
                
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending data via TCP: ${e.message}", e)
                showToast("Error: ${e.message}")
                Result.failure(e)
            } finally {
                // 3. Close resources regardless of success or failure
                try {
                    outputStream?.close()
                    socket?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing TCP resources", e)
                }
            }
        }
    }
} 