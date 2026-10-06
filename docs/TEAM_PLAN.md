# Team of 3 — Work Division and Git Commit Plan

Use one GitHub repository and make small commits as features are completed. Everyone pulls before starting new work.

## Member 1 — Networking & Discovery

Own:
- `DiscoveryService.java`
- peer discovery protocol
- UDP bootstrap/gossip
- `PeerInfo.java`
- discovery testing

Suggested commits:
```text
chore: initialize java project structure
feat: add peer identity and udp discovery
feat: exchange peer file availability bitmaps
fix: remove stale peers from discovery table
```

## Member 2 — File Pieces & Integrity

Own:
- `SharedFile.java`
- `FileManager.java`
- piece splitting
- SHA-256 piece and whole-file verification
- partial file assembly

Suggested commits:
```text
feat: add file metadata and piece splitting
feat: calculate sha256 piece hashes
feat: add partial file storage
feat: verify complete file integrity
```

## Member 3 — TCP Transfer, Download & Demo

Own:
- `PeerNode.java`
- `DownloadManager.java`
- TCP piece requests
- rarest-first selection
- parallel workers
- retry/peer-drop handling
- terminal demo

Suggested commits:
```text
feat: add tcp peer piece server
feat: add parallel piece downloader
feat: implement rarest-first selection
feat: retry failed peer transfers
feat: add terminal commands and transfer stats
```

## Shared integration commits

After all three parts are merged/tested:

```text
test: validate three-peer transfer
fix: handle concurrent piece claims
fix: handle peer disconnect during transfer
docs: add architecture and live demo
chore: add compile and peer launch scripts
```

## Daily Git rule

Before work:
```bash
git pull --rebase origin main
```

After work:
```bash
git status
git add .
git commit -m "<small clear change>"
git push origin main
```

Do not make one huge final commit. The commit history should show who built which part.

## Recommended repository branches

For a simple college project:
- `main` = stable/demo-ready version
- `feature/discovery` = Member 1
- `feature/file-integrity` = Member 2
- `feature/transfer` = Member 3

Merge feature branches into `main` after testing.
