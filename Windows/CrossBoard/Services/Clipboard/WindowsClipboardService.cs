using CrossBoard.Models;
using System;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows;
using System.Windows.Interop;

namespace CrossBoard.Services.Clipboard
{
    public class WindowsClipboardService : IClipboardService
    {
        private Timer _clipboardCheckTimer;
        private string _lastClipboardText = string.Empty;
        private bool _isSettingClipboard = false;
        private bool _disposed = false;
        
        public event EventHandler ClipboardChanged;
        
        // Windows messages for clipboard
        private const int WM_CLIPBOARDUPDATE = 0x031D;
        
        // Win32 API functions
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool AddClipboardFormatListener(IntPtr hwnd);
        
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool RemoveClipboardFormatListener(IntPtr hwnd);
        
        public WindowsClipboardService()
        {
            // Start a timer to check clipboard changes (more frequent checks)
            _clipboardCheckTimer = new Timer(CheckClipboardChanges, null, 0, 250);
            
            // We'll rely on the timer for clipboard monitoring
            // since the ContentChanged event is not available in all .NET versions
            Console.WriteLine("Using timer-based clipboard monitoring");
        }
        
        private void CheckClipboardChanges(object state)
        {
            if (_isSettingClipboard)
            {
                return; // Skip check if we're currently setting the clipboard
            }
            
            try
            {
                string currentText = string.Empty;
                
                // Need to access clipboard on UI thread
                Application.Current.Dispatcher.Invoke(() =>
                {
                    if (System.Windows.Clipboard.ContainsText())
                    {
                        currentText = System.Windows.Clipboard.GetText();
                    }
                });
                
                // Check if clipboard content has changed
                if (!string.IsNullOrEmpty(currentText) && currentText != _lastClipboardText)
                {
                    Console.WriteLine($"Clipboard change detected: '{currentText.Substring(0, Math.Min(50, currentText.Length))}...'");
                    _lastClipboardText = currentText;
                    
                    // Trigger the event to notify listeners (NetworkService will handle this)
                    ClipboardChanged?.Invoke(this, EventArgs.Empty);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error checking clipboard: {ex.Message}");
            }
        }
        
        public ClipboardData GetClipboardContent()
        {
            try
            {
                ClipboardData clipboardData = null;
                
                // Need to access clipboard on UI thread
                Application.Current.Dispatcher.Invoke(() =>
                {
                    if (System.Windows.Clipboard.ContainsText())
                    {
                        clipboardData = new ClipboardData
                        {
                            Text = System.Windows.Clipboard.GetText(),
                            Type = ClipboardType.Text,
                            SourceDeviceId = Environment.MachineName,
                            SourceDeviceName = $"PC-{Environment.MachineName}",
                            Timestamp = DateTimeOffset.Now.ToUnixTimeMilliseconds()
                        };
                        Console.WriteLine($"Got clipboard content: {clipboardData.Text.Substring(0, Math.Min(50, clipboardData.Text.Length))}...");
                    }
                });
                
                return clipboardData;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error getting clipboard content: {ex.Message}");
                return null;
            }
        }
        
        public void SetClipboardContent(ClipboardData clipboardData)
        {
            if (clipboardData == null || string.IsNullOrEmpty(clipboardData.Text))
                return;
                
            try
            {
                // Temporarily disable clipboard monitoring to avoid triggering our own event
                _isSettingClipboard = true;
                _lastClipboardText = clipboardData.Text;
                
                Console.WriteLine($"Setting clipboard content: {clipboardData.Text.Substring(0, Math.Min(50, clipboardData.Text.Length))}...");
                
                // Need to access clipboard on UI thread
                Application.Current.Dispatcher.Invoke(() =>
                {
                    System.Windows.Clipboard.SetText(clipboardData.Text);
                });
                
                Console.WriteLine("Clipboard content set successfully");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error setting clipboard content: {ex.Message}");
            }
            finally
            {
                // Re-enable clipboard monitoring after a short delay
                Thread.Sleep(100);
                _isSettingClipboard = false;
            }
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
                    // Dispose managed resources
                    _clipboardCheckTimer?.Dispose();
                }
                
                // Dispose unmanaged resources
                
                _disposed = true;
            }
        }
        
        ~WindowsClipboardService()
        {
            Dispose(false);
        }
    }
} 