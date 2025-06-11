package com.bread.crossboard.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Helper class for Network Service Discovery (NSD) using mDNS
 * This allows devices to discover each other on the local network without manual IP configuration
 */
class NsdHelper(private val context: Context) {

    private val TAG = "NsdHelper"
    
    // Service type for CrossBoard clipboard sync
    private val SERVICE_TYPE = "_crossboard._tcp."
    
    // Service name for this device
    private val SERVICE_NAME = "CrossBoard-${android.os.Build.MODEL}"
    
    // Port for the service
    private val SERVICE_PORT = 8765
    
    // NsdManager instance
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    
    // Registration listener
    private var registrationListener: NsdManager.RegistrationListener? = null
    
    // Discovery listener
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    
    // Resolution listener
    private var resolveListener: NsdManager.ResolveListener? = null
    
    // Discovered services
    private val _discoveredServices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val discoveredServices: StateFlow<List<DeviceInfo>> = _discoveredServices
    
    // Callback for when a service is resolved
    private var onServiceResolved: ((DeviceInfo) -> Unit)? = null
    
    /**
     * Register this device as a service on the network
     */
    fun registerService() {
        // Create a service info
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = SERVICE_PORT
        }
        
        // Create a registration listener
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Service registration failed: $errorCode")
            }
            
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed: $errorCode")
            }
            
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${serviceInfo.serviceName}")
            }
            
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: ${serviceInfo.serviceName}")
            }
        }
        
        // Register the service
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            Log.d(TAG, "Registering service: $SERVICE_NAME")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering service", e)
        }
    }
    
    /**
     * Discover services on the network
     */
    fun discoverServices() {
        // Create a discovery listener
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
            }
            
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
            
            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "Discovery started: $serviceType")
            }
            
            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "Discovery stopped: $serviceType")
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // Don't log Android device discoveries to reduce noise
                if (!isAndroidDevice(serviceInfo.serviceName)) {
                    Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                }
                
                // Skip our own service immediately
                if (isOwnDevice(serviceInfo.serviceName)) {
                    Log.d(TAG, "Skipping own service at discovery level: ${serviceInfo.serviceName}")
                    return
                }
                
                // Skip all Android devices
                if (isAndroidDevice(serviceInfo.serviceName)) {
                    Log.d(TAG, "Skipping Android device: ${serviceInfo.serviceName}")
                    return
                }
                
                // Only resolve if it's a CrossBoard service
                if (serviceInfo.serviceType == SERVICE_TYPE) {
                    resolveService(serviceInfo)
                }
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                // Don't log Android device losses to reduce noise
                if (!isAndroidDevice(serviceInfo.serviceName)) {
                    Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                }
                
                // Remove from discovered services
                val currentServices = _discoveredServices.value.toMutableList()
                val serviceToRemove = currentServices.find { it.deviceName == serviceInfo.serviceName }
                if (serviceToRemove != null) {
                    currentServices.remove(serviceToRemove)
                    _discoveredServices.value = currentServices
                }
            }
        }
        
        // Start discovery
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            Log.d(TAG, "Starting service discovery for: $SERVICE_TYPE")
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering services", e)
        }
    }
    
    /**
     * Check if a service name belongs to this device
     */
    private fun isOwnDevice(serviceName: String): Boolean {
        return serviceName.contains(android.os.Build.MODEL) || 
               serviceName == SERVICE_NAME ||
               serviceName.contains("Android") ||
               serviceName.contains("Phone") ||
               serviceName.contains("Pixel") ||
               serviceName.contains("Samsung") ||
               serviceName.contains("Xiaomi") ||
               serviceName.contains("Redmi") ||
               serviceName.contains("OPPO") ||
               serviceName.contains("Vivo") ||
               serviceName.contains("OnePlus") ||
               serviceName.contains("Huawei") ||
               serviceName.contains("Honor") ||
               serviceName.contains("Realme") ||
               serviceName.contains("Poco")
    }
    
    /**
     * Check if a service name belongs to an Android device
     */
    private fun isAndroidDevice(serviceName: String): Boolean {
        return serviceName.contains("Android") ||
               serviceName.contains("Phone") ||
               serviceName.contains("Pixel") ||
               serviceName.contains("Samsung") ||
               serviceName.contains("Xiaomi") ||
               serviceName.contains("Redmi") ||
               serviceName.contains("OPPO") ||
               serviceName.contains("Vivo") ||
               serviceName.contains("OnePlus") ||
               serviceName.contains("Huawei") ||
               serviceName.contains("Honor") ||
               serviceName.contains("Realme") ||
               serviceName.contains("Poco")
    }
    
    /**
     * Check if a service name belongs to a Windows/PC device
     */
    private fun isWindowsDevice(serviceName: String): Boolean {
        return serviceName.contains("PC") ||
               serviceName.contains("Windows") ||
               serviceName.contains("Desktop") ||
               serviceName.contains("Laptop") ||
               serviceName.contains("CrossBoard-PC")
    }
    
    /**
     * Resolve a service to get its host and port
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        // Create a resolve listener
        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
                
                // Try again after a delay if it's a busy error
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        resolveService(serviceInfo)
                    }, 1000)
                }
            }
            
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                // Don't log Android device resolutions to reduce noise
                if (!isAndroidDevice(serviceInfo.serviceName)) {
                    Log.d(TAG, "Service resolved: ${serviceInfo.serviceName}")
                    Log.d(TAG, "Host: ${serviceInfo.host.hostAddress}, Port: ${serviceInfo.port}")
                }
                
                // Skip if this is our own service
                if (isOwnDevice(serviceInfo.serviceName)) {
                    Log.d(TAG, "Skipping own service at resolve level: ${serviceInfo.serviceName}")
                    return
                }
                
                // Skip all Android devices
                if (isAndroidDevice(serviceInfo.serviceName)) {
                    Log.d(TAG, "Skipping Android device at resolve level: ${serviceInfo.serviceName}")
                    return
                }
                
                // Only include Windows/PC devices
                if (!isWindowsDevice(serviceInfo.serviceName)) {
                    Log.d(TAG, "Skipping non-PC device: ${serviceInfo.serviceName}")
                    return
                }
                
                // Create device info
                val deviceInfo = DeviceInfo(
                    deviceId = serviceInfo.serviceName,
                    deviceName = serviceInfo.serviceName,
                    ipAddress = serviceInfo.host.hostAddress
                )
                
                // Add to discovered services if not already present
                val currentServices = _discoveredServices.value.toMutableList()
                if (!currentServices.any { it.ipAddress == deviceInfo.ipAddress }) {
                    currentServices.add(deviceInfo)
                    _discoveredServices.value = currentServices
                    
                    // Notify callback
                    onServiceResolved?.invoke(deviceInfo)
                    
                    // Show toast only for PC devices
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            context, 
                            "PC found: ${deviceInfo.deviceName}", 
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        
        // Resolve the service
        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving service", e)
        }
    }
    
    /**
     * Set callback for when a service is resolved
     */
    fun setOnServiceResolvedListener(listener: (DeviceInfo) -> Unit) {
        onServiceResolved = listener
    }
    
    /**
     * Stop discovery and unregister service
     */
    fun tearDown() {
        try {
            discoveryListener?.let {
                nsdManager.stopServiceDiscovery(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }
        
        try {
            registrationListener?.let {
                nsdManager.unregisterService(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering service", e)
        }
    }
    
    /**
     * Device info class
     */
    data class DeviceInfo(
        val deviceId: String,
        val deviceName: String,
        val ipAddress: String
    )
} 