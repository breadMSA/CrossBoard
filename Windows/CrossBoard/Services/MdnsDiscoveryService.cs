using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Threading.Tasks;
using CrossBoard.Models;
using Makaretu.Dns;
using System.Threading;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace CrossBoard.Services
{
    public class MdnsDiscoveryService
    {
        private const string ServiceType = "_crossboard._tcp";
        private const int ServicePort = 8765;
        
        private MulticastService? _mdns;
        private ServiceDiscovery? _discovery;
        private ServiceProfile? _profile;
        private bool _isRunning = false;
        
        public event EventHandler<DeviceInfo>? DeviceDiscovered;
        public event EventHandler<DeviceInfo>? DeviceLost;
        
        public MdnsDiscoveryService()
        {
            // Create the multicast service
            _mdns = new MulticastService();
            
            // Create the service discovery
            _discovery = new ServiceDiscovery(_mdns);
            
            // Handle service discovery events
            _discovery.ServiceInstanceDiscovered += OnServiceDiscovered;
            _discovery.ServiceInstanceShutdown += OnServiceLost;
        }
        
        public void Start()
        {
            if (_isRunning)
                return;
                
            try
            {
                // Start the multicast service
                _mdns?.Start();
                
                // Create and advertise our service
                AdvertiseService();
                
                // Start discovering services
                _discovery?.QueryServiceInstances(ServiceType);
                
                _isRunning = true;
                
                Console.WriteLine("mDNS discovery service started");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error starting mDNS discovery service: {ex.Message}");
            }
        }
        
        public void Stop()
        {
            if (!_isRunning)
                return;
                
            try
            {
                // Stop advertising our service
                if (_profile != null && _discovery != null)
                {
                    _discovery.Unadvertise(_profile);
                    _profile = null;
                }
                
                // Stop the multicast service
                _mdns?.Stop();
                
                _isRunning = false;
                
                Console.WriteLine("mDNS discovery service stopped");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error stopping mDNS discovery service: {ex.Message}");
            }
        }
        
        private void AdvertiseService()
        {
            try
            {
                if (_discovery == null)
                    return;
                    
                // Create a service profile for our service
                _profile = new ServiceProfile(
                    Environment.MachineName,
                    ServiceType,
                    ServicePort
                );
                
                // Add some metadata
                _profile.AddProperty("deviceType", "windows");
                _profile.AddProperty("deviceName", $"PC-{Environment.MachineName}");
                
                // Advertise the service
                _discovery.Advertise(_profile);
                
                Console.WriteLine($"Advertising service: {_profile.InstanceName}.{_profile.ServiceName}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error advertising service: {ex.Message}");
            }
        }
        
        private void OnServiceDiscovered(object? sender, ServiceInstanceDiscoveryEventArgs e)
        {
            try
            {
                // Skip our own service
                if (e.ServiceInstanceName.ToString().StartsWith(Environment.MachineName))
                    return;
                    
                // Get the IP address and port
                var addresses = e.Message.AdditionalRecords
                    .OfType<ARecord>()
                    .Select(a => a.Address.ToString())
                    .ToList();
                    
                if (addresses.Count == 0)
                    return;
                    
                // Get the port from the SRV record
                var srvRecords = e.Message.AdditionalRecords.OfType<SRVRecord>().ToList();
                int port = srvRecords.Count > 0 ? srvRecords[0].Port : ServicePort;
                
                // Get device type and name from TXT records
                var txtRecords = e.Message.AdditionalRecords.OfType<TXTRecord>().ToList();
                string deviceType = "unknown";
                string deviceName = e.ServiceInstanceName.ToString();
                
                if (txtRecords.Count > 0)
                {
                    foreach (var txt in txtRecords[0].Strings)
                    {
                        if (txt.StartsWith("deviceType="))
                            deviceType = txt.Substring("deviceType=".Length);
                        else if (txt.StartsWith("deviceName="))
                            deviceName = txt.Substring("deviceName=".Length);
                    }
                }
                
                // Create device info
                var deviceInfo = new DeviceInfo
                {
                    DeviceId = e.ServiceInstanceName.ToString(),
                    DeviceName = deviceName,
                    IpAddress = addresses[0]
                };
                
                // Notify listeners
                DeviceDiscovered?.Invoke(this, deviceInfo);
                
                Console.WriteLine($"Discovered device: {deviceInfo.DeviceName} at {deviceInfo.IpAddress}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error processing discovered service: {ex.Message}");
            }
        }
        
        private void OnServiceLost(object? sender, ServiceInstanceShutdownEventArgs e)
        {
            try
            {
                // Skip our own service
                if (e.ServiceInstanceName.ToString().StartsWith(Environment.MachineName))
                    return;
                    
                // Create device info
                var deviceInfo = new DeviceInfo
                {
                    DeviceId = e.ServiceInstanceName.ToString(),
                    DeviceName = e.ServiceInstanceName.ToString(),
                    IpAddress = ""
                };
                
                // Notify listeners
                DeviceLost?.Invoke(this, deviceInfo);
                
                Console.WriteLine($"Lost device: {deviceInfo.DeviceName}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error processing lost service: {ex.Message}");
            }
        }
    }
} 