using CrossBoard.Services;
using CrossBoard.Services.Clipboard;
using Microsoft.Extensions.DependencyInjection;
using System;
using System.Windows;

namespace CrossBoard
{
    public partial class App : Application
    {
        public static IClipboardService ClipboardService { get; private set; }
        public static NetworkService NetworkService { get; private set; }
        public static SettingsService SettingsService { get; private set; }

        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);
            
            Console.WriteLine("Application starting...");
            
            try
            {
                // Initialize services
                Console.WriteLine("Initializing services...");
                SettingsService = new SettingsService();
                Console.WriteLine("Settings service initialized");
                
                ClipboardService = new WindowsClipboardService();
                Console.WriteLine("Clipboard service initialized");
                
                NetworkService = new NetworkService(SettingsService, ClipboardService);
                Console.WriteLine("Network service initialized");
                
                // Start network service
                Console.WriteLine("Starting network service...");
                NetworkService.Start();
                Console.WriteLine("Network service started successfully");
                
                // Test the clipboard service
                var clipboardContent = ClipboardService.GetClipboardContent();
                if (clipboardContent != null)
                {
                    Console.WriteLine($"Current clipboard content: {clipboardContent.Text.Substring(0, Math.Min(50, clipboardContent.Text.Length))}...");
                }
                else
                {
                    Console.WriteLine("No clipboard content available");
                }
                
                // Create and show main window
                Console.WriteLine("Creating main window...");
                var mainWindow = new MainWindow(ClipboardService, NetworkService, SettingsService);
                mainWindow.Show();
                Console.WriteLine("Main window displayed");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error during startup: {ex.Message}");
                Console.WriteLine($"Stack trace: {ex.StackTrace}");
                MessageBox.Show($"Error starting application: {ex.Message}", "Error", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        protected override void OnExit(ExitEventArgs e)
        {
            try
            {
                Console.WriteLine("Application shutting down...");
                
                // Stop services
                if (NetworkService != null)
                {
                    Console.WriteLine("Stopping network service...");
                    NetworkService.Stop();
                    Console.WriteLine("Network service stopped");
                }
                
                // Dispose of any resources
                (ClipboardService as IDisposable)?.Dispose();
                (NetworkService as IDisposable)?.Dispose();
                (SettingsService as IDisposable)?.Dispose();
                
                Console.WriteLine("All services stopped and resources disposed");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error during shutdown: {ex.Message}");
            }
            
            base.OnExit(e);
        }
    }
} 