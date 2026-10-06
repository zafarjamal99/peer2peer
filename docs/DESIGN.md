# Design Document

## 1. Architecture

The project uses a hybrid networking model:

```text
             UDP multicast discovery
        +------------+------------+
        |            |            |
      Peer 1       Peer 2       Peer 3
        | \           |           / |
        |  \          |          /  |
        +--- TCP piece transfers ---+
```

There is no central server for file data. Every peer runs both:

1. A discovery component that announces its identity and available pieces.
2. A TCP server that serves pieces directly to other peers.
3. A download manager that requests pieces from other peers.

## 2. Peer discovery

Each peer runs a small UDP discovery socket. Peers can be started with one or more bootstrap peer addresses. Every few seconds a peer sends discovery requests and availability announcements to known peers. A peer that receives a discovery request responds with its current announcements.

This is a small peer-to-peer gossip/bootstrap mechanism rather than a central tracker. For a same-machine demo, each peer uses a different discovery UDP port (`TCP port + 10000`).

The announcement contains:

- peer ID
- TCP port
- file name
- file ID (whole-file SHA-256)
- file size
- piece size
- piece count
- whole-file SHA-256
- bitset showing pieces currently available

Announcements are small control messages. They do not contain file data.

A peer stores recently seen peers in an in-memory peer table. Entries older than the peer TTL are removed.

This avoids a central tracker for the small demonstration network.

## 3. File splitting

Default piece size:

```text
64 KiB
```

For a file of size S:

```text
pieceCount = ceil(S / 65536)
```

The last piece can be smaller.

Each piece is addressed by:

```text
(fileId, pieceIndex)
```

The file ID is the SHA-256 of the complete original file.

## 4. Integrity verification

For every piece, the seeding peer calculates:

```text
SHA-256(piece)
```

The manifest contains all piece hashes.

When a peer downloads a piece:

1. It receives the raw bytes over TCP.
2. It calculates SHA-256 locally.
3. It compares the result with the expected piece hash.
4. It writes the piece only if the hash matches.

After every piece is present, the complete file SHA-256 is calculated and compared with the advertised file ID.

Therefore corrupted or tampered pieces are rejected.

## 5. Piece selection

The download manager uses a simplified **rarest-first** strategy.

For each missing piece:

```text
availability(piece) = number of currently known peers having that piece
```

The next piece is selected from the missing pieces with the smallest availability.

This improves distribution because peers request pieces that are less replicated first.

Tie-breaking is random to avoid every peer choosing exactly the same piece.

## 6. Parallel transfer

A downloading peer starts several worker threads.

Each worker:

```text
select rarest missing piece
        ↓
select a peer owning that piece
        ↓
request piece over TCP
        ↓
verify SHA-256
        ↓
write piece
        ↓
update local availability
```

Because workers can choose different peers, pieces can be downloaded concurrently.

## 7. Peer churn / failure handling

A TCP request has a timeout. If a peer:

- disconnects,
- crashes,
- refuses the request,
- times out,
- sends an invalid piece,

the worker marks that peer as failed for the current attempt and retries the same piece using another peer.

The piece is not marked complete until integrity verification succeeds.

If a peer disappears completely, its UDP announcement expires from the peer table.

This means one peer failure should not destroy the entire download as long as another peer has the required pieces.

## 8. Fairness / free-riding

A small project cannot reproduce all of BitTorrent's production incentive mechanisms, but this implementation tracks:

```text
uploadedBytes
downloadedBytes
```

for each peer.

The downloader gives a small preference to peers that have previously uploaded successful pieces to us.

A full BitTorrent-style system would use:

- choking/unchoking,
- tit-for-tat,
- optimistic unchoking,
- upload slot limits.

For this assignment, those mechanisms are discussed as a design extension rather than adding unnecessary complexity.

## 9. Why it would not scale

This design is intentionally small-scale.

Limitations:

- UDP multicast does not work reliably across every routed network.
- Every peer maintains an in-memory peer table.
- Announcements grow with the number of files/pieces.
- There is no distributed hash table.
- There is no persistent tracker/DHT.
- There is no NAT traversal.
- Peer authentication is not implemented.
- Piece requests are simple TCP connections rather than persistent pipelined connections.
- Large swarms would require stronger congestion control, peer ranking, and connection management.

A production system could replace multicast discovery with a DHT or tracker-assisted bootstrap while keeping direct peer-to-peer piece transfer.

## 10. Decentralization trade-off

The system is not "serverless" in the sense that there are no network services. Every peer is a server for pieces. The important property is that no central server is required to store or distribute the file.

Discovery is decentralized within the demo network because peers exchange UDP announcements directly. A bootstrap address is only a starting point; it does not carry file data.

## 11. Protocol summary

### UDP discovery

```text
ANNOUNCE|peerId|port|fileId|fileName|fileSize|pieceSize|pieceCount|wholeHash|pieceHashList|bitmap
```

Fields are Base64 encoded where necessary.

### TCP

Client:

```text
HELLO|peerId
GET|fileId|pieceIndex
```

Server:

```text
OK
PIECE|pieceIndex|length|pieceHash
<raw bytes>
```

or:

```text
ERROR|reason
```
