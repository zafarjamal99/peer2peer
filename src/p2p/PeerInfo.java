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

    private final Map<String, Advertisement> files =
            new ConcurrentHashMap<>();

    public PeerInfo(
            String peerId,
            String name,
            String host,
            int port,
            int discoveryPort) {

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

    public boolean hasPiece(
            String fileId,
            int pieceIndex) {

        Advertisement ad = files.get(fileId);

        return ad != null &&
                ad.hasPiece(pieceIndex);
    }

    // Number of pieces this peer currently advertises
    // for the requested file.
    public int availablePieces(String fileId) {
        Advertisement ad = files.get(fileId);

        return ad == null
                ? 0
                : ad.availableCount();
    }

    // Returns a copy of the advertised piece bitmap.
    public BitSet pieceBitmap(String fileId) {
        Advertisement ad = files.get(fileId);

        return ad == null
                ? new BitSet()
                : ad.bitmapCopy();
    }

    public boolean hasFile(String fileId) {
        return files.containsKey(fileId);
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

        public boolean isComplete() {
            return bitmap.cardinality() == pieceCount;
        }
    }
}