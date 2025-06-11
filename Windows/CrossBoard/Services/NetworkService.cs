using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading.Tasks;
using CrossBoard.Models;
using System.Windows;
using System.Net.Http;
using CrossBoard.Services.Clipboard;

namespace CrossBoard.Services
{
    public class NetworkService : IDisposable
    {
        private TcpListener _listener;
        private readonly IClipboardService _clipboardService;
        private readonly SettingsService _settingsService;
        private bool _isRunning = false;
        public const int Port = 65432; // TCP port for direct communication
        
        // Event handlers
        public event EventHandler<DeviceInfo>? DeviceDiscovered;
        public event EventHandler<DeviceInfo>? DeviceLost;
        public event EventHandler<List<DeviceInfo>>? DevicesUpdated;
        public event EventHandler<ClipboardData>? ClipboardDataReceived;
        
        // Devices tracking
        private readonly List<DeviceInfo> _devices = new List<DeviceInfo>();
        private readonly object _devicesLock = new object();
        private bool _disposed = false;
        
        // HTTP client for legacy connections
        private readonly HttpClient _httpClient;
        
        public bool IsRunning => _isRunning;

        public NetworkService(SettingsService settingsService, IClipboardService clipboardService)
        {
            _settingsService = settingsService;
            _clipboardService = clipboardService;
            
            _httpClient = new HttpClient
            {
                Timeout = TimeSpan.FromSeconds(5)
            };
            
            Console.WriteLine("NetworkService initialized");
        }

        public void Start()
        {
            if (_isRunning)
                return;

            _isRunning = true;
            
            // Start the TCP server
            Task.Run(StartTcpServer);
            
            Console.WriteLine("Network service started");
        }

        public void Stop()
        {
            if (!_isRunning)
                return;

            _isRunning = false;
            
            // Stop the TCP server
            _listener?.Stop();
            
            Console.WriteLine("Network service stopped");
        }
        
        private async Task StartTcpServer()
        {
            try
            {
                _listener = new TcpListener(IPAddress.Any, Port);
                _listener.Start();
                
                Console.WriteLine($"TCP server started on port {Port}");
                
                while (_isRunning)
                {
                    try
                    {
                        // Wait for a client connection
                        TcpClient client = await _listener.AcceptTcpClientAsync();
                        
                        // Handle the client in a separate task
                        _ = HandleClientAsync(client);
                    }
                    catch (Exception ex)
                    {
                        if (_isRunning)
                        {
                            Console.WriteLine($"Error accepting client: {ex.Message}");
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"TCP server error: {ex.Message}");
            }
        }
        
        private async Task HandleClientAsync(TcpClient client)
        {
            var clientEndPoint = client.Client.RemoteEndPoint?.ToString() ?? "Unknown device";
            Console.WriteLine($"Received connection from {clientEndPoint}");
            
            try
            {
                using NetworkStream stream = client.GetStream();
                byte[] buffer = new byte[4096]; // Larger buffer for bigger clipboard content
                int bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length);
                
                if (bytesRead > 0)
                {
                    string receivedText = Encoding.UTF8.GetString(buffer, 0, bytesRead);
                    Console.WriteLine($"Received text: {receivedText}");
                    
                    // Create clipboard data
                    var clipboardData = new ClipboardData
                    {
                        Text = receivedText,
                        Type = ClipboardType.Text,
                        SourceDeviceId = "android_device",
                        SourceDeviceName = "Android Device",
                        Timestamp = DateTimeOffset.Now.ToUnixTimeMilliseconds()
                    };
                    
                    // Update the clipboard on the UI thread
                    Application.Current.Dispatcher.Invoke(() =>
                    {
                        _clipboardService.SetClipboardContent(clipboardData);
                        ClipboardDataReceived?.Invoke(this, clipboardData);
                    });
                    
                    // Add the device to our list if not already there
                    var deviceIp = ((IPEndPoint)client.Client.RemoteEndPoint).Address.ToString();
                    AddOrUpdateDevice(deviceIp);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error handling client: {ex.Message}");
            }
            finally
            {
                client.Close();
            }
        }
        
        private void AddOrUpdateDevice(string ipAddress)
        {
            lock (_devicesLock)
            {
                var deviceInfo = new DeviceInfo
                {
                    DeviceId = $"android_{ipAddress.Replace(".", "_")}",
                    DeviceName = $"Android at {ipAddress}",
                    IpAddress = ipAddress
                };
                
                var existingDevice = _devices.Find(d => d.IpAddress == ipAddress);
                if (existingDevice == null)
                {
                    _devices.Add(deviceInfo);
                    DeviceDiscovered?.Invoke(this, deviceInfo);
                    DevicesUpdated?.Invoke(this, _devices);
                    
                    Console.WriteLine($"Added new device: {deviceInfo.DeviceName}");
                }
            }
        }
        
        public async Task SendClipboardDataAsync(string ipAddress, ClipboardData data)
        {
            if (string.IsNullOrEmpty(ipAddress) || !_isRunning)
                return;
                
            try
            {
                using (var client = new TcpClient())
                {
                    // Connect with a timeout
                    var connectTask = client.ConnectAsync(ipAddress, Port);
                    if (await Task.WhenAny(connectTask, Task.Delay(3000)) != connectTask)
                    {
                        throw new TimeoutException("Connection timed out");
                    }
                    
                    // Send the clipboard text directly
                    using (NetworkStream stream = client.GetStream())
                    {
                        byte[] buffer = Encoding.UTF8.GetBytes(data.Text);
                        await stream.WriteAsync(buffer, 0, buffer.Length);
                        await stream.FlushAsync();
                    }
                    
                    Console.WriteLine($"Clipboard data sent to {ipAddress}");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error sending clipboard data to {ipAddress}: {ex.Message}");
            }
        }

        public async Task SendClipboardDataToAllDevices(ClipboardData data)
        {
            if (!_isRunning)
                return;
                
            lock (_devicesLock)
            {
                foreach (var device in _devices)
                {
                    if (!string.IsNullOrEmpty(device.IpAddress))
                    {
                        SendClipboardDataAsync(device.IpAddress, data);
                    }
                }
            }
        }
        
        public List<DeviceInfo> GetConnectedDevices()
        {
            lock (_devicesLock)
            {
                return new List<DeviceInfo>(_devices);
            }
        }
        
        // Scan network for devices
        public void ScanNetwork()
        {
            Console.WriteLine("Scanning network for devices...");
            // This is now a placeholder - we rely on devices connecting to us
        }

        public void Dispose()
        {
            Dispose(true);
            GC.SuppressFinalize(this);
        }
        
        protected virtual void Dispose(bool disposing)
        {
            if (!_disposed)
            {
                if (disposing)
                {
                    // Stop the service if it's running
                    if (_isRunning)
                    {
                        Stop();
                    }
                    
                    // Dispose managed resources
                    _httpClient?.Dispose();
                    _listener?.Stop();
                }
                
                _disposed = true;
            }
        }
        
        ~NetworkService()
        {
            Dispose(false);
        }
    }
}