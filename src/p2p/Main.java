package p2p;

import java.nio.file.*;
import java.net.*;
import java.util.*;

// Application entry point
public class Main {
    public static void main(String[] args) throws Exception {
        int port = 5001;
        int discoveryPort = 4446;
        String multicastGroup = "239.255.42.99";
        String name = "Peer-" + UUID.randomUUID().toString().substring(0, 6);

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--name" -> name = args[++i];
                case "--discovery-port" -> discoveryPort = Integer.parseInt(args[++i]);
                case "--group" -> multicastGroup = args[++i];
                default -> {
                    System.out.println("Unknown argument: " + args[i]);
                    return;
                }
            }
        }

        Files.createDirectories(Path.of("downloads"));
        Files.createDirectories(Path.of("shared"));

        PeerNode node = new PeerNode(name, port, discoveryPort, multicastGroup, List.of());
        Runtime.getRuntime().addShutdownHook(new Thread(node::stop));
        node.start();

        System.out.println();
        System.out.println("P2P File Sharing Peer Application");
        System.out.println("---------------------");
        System.out.println("Name: " + name);
        System.out.println("TCP port: " + port);
        System.out.println("Typing 'help' for commands.");
        System.out.println();

        Scanner scanner = new Scanner(System.in);
        while (node.isRunning() && scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) {
                node.handleCommand(line);
            }
        }
        node.stop();
    }
}
