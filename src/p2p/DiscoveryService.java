package p2p;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Automatic LAN peer discovery using UDP broadcast.
 *
 * No peer IP addresses are required on the command line.
 * Each peer periodically broadcasts HELLO and file availability messages
 * to the local network. The receiver listens on UDP port 4446.
 * File data is never sent through discovery; file pieces use TCP.
 */
public class DiscoveryService {
    private final String peerId;
    private final String peerName;
    private final int tcpPort;
    private final int discoveryPort;
    private final List<SharedFile> localFiles;
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private volatile boolean running;
    private DatagramSocket socket;
    private List<InetAddress> broadcastAddresses = List.of();

    public DiscoveryService(String peerId, String peerName, int tcpPort,
                            int discoveryPort, String ignoredGroup,
                            List<SharedFile> localFiles,
                            List<InetSocketAddress> ignoredBootstrapPeers) {
        this.peerId = peerId;
        this.peerName = peerName;
        this.tcpPort = tcpPort;
        this.discoveryPort = discoveryPort;
        this.localFiles = localFiles;
    }

    public void start() throws IOException {
        broadcastAddresses = findBroadcastAddresses();
        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.setBroadcast(true);
        socket.bind(new InetSocketAddress("0.0.0.0", discoveryPort));

        running = true;
        executor.submit(this::receiveLoop);
        executor.submit(this::announceLoop);

        System.out.println("[DISCOVERY] UDP LAN broadcast on port " + discoveryPort);
        System.out.println("[DISCOVERY] Automatic peer discovery enabled - no peer IP required");
    }

    private void announceLoop() {
        while (running) {
            try {
                broadcast(encodeHello());
                for (SharedFile file : new ArrayList<>(localFiles)) {
                    broadcast(encodeAnnouncement(file));
                }
                Thread.sleep(2000);
            } catch (Exception e) {
                if (running) System.err.println("[DISCOVERY] " + e.getMessage());
            }
        }
    }

    private void broadcast(byte[] data) throws IOException {
        Set<String> sent = new HashSet<>();
        for (InetAddress address : broadcastAddresses) {
            String key = address.getHostAddress();
            if (sent.add(key)) {
                socket.send(new DatagramPacket(data, data.length, address, discoveryPort));
            }
        }
        // Always try limited broadcast as a fallback.
        if (sent.add("255.255.255.255")) {
            socket.send(new DatagramPacket(data, data.length,
                    InetAddress.getByName("255.255.255.255"), discoveryPort));
        }
    }

    private List<InetAddress> findBroadcastAddresses() throws IOException {
        List<InetAddress> result = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            try {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            } catch (SocketException e) {
                continue;
            }
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress broadcast = ia.getBroadcast();
                if (broadcast instanceof Inet4Address) {
                    result.add(broadcast);
                }
            }
        }
        if (result.isEmpty()) {
            result.add(InetAddress.getByName("255.255.255.255"));
        }
        return result;
    }

    private byte[] encodeHello() {
        String message = "HELLO|" + b64(peerId) + "|" + b64(peerName) + "|"
                + b64(Integer.toString(tcpPort)) + "|" + b64(Integer.toString(discoveryPort));
        return message.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] encodeAnnouncement(SharedFile file) {
        String hashes = String.join(",", file.pieceHashes);
        String bitmap = Base64.getEncoder().encodeToString(file.bitmapCopy().toByteArray());
        String[] raw = {
                file.fileId, file.fileName, Long.toString(file.fileSize), Integer.toString(file.pieceSize),
                Integer.toString(file.pieceCount), file.wholeHash, hashes, bitmap
        };
        StringBuilder message = new StringBuilder("ANNOUNCE");
        String[] prefix = { peerId, peerName, Integer.toString(tcpPort), Integer.toString(discoveryPort) };
        for (String value : prefix) message.append('|').append(b64(value));
        for (String value : raw) message.append('|').append(b64(value));
        return message.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void receiveLoop() {
        byte[] buffer = new byte[65507];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), packet.getOffset(),
                        packet.getLength(), StandardCharsets.UTF_8);
                handleMessage(message, packet.getAddress().getHostAddress());
                removeStalePeers();
            } catch (IOException e) {
                if (running) System.err.println("[DISCOVERY] receive error: " + e.getMessage());
            }
        }
    }

    private void handleMessage(String message, String host) {
        try {
            if (message.startsWith("HELLO|")) {
                handleHello(message, host);
            } else if (message.startsWith("ANNOUNCE|")) {
                handleAnnouncement(message, host);
            }
        } catch (Exception ignored) {
            // Ignore malformed discovery packets.
        }
    }

    public List<String> tcpDiscoveryMessages() {
        List<String> messages = new ArrayList<>();
        messages.add(new String(encodeHello(), StandardCharsets.UTF_8));
        for (SharedFile file : new ArrayList<>(localFiles)) {
            messages.add(new String(encodeAnnouncement(file), StandardCharsets.UTF_8));
        }
        return messages;
    }

    private void handleHello(String message, String host) {
        String[] fields = message.split("\\|", -1);
        if (fields.length != 5 || !"HELLO".equals(fields[0])) return;
        try {
            String remoteId = unb64(fields[1]);
            if (peerId.equals(remoteId)) return;
            String name = unb64(fields[2]);
            int port = Integer.parseInt(unb64(fields[3]));
            int remoteDiscoveryPort = Integer.parseInt(unb64(fields[4]));
            PeerInfo peer = peers.computeIfAbsent(remoteId,
                    id -> new PeerInfo(id, name, host, port, remoteDiscoveryPort));
            peer.lastSeen = System.currentTimeMillis();
        } catch (Exception ignored) {
            // Ignore malformed discovery packets.
        }
    }

    private void handleAnnouncement(String message, String host) {
        String[] fields = message.split("\\|", -1);
        if (fields.length != 13 || !"ANNOUNCE".equals(fields[0])) return;

        try {
            String remoteId = unb64(fields[1]);
            if (peerId.equals(remoteId)) return;

            String name = unb64(fields[2]);
            int port = Integer.parseInt(unb64(fields[3]));
            int remoteDiscoveryPort = Integer.parseInt(unb64(fields[4]));
            String fileId = unb64(fields[5]);
            String fileName = unb64(fields[6]);
            long size = Long.parseLong(unb64(fields[7]));
            int pieceSize = Integer.parseInt(unb64(fields[8]));
            int pieceCount = Integer.parseInt(unb64(fields[9]));
            String wholeHash = unb64(fields[10]);

            List<String> pieceHashes = fields[11].isEmpty()
                    ? List.of()
                    : Arrays.asList(unb64(fields[11]).split(",", -1));
            byte[] bitmapBytes = fields[12].isEmpty()
                    ? new byte[0]
                    : Base64.getDecoder().decode(unb64(fields[12]));
            BitSet bitmap = BitSet.valueOf(bitmapBytes);

            PeerInfo peer = peers.computeIfAbsent(remoteId,
                    id -> new PeerInfo(id, name, host, port, remoteDiscoveryPort));
            peer.lastSeen = System.currentTimeMillis();
            peer.updateFile(new PeerInfo.Advertisement(
                    fileId, fileName, size, pieceSize, pieceCount,
                    wholeHash, pieceHashes, bitmap));
        } catch (Exception ignored) {
            // Ignore malformed discovery packets.
        }
    }

    private void removeStalePeers() {
        long cutoff = System.currentTimeMillis() - 7000;
        peers.values().removeIf(p -> p.lastSeen < cutoff);
    }

    public Collection<PeerInfo> getPeers() {
        removeStalePeers();
        return new ArrayList<>(peers.values());
    }

    public List<PeerInfo> peersWithPiece(String fileId, int pieceIndex) {
        List<PeerInfo> result = new ArrayList<>();
        for (PeerInfo p : getPeers()) {
            if (p.hasPiece(fileId, pieceIndex)) result.add(p);
        }
        return result;
    }

    public PeerInfo findPeer(String peerId) {
        return peers.get(peerId);
    }

    public void stop() {
        running = false;
        if (socket != null) socket.close();
        executor.shutdownNow();
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }
}
