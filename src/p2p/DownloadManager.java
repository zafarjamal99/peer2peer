package p2p;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class DownloadManager {
    private final PeerNode node;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final ExecutorService coordinators = Executors.newCachedThreadPool();
    private final Set<String> activeDownloads = ConcurrentHashMap.newKeySet();

    public DownloadManager(PeerNode node) {
        this.node = node;
    }

    public void startDownload(String fileName) {
        PeerInfo.Advertisement ad = findAdvertisement(fileName);
        if (ad == null) {
            System.out.println("[DOWNLOAD] File not discovered yet: " + fileName);
            return;
        }

        if (!activeDownloads.add(ad.fileId)) {
            System.out.println("[DOWNLOAD] Already downloading " + fileName);
            return;
        }

        coordinators.submit(() -> {
            try {
                download(ad);
            } finally {
                activeDownloads.remove(ad.fileId);
            }
        });
    }

    private PeerInfo.Advertisement findAdvertisement(String fileName) {
        for (PeerInfo peer : node.discovery().getPeers()) {
            for (PeerInfo.Advertisement ad : peer.advertisements()) {
                if (ad.fileName.equals(fileName)) return ad;
            }
        }
        return null;
    }

    private void download(PeerInfo.Advertisement ad) {
        final SharedFile file;
        try {
            file = node.files().createPartial(ad);
            node.registerLocalFile(file);
        } catch (IOException e) {
            System.err.println("[DOWNLOAD] Cannot create partial file: " + e.getMessage());
            return;
        }

        if (file.isComplete()) {
            finish(file);
            return;
        }

        System.out.println("[DOWNLOAD] Starting " + ad.fileName +
                " (" + ad.pieceCount + " pieces, 4 workers)");

        CountDownLatch latch = new CountDownLatch(4);
        Set<Integer> claimedPieces = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < 4; i++) {
            workers.submit(() -> {
                try {
                    workerLoop(file, claimedPieces);
                } finally {
                    latch.countDown();
                }
            });
        }

      
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (file.isComplete()) {
            finish(file);
        } else {
            System.out.println("[DOWNLOAD] Stopped with " +
                    file.completedPieces() + "/" + file.pieceCount +
                    " pieces completed. Remaining pieces may need more available peers.");
        }
    }
    

    private void workerLoop(SharedFile file, Set<Integer> claimedPieces) {
        Set<String> failedPeersForPiece = new HashSet<>();

        while (node.isRunning() && !file.isComplete()) {
            int piece = chooseRarestPiece(file);
            if (piece < 0) {
                sleep(700);
                continue;
            }

            if (!claimedPieces.add(piece)) continue;

            List<PeerInfo> candidates = node.discovery().peersWithPiece(file.fileId, piece);
            candidates.removeIf(p -> failedPeersForPiece.contains(p.peerId));
            candidates.sort(Comparator.comparingLong((PeerInfo p) -> p.bytesReceivedFromPeer).reversed());

            if (candidates.isEmpty()) {
                claimedPieces.remove(piece);
                failedPeersForPiece.clear();
                sleep(700);
                continue;
            }

            boolean success = false;
            for (PeerInfo peer : candidates) {
                try {
                    byte[] data = requestPiece(peer, file.fileId, piece);
                    String actual = hexSha256(data);

                    if (!actual.equalsIgnoreCase(file.pieceHashes.get(piece))) {
                        System.out.println("[VERIFY] Rejected corrupt piece " + piece +
                                " from " + peer.name);
                        failedPeersForPiece.add(peer.peerId);
                        continue;
                    }

                    file.writePiece(piece, data);
                    file.markPiece(piece);
                    peer.bytesReceivedFromPeer += data.length;
                    int now = file.completedPieces();

                    System.out.println("[DOWNLOAD] piece " + piece + " <- " +
                            peer.name + " | " + now + "/" + file.pieceCount);

                    success = true;
                    failedPeersForPiece.clear();
                    break;
                } catch (Exception e) {
                    failedPeersForPiece.add(peer.peerId);
                    System.out.println("[RETRY] piece " + piece + " from " +
                            peer.name + " failed: " + e.getMessage());
                }
            }

            claimedPieces.remove(piece);
            if (!success) {
                sleep(500);
            }
        }
    }

    private int chooseRarestPiece(SharedFile file) {
        List<Integer> candidates = new ArrayList<>();
        int minAvailability = Integer.MAX_VALUE;

        for (int i = 0; i < file.pieceCount; i++) {
            if (file.hasPiece(i)) continue;

            int availability = node.discovery().peersWithPiece(file.fileId, i).size();

            if (availability == 0) continue;
            if (availability < minAvailability) {
                minAvailability = availability;
                candidates.clear();
                candidates.add(i);
            } else if (availability == minAvailability) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) return -1;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private byte[] requestPiece(PeerInfo peer, String fileId, int pieceIndex) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(peer.host, peer.port), 1500);
            socket.setSoTimeout(5000);

            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream()));

            out.writeUTF("HELLO|" + node.peerId());
            out.flush();

            String hello = in.readUTF();
            if (!"OK".equals(hello)) throw new IOException("HELLO rejected");

            out.writeUTF("GET|" + fileId + "|" + pieceIndex);
            out.flush();

            String header = in.readUTF();
            String[] parts = header.split("\\|", -1);

            if (parts.length != 4 || !"PIECE".equals(parts[0])) {
                throw new IOException(header.startsWith("ERROR|")
                        ? header.substring(6): "invalid piece response");
            }

            int index = Integer.parseInt(parts[1]);
            int length = Integer.parseInt(parts[2]);
            if (index != pieceIndex || length < 0 || length > SharedFile.PIECE_SIZE) {
                throw new IOException("invalid piece metadata");
            }

            byte[] data = in.readNBytes(length);
            if (data.length != length) throw new EOFException("peer disconnected");
            return data;
        }
    }

    private void finish(SharedFile file) {
        try {
            String hash = file.verifyWholeFile();
            if (!hash.equalsIgnoreCase(file.wholeHash)) {
                System.out.println("[VERIFY] FINAL HASH FAILED");
                return;
            }

            Path finalPath = Path.of("downloads", file.fileName);
            Files.copy(file.path, finalPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[VERIFY] Whole-file SHA-256: " + hash);
            System.out.println("[DOWNLOAD] COMPLETE: " + finalPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[DOWNLOAD] Finalization failed: " + e.getMessage());
        }
    }

    private static String hexSha256(byte[] data) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return SharedFile.hex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        coordinators.shutdownNow();
        workers.shutdownNow();
    }
}
