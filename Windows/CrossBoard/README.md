# CrossBoard - Windows Client

CrossBoard is a cross-platform clipboard synchronization tool that allows you to share clipboard contents between your Windows PC and Android devices.

## Setup Instructions

### 1. Setup Network Permissions

Before running CrossBoard, you need to set up the necessary network permissions:

1. Run `setup_permissions.bat` as administrator
   - This will set up URL ACL permissions for the HTTP server
   - This will add firewall rules for TCP (port 8765) and UDP (port 5353 for mDNS)

### 2. Run the Application

1. Run the application using `run_as_admin.bat`
   - This will start the application with administrator privileges

### 3. Connect to Android Devices

1. Install and run the CrossBoard app on your Android device
2. Make sure both devices are on the same network
3. The devices should discover each other automatically using mDNS
4. If automatic discovery doesn't work, you can manually enter the IP address of your Android device in the Windows app

## Troubleshooting

If you're having trouble connecting:

1. Make sure both devices are on the same network
2. Check that your firewall is allowing CrossBoard (both TCP and UDP)
3. Try disabling your firewall temporarily for testing
4. Make sure your router allows multicast traffic (required for mDNS)
5. Check the logs in the application for more details

## Manual Connection

If automatic discovery doesn't work, you can manually connect:

1. Find the IP address of your Android device (Settings > About phone > Status > IP address)
2. In the Windows app, enter the IP address in the "Manual Connection" section
3. Click "Connect" to establish a connection

## License

CrossBoard is open source software licensed under the MIT license. 