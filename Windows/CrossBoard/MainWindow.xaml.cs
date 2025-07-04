using CrossBoard.Models;
using CrossBoard.Services;
using CrossBoard.Services.Clipboard;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Shapes;
using System.Drawing;
using System.Windows.Forms;
using Timer = System.Timers.Timer;

namespace CrossBoard
{
    public partial class MainWindow : Window
    {
        private readonly IClipboardService _clipboardService;
        private readonly NetworkService _networkService;
        private readonly SettingsService _settingsService;
        private readonly Timer _statusUpdateTimer;
        
        // System tray icon
        private NotifyIcon _notifyIcon;
        
        public MainWindow(IClipboardService clipboardService, NetworkService networkService, SettingsService settingsService)
        {
            InitializeComponent();
            
            _clipboardService = clipboardService;
            _networkService = networkService;
            _settingsService = settingsService;
            
            // Display local IP address
            DisplayLocalIpAddress();
            
            // Subscribe to clipboard changes
            _clipboardService.ClipboardChanged += OnClipboardChanged;
            
            // Subscribe to network events
            _networkService.DeviceDiscovered += OnDeviceDiscovered;
            _networkService.ClipboardDataReceived += OnClipboardDataReceived;
            
            // Start a timer to update status
            _statusUpdateTimer = new Timer(1000);
            _statusUpdateTimer.Elapsed += (s, e) => UpdateStatus();
            _statusUpdateTimer.Start();
            
            // Update status initially
            UpdateStatus();
            
            // Initialize system tray icon
            InitializeNotifyIcon();
            
            // Handle window closing event
            this.Closing += MainWindow_Closing;
            
            // Load settings
            LoadSettings();
        }
        
        private void InitializeNotifyIcon()
        {
            _notifyIcon = new NotifyIcon
            {
                Icon = System.Drawing.Icon.ExtractAssociatedIcon(System.Reflection.Assembly.GetEntryAssembly().Location),
                Text = "CrossBoard - Clipboard Sync",
                Visible = true
            };
            
            // Create context menu
            var contextMenu = new ContextMenuStrip();
            
            // Show window menu item
            var showMenuItem = new ToolStripMenuItem("Show Window");
            showMenuItem.Click += (s, e) => 
            {
                this.Show();
                this.WindowState = WindowState.Normal;
                this.Activate();
            };
            contextMenu.Items.Add(showMenuItem);
            
            // Separator
            contextMenu.Items.Add(new ToolStripSeparator());
            
            // Exit menu item
            var exitMenuItem = new ToolStripMenuItem("Exit");
            exitMenuItem.Click += (s, e) => 
            {
                _notifyIcon.Visible = false;
                System.Windows.Application.Current.Shutdown();
            };
            contextMenu.Items.Add(exitMenuItem);
            
            _notifyIcon.ContextMenuStrip = contextMenu;
            
            // Double-click to show window
            _notifyIcon.DoubleClick += (s, e) => 
            {
                this.Show();
                this.WindowState = WindowState.Normal;
                this.Activate();
            };
        }
        
        private void MainWindow_Closing(object sender, System.ComponentModel.CancelEventArgs e)
        {
            // Cancel the close and minimize to tray instead
            e.Cancel = true;
            this.Hide();
            
            // Show notification
            _notifyIcon.ShowBalloonTip(
                3000, 
                "CrossBoard", 
                "CrossBoard is still running in the background. Click the tray icon to open the window again.", 
                ToolTipIcon.Info
            );
        }
        
        private void DisplayLocalIpAddress()
        {
            try
            {
                var ipAddresses = GetLocalIpAddresses();
                if (ipAddresses.Count > 0)
                {
                    // Display IP addresses in the UI
                    var ipText = "Your IP Addresses:\n" + string.Join("\n", ipAddresses);
                    IpAddressText.Text = ipText;
                    
                    // Also set the first IP to the manual connection textbox
                    ManualIpTextBox.Text = ipAddresses.FirstOrDefault() ?? "";
                }
            }
            catch (Exception ex)
            {
                StatusText.Text = $"Error getting IP address: {ex.Message}";
            }
        }
        
        private List<string> GetLocalIpAddresses()
        {
            var result = new List<string>();
            
            try
            {
                // Get all network interfaces
                NetworkInterface[] networkInterfaces = NetworkInterface.GetAllNetworkInterfaces();
                
                foreach (NetworkInterface networkInterface in networkInterfaces)
                {
                    // Only get IP addresses from active network interfaces
                    if (networkInterface.OperationalStatus == OperationalStatus.Up)
                    {
                        // Skip loopback, tunnel and virtual adapters
                        if (networkInterface.NetworkInterfaceType == NetworkInterfaceType.Loopback ||
                            networkInterface.Description.ToLower().Contains("virtual") ||
                            networkInterface.Description.ToLower().Contains("pseudo"))
                        {
                            continue;
                        }
                        
                        // Get IP properties
                        IPInterfaceProperties ipProperties = networkInterface.GetIPProperties();
                        
                        // Get IPv4 addresses
                        foreach (UnicastIPAddressInformation ipInfo in ipProperties.UnicastAddresses)
                        {
                            if (ipInfo.Address.AddressFamily == AddressFamily.InterNetwork)
                            {
                                result.Add(ipInfo.Address.ToString());
                            }
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error getting IP addresses: {ex.Message}");
            }
            
            return result;
        }
        
        private void UpdateStatus()
        {
            Dispatcher.Invoke(() =>
            {
                // Update service status
                ServiceStatusText.Text = _networkService.IsRunning ? "Running" : "Stopped";
                
                // Update status text
                StatusText.Text = $"Ready - {DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss")}";
            });
        }
        
        private void ScanNetworkButton_Click(object sender, RoutedEventArgs e)
        {
            _networkService.ScanNetwork();
            StatusText.Text = "Scanning network...";
        }
        
        private void ConnectButton_Click(object sender, RoutedEventArgs e)
        {
            string ipAddress = ManualIpTextBox.Text.Trim();
            if (string.IsNullOrEmpty(ipAddress))
            {
                System.Windows.MessageBox.Show("Please enter a valid IP address", "Error", MessageBoxButton.OK, MessageBoxImage.Error);
                return;
            }
            
            // Create a device info
            var deviceInfo = new DeviceInfo
            {
                DeviceId = $"android-{ipAddress}",
                DeviceName = $"Android at {ipAddress}",
                IpAddress = ipAddress
            };
            
            // Add to devices list
            if (!DevicesList.Items.Contains(deviceInfo.DeviceName))
            {
                DevicesList.Items.Add(deviceInfo.DeviceName);
            }
            
            // Send test clipboard data
            var clipboardData = new ClipboardData
            {
                Text = $"Test from Windows: {DateTime.Now}",
                Type = ClipboardType.Text,
                SourceDeviceId = Environment.MachineName,
                SourceDeviceName = $"PC-{Environment.MachineName}",
                Timestamp = DateTimeOffset.Now.ToUnixTimeMilliseconds()
            };
            
            _networkService.SendClipboardDataAsync(ipAddress, clipboardData);
            StatusText.Text = $"Connecting to {ipAddress}...";
        }
        
        private void OnClipboardChanged(object sender, EventArgs e)
        {
            // Get the current clipboard content
            var clipboardData = _clipboardService.GetClipboardContent();
            
            if (clipboardData != null)
            {
                // Update UI
                Dispatcher.Invoke(() =>
                {
                    LastClipboardText.Text = clipboardData.Text;
                    LastClipboardTime.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                });
                
                // Check sync direction before sending
                if (_settingsService.SyncDirection != SyncDirection.ReceiveOnly)
                {
                    // Send to connected devices
                    _networkService.SendClipboardDataToAllDevices(clipboardData);
                }
            }
        }
        
        private void OnDeviceDiscovered(object sender, DeviceInfo deviceInfo)
        {
            // Add device to UI
            Dispatcher.Invoke(() =>
            {
                if (!DevicesList.Items.Contains(deviceInfo.DeviceName))
                {
                    DevicesList.Items.Add(deviceInfo.DeviceName);
                }
            });
        }
        
        private void OnClipboardDataReceived(object sender, ClipboardData clipboardData)
        {
            // Update UI
            Dispatcher.Invoke(() =>
            {
                ReceivedClipboardText.Text = clipboardData.Text;
                ReceivedClipboardTime.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                ReceivedClipboardDevice.Text = clipboardData.SourceDeviceName;
            });
            
            // Check sync direction before setting to clipboard
            if (_settingsService.SyncDirection != SyncDirection.SendOnly)
            {
                // Set to clipboard
                _clipboardService.SetClipboardContent(clipboardData);
                
                // Show notification if window is minimized
                if (this.WindowState == WindowState.Minimized || !this.IsVisible)
                {
                    _notifyIcon?.ShowBalloonTip(
                        3000,
                        "New Clipboard Content",
                        $"Received from: {clipboardData.SourceDeviceName}",
                        ToolTipIcon.Info
                    );
                }
            }
        }
        
        private void MinimizeToTrayButton_Click(object sender, RoutedEventArgs e)
        {
            // Hide the window
            this.Hide();
            
            // Show notification
            _notifyIcon.ShowBalloonTip(
                3000, 
                "CrossBoard", 
                "CrossBoard is still running in the background. Click the tray icon to open the window again.", 
                ToolTipIcon.Info
            );
        }
        
        private void ExitButton_Click(object sender, RoutedEventArgs e)
        {
            // Ask for confirmation
            var result = System.Windows.MessageBox.Show(
                "Are you sure you want to exit CrossBoard? This will stop the clipboard synchronization.",
                "Confirm Exit",
                MessageBoxButton.YesNo,
                MessageBoxImage.Question
            );
            
            if (result == MessageBoxResult.Yes)
            {
                // Clean up notify icon
                if (_notifyIcon != null)
                {
                    _notifyIcon.Visible = false;
                    _notifyIcon.Dispose();
                }
                
                // Exit application
                System.Windows.Application.Current.Shutdown();
            }
        }
        
        private void SaveDeviceNameButton_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                string deviceName = DeviceNameTextBox.Text.Trim();
                if (!string.IsNullOrEmpty(deviceName))
                {
                    _settingsService.DeviceName = deviceName;
                    StatusText.Text = $"Device name saved: {deviceName}";
                }
                else
                {
                    StatusText.Text = "Device name cannot be empty";
                }
            }
            catch (Exception ex)
            {
                StatusText.Text = $"Error saving device name: {ex.Message}";
            }
        }
        
        private void LoadSettings()
        {
            try
            {
                // Load device name
                DeviceNameTextBox.Text = _settingsService.DeviceName;
                
                // Load sync direction
                SyncDirectionComboBox.SelectedIndex = (int)_settingsService.SyncDirection;
                
                // Load auto start setting
                AutoStartCheckBox.IsChecked = _settingsService.AutoStart;
                
                // Load wifi only setting
                WifiOnlyCheckBox.IsChecked = _settingsService.WifiOnly;
            }
            catch (Exception ex)
            {
                StatusText.Text = $"Error loading settings: {ex.Message}";
            }
        }
        
        private void SyncDirectionComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (SyncDirectionComboBox.SelectedItem is ComboBoxItem selectedItem)
            {
                string tag = selectedItem.Tag.ToString();
                if (Enum.TryParse<SyncDirection>(tag, out var direction))
                {
                    _settingsService.SyncDirection = direction;
                    StatusText.Text = $"Sync direction set to: {direction}";
                }
            }
        }
        
        private void AutoStartCheckBox_Checked(object sender, RoutedEventArgs e)
        {
            _settingsService.AutoStart = true;
            StatusText.Text = "Auto start enabled";
        }
        
        private void AutoStartCheckBox_Unchecked(object sender, RoutedEventArgs e)
        {
            _settingsService.AutoStart = false;
            StatusText.Text = "Auto start disabled";
        }
        
        private void WifiOnlyCheckBox_Checked(object sender, RoutedEventArgs e)
        {
            _settingsService.WifiOnly = true;
            StatusText.Text = "Wi-Fi only mode enabled";
        }
        
        private void WifiOnlyCheckBox_Unchecked(object sender, RoutedEventArgs e)
        {
            _settingsService.WifiOnly = false;
            StatusText.Text = "Wi-Fi only mode disabled";
        }
        
        private void ConfigureFirewallButton_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                // Get the path to the setup_permissions.bat file
                string batchFilePath = System.IO.Path.Combine(
                    System.IO.Path.GetDirectoryName(System.Reflection.Assembly.GetExecutingAssembly().Location),
                    "setup_permissions.bat"
                );
                
                // Check if the file exists
                if (!System.IO.File.Exists(batchFilePath))
                {
                    System.Windows.MessageBox.Show(
                        "setup_permissions.bat file not found. Please make sure it exists in the application directory.",
                        "Error",
                        MessageBoxButton.OK,
                        MessageBoxImage.Error
                    );
                    return;
                }
                
                // Create process start info
                var startInfo = new System.Diagnostics.ProcessStartInfo
                {
                    FileName = "cmd.exe",
                    Arguments = $"/c start \"Firewall Configuration\" \"{batchFilePath}\"",
                    UseShellExecute = true,
                    Verb = "runas" // Request admin privileges
                };
                
                // Start the process
                System.Diagnostics.Process.Start(startInfo);
                
                StatusText.Text = "Firewall configuration script started";
            }
            catch (Exception ex)
            {
                System.Windows.MessageBox.Show(
                    $"Error running firewall configuration script: {ex.Message}",
                    "Error",
                    MessageBoxButton.OK,
                    MessageBoxImage.Error
                );
                StatusText.Text = "Error running firewall configuration";
            }
        }
    }
} 