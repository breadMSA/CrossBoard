@echo off
echo ===================================================
echo CrossBoard - Setup Network Permissions
echo ===================================================
echo.
echo This script will set up the necessary permissions for CrossBoard to work properly:
echo 1. URL ACL permissions for HTTP server (port 8765)
echo 2. Firewall rules for TCP (port 8765) and UDP (port 5353 for mDNS)
echo.
echo Running as administrator...
echo.

REM Set up URL ACL permissions
echo Setting up URL ACL permissions...
netsh http add urlacl url=http://*:8765/ user=Everyone
echo.

REM Set up firewall rules
echo Setting up firewall rules...
echo 1. Adding TCP rule for port 8765...
netsh advfirewall firewall add rule name="CrossBoard TCP" dir=in action=allow protocol=TCP localport=8765
echo.

echo 2. Adding UDP rule for mDNS (port 5353)...
netsh advfirewall firewall add rule name="CrossBoard UDP (mDNS)" dir=in action=allow protocol=UDP localport=5353
echo.

echo ===================================================
echo Setup complete!
echo.
echo If you still have connection issues:
echo 1. Make sure your firewall is allowing CrossBoard
echo 2. Try disabling your firewall temporarily for testing
echo 3. Check that both devices are on the same network
echo 4. Make sure your router allows multicast traffic
echo ===================================================
echo.
pause 