# Three-Peer LAN Discovery

## Overview

The P2P File Sharing Network discovers peers automatically over a shared local area network.

## Network Configuration

- Discovery transport: UDP broadcast
- Discovery port: 4446
- File-transfer transport: TCP
- File-transfer port: 5001
- Test devices should be connected to the same Wi-Fi network or LAN.

## How Discovery Works

1. Each peer starts its discovery service.
2. Peers broadcast discovery messages over the local network.
3. Other peers receive these messages and record peer identity and connection information.
4. Peers announce shared-file metadata and piece availability.
5. Stale peers are removed after the configured timeout.

## Testing Procedure

1. Compile the project using `.\scripts\compile.bat`.
2. Start the P2P application on each device.
3. Confirm all devices are connected to the same network.
4. Check the discovered peers using the application's peer-list command.
5. Share a test file and verify that its availability is advertised.

## Troubleshooting

- Confirm all devices are connected to the same network.
- Check Windows Firewall permissions for UDP port 4446 and TCP port 5001.
- Make sure the application is running on every device.
- Allow time for discovery messages to arrive.
- Check whether the Wi-Fi network isolates connected devices.

## Expected Result

Peers on a reachable local network should discover one another and advertise information needed for file sharing. Results depend on firewall and network settings.
