package p2p;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.net.InetSocketAddress;
import java.util.concurrent.*;

public class PeerNode {
    private final String peerId = UUID.randomUUID().toString();
    private final String name;
    private final int port;
    private final int discoveryPort;
    private final String multicastGroup;
    private final FileManager fileManager = new FileManager();
    private final List<SharedFile> localFiles = new CopyOnWriteArrayList<>();
    private final DownloadManager downloadManager;
    private final List<InetSocketAddress> bootstrapPeers;
    private DiscoveryService discovery;
    private ServerSocket serverSocket;
    private ExecutorService serverPool = Executors.newCachedThreadPool();
    private volatile boolean running;

    public PeerNode(String name, int port) {
        this(name, port, 4446, "239.255.42.99", List.of());
    }

    public PeerNode(String name, int port, List<InetSocketAddress> bootstrapPeers) {
        this(name, port, 4446, "239.255.42.99", bootstrapPeers);
    }

    public PeerNode(String name, int port, int discoveryPort, String multicastGroup,
                    List<InetSocketAddress> bootstrapPeers) {
        this.name = name;
        this.port = port;
        this.discoveryPort = discoveryPort;
        this.multicastGroup = multicastGroup;
        this.bootstrapPeers = List.copyOf(bootstrapPeers);
        this.downloadManager = new DownloadManager(this);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        discovery = new DiscoveryService(peerId, name, port, discoveryPort, multicastGroup,
                localFiles, bootstrapPeers);
        discovery.start();

        serverPool.submit(this::acceptLoop);
        System.out.println("[SERVER] TCP listening on port " + port);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                serverPool.submit(() -> handleClient(socket));
            } catch (IOException e) {
                if (running) System.err.println("[SERVER] " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket socket) {
        try (socket;
             DataInputStream in = new DataInputStream(
                     new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(
                     new BufferedOutputStream(socket.getOutputStream()))) {

            socket.setSoTimeout(8000);

            String hello = in.readUTF();
            if (hello.startsWith("DISCOVER_TCP|")) {
                for (String message : discovery.tcpDiscoveryMessages()) {
                    out.writeUTF(message);
                }
                out.writeUTF("END");
                out.flush();
                return;
            }
            if (!hello.startsWith("HELLO|")) {
                out.writeUTF("ERROR|expected HELLO or DISCOVER_TCP");
                out.flush();
                return;
            }

            out.writeUTF("OK");
            out.flush();

            String request = in.readUTF();
            String[] parts = request.split("\\|", -1);
            if (parts.length != 3 || !"GET".equals(parts[0])) {
                out.writeUTF("ERROR|invalid request");
                out.flush();
                return;
            }

            String fileId = parts[1];
            int pieceIndex = Integer.parseInt(parts[2]);

            SharedFile file = fileManager.get(fileId);
            if (file == null || !file.hasPiece(pieceIndex)) {
                out.writeUTF("ERROR|piece unavailable");
                out.flush();
                return;
            }

            byte[] data = file.readPiece(pieceIndex);
            String hash = SharedFile.hex(SharedFile.sha256Bytes(data, 0, data.length));

            out.writeUTF("PIECE|" + pieceIndex + "|" + data.length + "|" + hash);
            out.write(data);
            out.flush();

            // The requester ID will be extracted from HELLO.
            String requesterId = hello.substring(6);
            PeerInfo requester = discovery.findPeer(requesterId);
            if (requester != null) requester.bytesSentToPeer += data.length;

        } catch (Exception e) {
            // A peer leaving mid-transfer is expected; the downloader will retry again.
        }
    }

    public void handleCommand(String line) {
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);

        try {
            switch (command) {
                case "help" -> printHelp();

                case "peers" -> {
                    Collection<PeerInfo> peers = discovery.getPeers();
                    if (peers.isEmpty()) {
                        System.out.println("No peers available.");
                    } else {
                        for (PeerInfo p : peers) {
                            System.out.println("- " + p + " | files=" + p.advertisements().size());
                            for (PeerInfo.Advertisement ad : p.advertisements()) {
                                System.out.println("    " + ad.fileName +
                                        " pieces=" + ad.availableCount() + "/" + ad.pieceCount);
                            }
                        }
                    }
                }

                case "share" -> {
                    if (parts.length < 2) {
                        System.out.println("Usage: share <path>");
                        return;
                    }
                    SharedFile file = fileManager.share(Path.of(parts[1]));
                    localFiles.add(file);
                    System.out.println("[SHARE] " + file.fileName);
                    System.out.println("[SHARE] pieces: " + file.pieceCount);
                    System.out.println("[SHARE] SHA-256: " + file.wholeHash);
                }

                case "files" -> {
                    Collection<SharedFile> files = fileManager.all();
                    if (files.isEmpty()) {
                        System.out.println("No local files.");
                    } else {
                        for (SharedFile f : files) {
                            System.out.println("- " + f.fileName + " " +
                                    f.completedPieces() + "/" + f.pieceCount +
                                    " pieces, id=" + f.fileId.substring(0, 12));
                        }
                    }
                }

                case "download" -> {
                    if (parts.length < 2) {
                        System.out.println("Usage: download <file-name>");
                        return;
                    }
                    downloadManager.startDownload(parts[1]);
                }

                case "status" -> {
                    for (SharedFile f : fileManager.all()) {
                        System.out.println(f.fileName + ": " +
                                f.completedPieces() + "/" + f.pieceCount +
                                " pieces");
                    }
                }

                case "stats" -> {
                    for (PeerInfo p : discovery.getPeers()) {
                        System.out.println(p.name + " | received-from-peer=" +
                                p.bytesReceivedFromPeer + " B | sent-to-peer=" +
                                p.bytesSentToPeer + " B");
                    }
                }

                case "stop" -> {
                    System.out.println("[DOWNLOAD] stop is represented by closing this peer.");
                    System.out.println("Use 'disconnect' to stop the network services.");
                }

                case "resume" -> System.out.println(
                        "A partial .part file is retained only for the current process; " +
                        "run download again after rediscovery.");

                case "disconnect" -> {
                    System.out.println("Disconnecting " + name + "...");
                    stop();
                }

                case "quit", "exit" -> stop();

                default -> System.out.println("Unknown command. Type 'help'.");
            }
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
        }
    }

    private void printHelp() {
        System.out.println("""
                Commands:
                  help                 Show commands
                  peers                Show discovered peers and piece availability
                  share <path>        Share a complete local file
                  files                Show local/partial files
                  download <name>     Download a discovered file
                  status               Show local piece progress
                  stats                Show peer transfer counters
                  stop                 Explain peer stop command
                  resume               Explain partial-download behavior
                  disconnect           Stop this peer
                  quit                 Stop this peer
                """);
    }

    public void stop() {
        if (!running) return;
        running = false;

        try {
            if (discovery != null) discovery.stop();
        } catch (Exception ignored) {}

        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}

        downloadManager.stop();
        serverPool.shutdownNow();
    }

    public boolean isRunning() {
        return running;
    }

    public String peerId() {
        return peerId;
    }

    public FileManager files() {
        return fileManager;
    }

    public DiscoveryService discovery() {
        return discovery;
    }

    public void registerLocalFile(SharedFile file) {
        if (!localFiles.contains(file)) localFiles.add(file);
    }
}
