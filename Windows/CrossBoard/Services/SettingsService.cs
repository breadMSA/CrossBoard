using System;
using System.IO;
using System.Text.Json;
using Microsoft.Win32;

namespace CrossBoard.Services
{
    public class SettingsService
    {
        private const string RegistryKey = @"SOFTWARE\CrossBoard";
        private const string SettingsFileName = "settings.json";
        
        private Settings _settings;
        
        public string DeviceId
        {
            get => _settings.DeviceId;
            set
            {
                _settings.DeviceId = value;
                SaveSettings();
            }
        }
        
        public string DeviceName
        {
            get => _settings.DeviceName;
            set
            {
                _settings.DeviceName = value;
                SaveSettings();
            }
        }
        
        public bool AutoCopy
        {
            get => _settings.AutoCopy;
            set
            {
                _settings.AutoCopy = value;
                SaveSettings();
            }
        }
        
        public bool WifiOnly
        {
            get => _settings.WifiOnly;
            set
            {
                _settings.WifiOnly = value;
                SaveSettings();
            }
        }
        
        public bool AutoStart
        {
            get => _settings.AutoStart;
            set
            {
                _settings.AutoStart = value;
                SaveSettings();
                SetStartupRegistry(value);
            }
        }
        
        public DateTime LastSynced
        {
            get => _settings.LastSynced;
            set
            {
                _settings.LastSynced = value;
                SaveSettings();
            }
        }
        
        public SyncDirection SyncDirection
        {
            get => _settings.SyncDirection;
            set
            {
                _settings.SyncDirection = value;
                SaveSettings();
            }
        }
        
        public SettingsService()
        {
            // Load or create settings
            _settings = LoadSettings() ?? CreateDefaultSettings();
            
            // Check if auto-start is enabled in registry
            _settings.AutoStart = IsStartupEnabled();
        }
        
        private Settings LoadSettings()
        {
            try
            {
                string settingsPath = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "CrossBoard",
                    SettingsFileName
                );
                
                if (File.Exists(settingsPath))
                {
                    string json = File.ReadAllText(settingsPath);
                    return JsonSerializer.Deserialize<Settings>(json);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error loading settings: {ex.Message}");
            }
            
            return null;
        }
        
        private Settings CreateDefaultSettings()
        {
            return new Settings
            {
                DeviceId = Guid.NewGuid().ToString(),
                DeviceName = Environment.MachineName,
                AutoCopy = true,
                WifiOnly = true,
                AutoStart = false,
                LastSynced = DateTime.MinValue,
                SyncDirection = SyncDirection.Bidirectional
            };
        }
        
        private void SaveSettings()
        {
            try
            {
                string settingsDirectory = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "CrossBoard"
                );
                
                // Create directory if it doesn't exist
                if (!Directory.Exists(settingsDirectory))
                {
                    Directory.CreateDirectory(settingsDirectory);
                }
                
                string settingsPath = Path.Combine(settingsDirectory, SettingsFileName);
                string json = JsonSerializer.Serialize(_settings, new JsonSerializerOptions { WriteIndented = true });
                File.WriteAllText(settingsPath, json);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error saving settings: {ex.Message}");
            }
        }
        
        private bool IsStartupEnabled()
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\Run", false))
                {
                    return key?.GetValue("CrossBoard") != null;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error checking startup registry: {ex.Message}");
                return false;
            }
        }
        
        private void SetStartupRegistry(bool enabled)
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\Run", true))
                {
                    if (enabled)
                    {
                        string executablePath = System.Reflection.Assembly.GetExecutingAssembly().Location;
                        key?.SetValue("CrossBoard", $"\"{executablePath}\"");
                    }
                    else
                    {
                        key?.DeleteValue("CrossBoard", false);
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error setting startup registry: {ex.Message}");
            }
        }
    }
    
    public enum SyncDirection
    {
        Bidirectional,
        ReceiveOnly,
        SendOnly
    }
    
    public class Settings
    {
        public string DeviceId { get; set; }
        public string DeviceName { get; set; }
        public bool AutoCopy { get; set; }
        public bool WifiOnly { get; set; }
        public bool AutoStart { get; set; }
        public DateTime LastSynced { get; set; }
        public SyncDirection SyncDirection { get; set; }
    }
} 