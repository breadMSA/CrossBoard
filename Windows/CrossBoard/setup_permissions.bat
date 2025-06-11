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
echo Setting up CrossBoard firewall rules...
echo.

REM Run as administrator check
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo This script requires administrator privileges.
    echo Please run this script as administrator.
    pause
    exit /b 1
)

echo Adding firewall rules for CrossBoard...

REM Get the full path of the executable
set EXEPATH=%~dp0CrossBoard.exe
if not exist "%EXEPATH%" (
    set EXEPATH=%~dp0bin\Debug\net6.0-windows\CrossBoard.exe
)
if not exist "%EXEPATH%" (
    set EXEPATH=%~dp0bin\Release\net6.0-windows\CrossBoard.exe
)

echo Executable path: %EXEPATH%

REM Remove any existing rules with the same name
netsh advfirewall firewall delete rule name="CrossBoard TCP (8765)" >nul 2>&1
netsh advfirewall firewall delete rule name="CrossBoard mDNS (5353)" >nul 2>&1

REM Add TCP rule for port 8765
echo Adding TCP rule for port 8765...
netsh advfirewall firewall add rule name="CrossBoard TCP (8765)" dir=in action=allow protocol=TCP localport=8765 profile=any program="%EXEPATH%" enable=yes

REM Add UDP rule for mDNS (port 5353)
echo Adding UDP rule for mDNS (port 5353)...
netsh advfirewall firewall add rule name="CrossBoard mDNS (5353)" dir=in action=allow protocol=UDP localport=5353 profile=any program="%EXEPATH%" enable=yes

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