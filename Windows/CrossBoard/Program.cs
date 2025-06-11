using System;
using System.Diagnostics;
using System.Threading.Tasks;
using System.Windows;
using CrossBoard.Services;

namespace CrossBoard
{
    public class Program
    {
        [STAThread]
        public static void Main()
        {
            // Setup firewall rules
            SetupFirewallRules();
            
            // Create and run the application
            var app = new App();
            app.Run();
        }
        
        private static void SetupFirewallRules()
        {
            try
            {
                // Check if the TCP rule exists
                var tcpRuleExists = CheckFirewallRuleExists("CrossBoard TCP");
                if (!tcpRuleExists)
                {
                    // Create TCP rule
                    Console.WriteLine("Creating TCP firewall rule for CrossBoard...");
                    RunProcessAsAdmin("netsh", "advfirewall firewall add rule name=\"CrossBoard TCP\" dir=in action=allow protocol=TCP localport=8765");
                }
                
                // Check if the UDP rule exists
                var udpRuleExists = CheckFirewallRuleExists("CrossBoard UDP");
                if (!udpRuleExists)
                {
                    // Create UDP rule for mDNS
                    Console.WriteLine("Creating UDP firewall rule for CrossBoard mDNS...");
                    RunProcessAsAdmin("netsh", "advfirewall firewall add rule name=\"CrossBoard UDP\" dir=in action=allow protocol=UDP localport=5353");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error setting up firewall rules: {ex.Message}");
                Console.WriteLine("You may need to manually add firewall rules for ports 8765 (TCP) and 5353 (UDP)");
            }
        }
        
        private static bool CheckFirewallRuleExists(string ruleName)
        {
            try
            {
                var process = new Process
                {
                    StartInfo = new ProcessStartInfo
                    {
                        FileName = "netsh",
                        Arguments = $"advfirewall firewall show rule name=\"{ruleName}\"",
                        UseShellExecute = false,
                        RedirectStandardOutput = true,
                        CreateNoWindow = true
                    }
                };
                
                process.Start();
                var output = process.StandardOutput.ReadToEnd();
                process.WaitForExit();
                
                return !output.Contains("No rules match the specified criteria");
            }
            catch
            {
                return false;
            }
        }
        
        private static void RunProcessAsAdmin(string fileName, string arguments)
        {
            try
            {
                var process = new Process
                {
                    StartInfo = new ProcessStartInfo
                    {
                        FileName = fileName,
                        Arguments = arguments,
                        UseShellExecute = true,
                        Verb = "runas" // Run as administrator
                    }
                };
                
                process.Start();
                process.WaitForExit();
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Failed to run process as admin: {ex.Message}");
                throw;
            }
        }
    }
} 