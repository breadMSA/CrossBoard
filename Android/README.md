# CrossBoard - Android Client

CrossBoard is a cross-platform clipboard synchronization tool that allows you to share clipboard contents between your Android device and Windows PC.

## Features

- Automatic device discovery using mDNS
- Direct connection to Windows PC using IP address
- Clipboard synchronization across devices
- Background service for continuous operation

## Setup Instructions

### 1. Install the App

1. Install the app on your Android device
2. Grant the necessary permissions:
   - Clipboard access
   - Network access
   - Background service permission

### 2. Connect to Windows PC

#### Automatic Discovery

1. Make sure both devices are on the same network
2. Start the CrossBoard service on your Android device
3. Start the CrossBoard application on your Windows PC
4. The devices should discover each other automatically using mDNS

#### Manual Connection

If automatic discovery doesn't work:

1. Find the IP address of your Windows PC
2. In the Android app, go to the Status tab
3. Enter the IP address in the "Direct Connection to Windows PC" section
4. Click "Connect to PC" to establish a connection

## Troubleshooting

If you're having trouble connecting:

1. Make sure both devices are on the same network
2. Check that your firewall is not blocking the connection
3. Try disabling your firewall temporarily for testing
4. Make sure your router allows multicast traffic (required for mDNS)
5. Try the manual connection method

## License

CrossBoard is open source software licensed under the MIT license. 