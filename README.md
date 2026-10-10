# P2P File Sharing Network — Small-Scale BitTorrent

A terminal-based Java implementation of a small BitTorrent-inspired peer-to-peer file sharing system.

## Important: works across multiple physical devices

The project now uses **UDP multicast for LAN discovery**. You can run peers on:

- 3 laptops/PCs on the same Wi-Fi/LAN
- 10+ devices on the same LAN, subject to the network's multicast/firewall limits
- multiple peers on one machine for local testing

File data is transferred directly between peers using TCP. There is no central file server or tracker.

### Network model

```text
                 UDP multicast discovery
                    239.255.42.99:4446
                    /       |       \\
                   /        |        \\
              Peer A     Peer B     Peer C   ... Peer N
                |  \\       /  \\       /  |
                +--------- TCP piece transfer --------+
```

Discovery packets contain peer identity, TCP port, file metadata and piece availability. Actual file pieces never travel through multicast.

## Requirements

- JDK 17 or newer
- Git
- All physical devices should be on the same LAN/Wi-Fi for automatic multicast discovery
- Windows Firewall must allow Java/JDK network access on the **Private network**

Check:

```bash
java -version
javac -version
```

## Compile

### Windows

```powershell
scripts\\compile.bat
```

### Linux/macOS

```bash
chmod +x scripts/*.sh
./scripts/compile.sh
```

## Run on 3 DIFFERENT DEVICES

### Step 1 — find each device's LAN IPv4 address

Windows:

```powershell
ipconfig
```

Look for the Wi-Fi/Ethernet adapter's `IPv4 Address`, for example:

```text
Device A: 192.168.1.10
Device B: 192.168.1.11
Device C: 192.168.1.12
```

### Step 2 — use a TCP port

You can use the same TCP port on different physical devices, for example `5001` on all three devices.

### Step 3 — start each peer

Device A:

```powershell
java -cp out p2p.Main --port 5001 --name PeerA
```

Device B:

```powershell
java -cp out p2p.Main --port 5001 --name PeerB
```

Device C:

```powershell
java -cp out p2p.Main --port 5001 --name PeerC
```

They should discover one another automatically through UDP multicast.

Run:

```text
peers
```

If discovery is working, each peer should show the other peers.

## If multicast is blocked by the Wi-Fi/network

Some college, hostel, office and public Wi-Fi networks block multicast or isolate wireless clients. Use manual bootstrap as a fallback.

Example if Device A is `192.168.1.10:5001`:

```powershell
java -cp out p2p.Main --port 5001 --name PeerB --peer 192.168.1.10:5001
```

For Peer C:

```powershell
java -cp out p2p.Main --port 5001 --name PeerC --peer 192.168.1.10:5001 --peer 192.168.1.11:5001
```

`--peer IP:TCP_PORT` is only a bootstrap/discovery address. File pieces are still transferred directly over TCP between peers.

## Windows Firewall

On the first run, Windows may ask whether Java should communicate on networks. Allow it on **Private networks**.

If discovery works but downloading fails, allow the selected TCP port (for example `5001`) through Windows Defender Firewall for the private network.

The discovery port is `4446` UDP by default.

## Run more than 3 devices

No code changes are required.

Every peer joins the same multicast group:

```text
239.255.42.99:4446
```

So Peer D, Peer E, Peer F, etc. can simply start with:

```powershell
java -cp out p2p.Main --port 5001 --name PeerD
```

on separate physical machines.

On the **same physical machine**, TCP ports must be different:

```powershell
java -cp out p2p.Main --port 5001 --name Peer1
java -cp out p2p.Main --port 5002 --name Peer2
java -cp out p2p.Main --port 5003 --name Peer3
```

## Basic commands

```text
help
peers
share <path>
files
download <file-name>
status
stats
disconnect
quit
```

## Test file sharing

On Peer A:

```text
share shared/test.bin
```

On Peer B/C:

```text
peers
```

Then:

```text
download test.bin
```

The downloader uses multiple worker threads and rarest-first selection, so pieces can be fetched concurrently from different peers.

## Test peer failure

Use a sufficiently large file. Start a download on Peer C and then stop Peer A:

```text
disconnect
```

The downloader should retry pieces through another available peer.

## Git workflow

Commit every meaningful contribution:

```bash
git add .
git commit -m "feat: add LAN multicast peer discovery"
git push
```

Suggested commits:

```text
chore: initialize project structure
feat: add peer identity and LAN discovery
feat: add file metadata and piece hashing
feat: add TCP piece transfer
feat: add rarest-first parallel download
feat: handle peer failure during transfer
fix: support multicast discovery across physical devices
test: validate three-device LAN transfer
docs: add LAN setup and demo instructions
```

## Limitations / scaling discussion

This is intentionally a small educational BitTorrent-style implementation. It does not implement a DHT, NAT traversal, encryption, choking/unchoking, persistent peer reputation, or tracker redundancy. UDP discovery is LAN-scoped and multicast-dependent. For internet-scale deployment, a DHT or redundant tracker, authenticated metadata, congestion-aware scheduling, NAT traversal and stronger peer incentives would be required.

## Day 4: Three-Peer Transfer Setup
### Prerequisites
- Java installed on each device.
- All peer devices connected to the same reachable Wi-Fi network or LAN.
- Firewall permissions configured for discovery and file-transfer ports.

### Compile
Run this command from the project directory on each device:

```powershell
.\scripts\compile.bat
```

### Three-Peer Test Procedure
1. Compile the project on Peer A, Peer B, and Peer C.
2. Start all three peers using the project's existing startup procedure.
3. Ensure each peer has a unique peer ID and appropriate TCP port.
4. Share a test file from Peer A.
5. Verify that Peer B and Peer C discover Peer A.
6. Download the file from Peer A to Peer B.
7. Download the same file from Peer A to Peer C.
8. Compare the SHA-256 hashes of the original and downloaded files.

### Expected Results
- All three peers discover one another.
- The shared file is visible to the other peers.
- Both downloads complete successfully.
- The original and downloaded file hashes match.

### Test Record
Record peer IDs, the test file name, download results, hash comparisons, and any errors observed.