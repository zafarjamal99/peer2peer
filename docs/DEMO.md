# Three-Device Live Demo

## Setup

Put three computers on the same Wi-Fi/LAN.

Find their IPv4 addresses with `ipconfig` on Windows.

Compile the project on each machine:

```powershell
scripts\\compile.bat
```

Start one peer on each device:

```powershell
java -cp out p2p.Main --port 5001 --name PeerA
java -cp out p2p.Main --port 5001 --name PeerB
java -cp out p2p.Main --port 5001 --name PeerC
```

## Discovery test

On each peer:

```text
peers
```

All three should eventually appear.

If they do not, check Windows Firewall and whether the Wi-Fi blocks multicast/client-to-client traffic. Use the manual `--peer IP:PORT` fallback described in README.md.

## File transfer test

On PeerA:

```text
share shared/demo.bin
```

On PeerB:

```text
download demo.bin
```

Before or during the download, share another file or allow PeerB to acquire pieces. The peer list should show piece availability.

## Peer failure test

Use a large file so the transfer lasts long enough to demonstrate failure recovery.

Start:

```text
download demo.bin
```

While it is running, stop PeerA:

```text
disconnect
```

The downloader should report a failed request and retry the piece from another peer when that piece remains available.

## Integrity test

The downloader verifies each received piece using its advertised SHA-256 hash and verifies the final reconstructed file using the whole-file SHA-256 hash.

Expected final message:

```text
[VERIFY] Whole-file SHA-256: <hash>
[DOWNLOAD] COMPLETE: <path>
```

## What to explain to the evaluator

1. UDP multicast is used only for decentralized LAN discovery.
2. TCP transfers file pieces directly between peers.
3. A file is divided into 256 KiB pieces.
4. Each piece has a SHA-256 hash.
5. The downloader chooses rare pieces first.
6. Four workers can download different pieces concurrently.
7. A failed peer does not destroy the download because uncompleted pieces can be retried from other peers.
8. The final SHA-256 verifies the reconstructed file.
9. The same discovery mechanism supports more than three peers without code changes.

## Day 4: File Reconstruction and Integrity Verification

### Test Setup
- Three peers connected to the same Wi-Fi network.
- Test file: `large_test.bin`
- File size: 50 MiB (52,428,800 bytes).
- Piece size: 256 KiB.
- Total pieces: 200.

### File Transfer Test
1. Start the peers on separate devices.
2. Verify automatic LAN peer discovery.
3. Download `large_test.bin` from another peer.
4. Confirm that all 200 pieces are received.
5. Confirm that the reconstructed file is saved in the downloads directory.

### SHA-256 Integrity Verification
The completed download reported this whole-file SHA-256 hash:

`8565a714dca840f8652c5bae9249ab05f5fb5a4f9f13fbe23304b10f68252da2`

Compare the reconstructed file's SHA-256 hash with the source file's hash to confirm that the file was reconstructed correctly.

### Peer Failure and Retry Test
During a transfer, a peer was made unavailable. The downloader retried outstanding pieces using another available peer and completed the download.

Verify this behavior against the actual test logs before presenting the results.

### Conclusion
The recorded tests covered multi-piece file transfer, whole-file integrity verification, and recovery from peer unavailability.
