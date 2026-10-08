
package p2p;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PeerInfo {
    public final String peerId;
    public final String name;
    public final String host;
    public final int port;
    public final int discoveryPort;
    public volatile long lastSeen;
    public volatile long bytesReceivedFromPeer;
    public volatile long bytesSentToPeer;
    
    public static final long STALE_TIMEOUT_MS = 7000L;

public boolean isStale(long now) {
    return now - lastSeen > STALE_TIMEOUT_MS;
}

    private final Map<String, Advertisement> files = new ConcurrentHashMap<>();

    public PeerInfo(String peerId, String name, String host, int port, int discoveryPort) {
        this.peerId = peerId;
        this.name = name;
        this.host = host;
        this.port = port;
        this.discoveryPort = discoveryPort;
        this.lastSeen = System.currentTimeMillis();
    }

    public void updateFile(Advertisement ad) {
        files.put(ad.fileId, ad);
        lastSeen = System.currentTimeMillis();
    }

    public Collection<Advertisement> advertisements() {
        return files.values();
    }

    public Advertisement getFile(String fileId) {
        return files.get(fileId);
    }

    public boolean hasPiece(String fileId, int pieceIndex) {
        Advertisement ad = files.get(fileId);
        return ad != null && ad.hasPiece(pieceIndex);
    }

    @Override
    public String toString() {
        return name + " [" + host + ":" + port + "]";
    }

    public static class Advertisement {
        public final String fileId;
        public final String fileName;
        public final long fileSize;
        public final int pieceSize;
        public final int pieceCount;
        public final String wholeHash;
        public final List<String> pieceHashes;
        private final BitSet bitmap;

        public Advertisement(
                String fileId,
                String fileName,
                long fileSize,
                int pieceSize,
                int pieceCount,
                String wholeHash,
                List<String> pieceHashes,
                BitSet bitmap) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.pieceSize = pieceSize;
            this.pieceCount = pieceCount;
            this.wholeHash = wholeHash;
            this.pieceHashes = List.copyOf(pieceHashes);
            this.bitmap = (BitSet) bitmap.clone();
        }

        public boolean hasPiece(int index) {
            return bitmap.get(index);
        }

        public int availableCount() {
            return bitmap.cardinality();
        }

        public BitSet bitmapCopy() {
            return (BitSet) bitmap.clone();
        }
    }
}
