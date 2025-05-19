package main.java; // Assuming a root package for the launcher

import main.java.network.ClientNode;
import main.java.network.HostServer;
import main.java.util.Constants; // For default socket port

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("======================================");
        System.out.println("  Welcome to DoubleA™ Multiplayer Shooter Game ");
        System.out.println("======================================");
        System.out.println("Choose an option:");
        System.out.println("  1. Create a new game (You will be the Host and Player1)");
        System.out.println("  2. Join an existing game");
        System.out.print("Enter your choice (1 or 2): ");

        String choice = scanner.nextLine().trim();

        if ("1".equals(choice)) {
            System.out.println("\n--- Creating a New Game (You are the Host & Player1) ---");

            int rmiPort = 1099; // Default RMI Port for the host
            int gameSocketPort = Constants.HOST_PORT; // Default game socket port

            // You can allow overriding these ports via input if desired
            System.out.print("Enter RMI Port for hosting (default 1099, press Enter for default): ");
            String rmiPortInput = scanner.nextLine().trim();
            if (!rmiPortInput.isEmpty()) {
                try {
                    rmiPort = Integer.parseInt(rmiPortInput);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid RMI port, using default " + rmiPort);
                }
            }

            System.out.print("Enter Game Socket Port for hosting (default " + gameSocketPort + ", press Enter for default): ");
            String gamePortInput = scanner.nextLine().trim();
            if (!gamePortInput.isEmpty()) {
                try {
                    gameSocketPort = Integer.parseInt(gamePortInput);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid game socket port, using default " + gameSocketPort);
                }
            }

            final int finalRmiPort = rmiPort;
            final int finalGameSocketPort = gameSocketPort;

            // Start HostServer in a new thread
            Thread hostThread = new Thread(() -> {
                HostServer server = new HostServer(finalGameSocketPort, finalRmiPort);
                // The server's start() method will print its IP.
                // We need to make sure it's fully started before the client tries to connect.
                server.start(); // This method blocks until server stops or errors
            });
            hostThread.setName("HostServerThread");
            hostThread.setDaemon(true); // Allow JVM to exit if only daemon threads are left
            hostThread.start();

            System.out.println("\nHost server starting in the background on RMI Port: " + finalRmiPort + " and Game Socket Port: " + finalGameSocketPort + "...");
            try {
                String myIp = InetAddress.getLocalHost().getHostAddress();
                System.out.println("Tell your friends to join using RMI on IP: " + myIp + " and RMI Port: " + finalRmiPort);
            } catch (UnknownHostException e) {
                System.err.println("Could not determine your local IP address.");
            }

            // Give the server a moment to start up before the local client tries to connect
            System.out.println("Waiting a few seconds for the server to initialize...");
            try {
                Thread.sleep(3000); // Adjust as needed, or implement a more robust check
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("\n--- Starting your client (Player1) to connect to your local host ---");
            // The host player connects to their own server using localhost for RMI
            ClientNode hostPlayerClient = new ClientNode("localhost", finalRmiPort);
            hostPlayerClient.start(); // This will block for user input

        } else if ("2".equals(choice)) {
            System.out.println("\n--- Joining an Existing Game ---");
            System.out.print("Enter Host's IP address (for RMI connection): ");
            String hostIp = scanner.nextLine().trim();

            int rmiPort = 1099; // Default RMI port to connect to
            System.out.print("Enter Host's RMI Port (default 1099, press Enter for default): ");
            String rmiPortInput = scanner.nextLine().trim();
            if (!rmiPortInput.isEmpty()) {
                try {
                    rmiPort = Integer.parseInt(rmiPortInput);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid RMI port, using default " + rmiPort);
                }
            }

            if (hostIp.isEmpty()) {
                System.out.println("Host IP cannot be empty. Exiting.");
            } else {
                ClientNode clientNode = new ClientNode(hostIp, rmiPort);
                clientNode.start(); // This will block for user input
            }
        } else {
            System.out.println("Invalid choice. Exiting.");
        }
        scanner.close();
        System.out.println("Main application thread finished. Game might be running in other threads if started.");
    }
}
