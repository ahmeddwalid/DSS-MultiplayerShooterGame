package main.java.network; // Assuming this is your package structure

import main.java.core.*;
import main.java.logic.EventQueue;
import main.java.logic.LogicalClock;
import main.java.util.Constants;
import main.java.util.MessageType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Optional;

public class ClientHandler implements Runnable {
    private final Socket clientGameEventSocket; // Renamed for clarity
    private ObjectOutputStream outStream; // Renamed for clarity
    private ObjectInputStream inStream;  // Renamed for clarity
    private final HostServer hostServer;
    private String playerId; // This will be set based on the first message from client over socket
    private final LogicalClock hostClock;
    private final EventQueue eventQueue;
    private final GameState gameState;
    private Player associatedPlayer;

    public ClientHandler(Socket socket, HostServer server, LogicalClock clock, EventQueue eventQueue, GameState gameState) {
        this.clientGameEventSocket = socket;
        this.hostServer = server;
        this.hostClock = clock;
        this.eventQueue = eventQueue;
        this.gameState = gameState;
    }

    public String getPlayerId() { return playerId; }
    public Player getAssociatedPlayer() { return associatedPlayer; }
    public SocketAddress getRemoteSocketAddress() { return clientGameEventSocket.getRemoteSocketAddress(); }

    @Override
    public void run() {
        try {
            outStream = new ObjectOutputStream(clientGameEventSocket.getOutputStream());
            inStream = new ObjectInputStream(clientGameEventSocket.getInputStream());

            // Expect first message from client to be CLIENT_CONNECT with its RMI-assigned PlayerID
            Message clientIdentificationMsg = (Message) inStream.readObject();
            if (clientIdentificationMsg.getMessageType() == MessageType.CLIENT_CONNECT &&
                    clientIdentificationMsg.getPayload() instanceof String) {

                this.playerId = (String) clientIdentificationMsg.getPayload();
                hostClock.update(clientIdentificationMsg.getLogicalTimestamp());
                System.out.println("Host (Socket): Client identified itself as: " + this.playerId + " from " + clientGameEventSocket.getRemoteSocketAddress());

                synchronized(gameState) {
                    // The RMI call in HostServer only assigns an ID and returns it.
                    // It does NOT add the Player to the GameState.
                    // So, we create and add the Player object here.
                    Optional<Player> existingPlayerOpt = gameState.getPlayer(this.playerId);
                    if (existingPlayerOpt.isPresent()) {
                        // This case should ideally not happen if RMI only assigns ID and doesn't add to GameState
                        this.associatedPlayer = existingPlayerOpt.get();
                        System.out.println("Host (Socket): Warning - Found pre-existing player object for " + this.playerId + " in GameState. Updating details.");
                        this.associatedPlayer.setIpAddress(clientGameEventSocket.getInetAddress().getHostAddress());
                        this.associatedPlayer.setPort(clientGameEventSocket.getPort());
                    } else {
                        System.out.println("Host (Socket): Creating new Player object for " + this.playerId + " and adding to GameState.");
                        int clientIndexForPositioning = hostServer.getConnectedClientCount();
                        int x = clientIndexForPositioning % Constants.GRID_SIZE;
                        int y = (clientIndexForPositioning / Constants.GRID_SIZE) % Constants.GRID_SIZE;
                        Position initialPosition = new Position(x, y);
                        if (!gameState.getGrid().isValidPosition(initialPosition)) initialPosition = new Position(0,0);

                        this.associatedPlayer = new Player(this.playerId, initialPosition, Constants.MAX_HEALTH);
                        this.associatedPlayer.setIpAddress(clientGameEventSocket.getInetAddress().getHostAddress());
                        this.associatedPlayer.setPort(clientGameEventSocket.getPort());
                        gameState.addPlayer(this.associatedPlayer);
                    }
                }

                hostClock.tick();
                // Send a confirmation back over the socket
                ConnectionAcceptedPayload acceptedPayload = new ConnectionAcceptedPayload(this.playerId, gameState.deepCopy());
                Message socketConfirmationResponse = new Message(MessageType.CONNECTION_ACCEPTED, acceptedPayload, HostServer.HOST_ID_FOR_MESSAGES, hostClock.getCurrentTime());
                sendMessage(socketConfirmationResponse);
                System.out.println("Host (Socket): Sent CONNECTION_ACCEPTED (socket confirmation) to " + this.playerId);

                // Now that the player is fully set up (ID known, Player object in GameState), register with HostServer
                hostServer.addClient(this);
                // And broadcast that this player has joined (this might be slightly redundant if addClient also broadcasts full state)
                hostServer.broadcastPlayerJoined(this.associatedPlayer, gameState.deepCopy());

            } else {
                System.err.println("Host (Socket): Invalid first message from client " + clientGameEventSocket.getRemoteSocketAddress() +
                        ". Expected CLIENT_CONNECT with PlayerID. Received: " +
                        (clientIdentificationMsg != null ? clientIdentificationMsg.getMessageType() : "null message"));
                cleanup();
                return;
            }

            // Main loop to listen for game action messages from this client
            while (!clientGameEventSocket.isClosed()) {
                Message clientMessage = (Message) inStream.readObject();
                if (clientMessage != null) {
                    hostClock.update(clientMessage.getLogicalTimestamp());
                    System.out.println("Host (Socket): Received message from " + clientMessage.getSenderId() +
                            " (L=" + clientMessage.getLogicalTimestamp() +
                            ", Host L after update=" + hostClock.getCurrentTime() +
                            "): " + clientMessage.getMessageType() + " Payload: " + clientMessage.getPayload().toString());
                    processClientMessage(clientMessage);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection reset")) { System.out.println("Host: Client " + (playerId != null ? playerId : clientGameEventSocket.getRemoteSocketAddress()) + " disconnected (reset).");
            } else if (e.getMessage() != null && (e.getMessage().equalsIgnoreCase("Socket closed") || e.getMessage().contains("Socket closed")) || e instanceof java.io.EOFException) { System.out.println("Host: Client " + (playerId != null ? playerId : clientGameEventSocket.getRemoteSocketAddress()) + " disconnected (EOF/closed).");
            } else { System.err.println("Host: Error in ClientHandler for " + (playerId != null ? playerId : clientGameEventSocket.getRemoteSocketAddress()) + ": " + e.getMessage()); }
        } finally {
            cleanup();
        }
    }

    // ... (processClientMessage, sendMessage, cleanup, ConnectionAcceptedPayload - ensure these are complete and use outStream/inStream)
    private void processClientMessage(Message message) {
        switch (message.getMessageType()) {
            case ACTION_REQUEST:
                if (message.getPayload() instanceof Action) {
                    Action action = (Action) message.getPayload();
                    if (!action.getPlayerId().equals(message.getSenderId())) { System.err.println("Host: Mismatch! Action playerId ("+action.getPlayerId()+") != message senderId ("+message.getSenderId()+")"); return; }
                    if ("IN_PROGRESS".equals(gameState.getGameStatus())) eventQueue.addIncomingAction(action);
                    else System.out.println("Host: Action request from " + playerId + " ignored. Game status: " + gameState.getGameStatus());
                } else System.err.println("Host: Received ACTION_REQUEST with invalid payload from " + playerId);
                break;
            case ACK_TO_HOST:
                if (message.getPayload() instanceof String) eventQueue.receiveAck((String) message.getPayload(), message.getSenderId());
                else System.err.println("Host: Received ACK_TO_HOST with invalid payload from " + playerId);
                break;
            case CLIENT_DISCONNECT:
                System.out.println("Host: Client " + playerId + " sent disconnect message. Closing socket.");
                try { if (!clientGameEventSocket.isClosed()) clientGameEventSocket.close(); }
                catch (IOException e) { System.err.println("Host: Error closing socket for " + playerId + ": " + e.getMessage());}
                break;
            default: System.out.println("Host: Received unhandled message type " + message.getMessageType() + " from " + playerId);
        }
    }

    public void sendMessage(Message message) {
        if (outStream != null && !clientGameEventSocket.isClosed()) {
            try { outStream.writeObject(message); outStream.flush(); outStream.reset(); }
            catch (IOException e) { System.err.println("Host: Failed to send message to " + playerId + ": " + e.getMessage()); cleanup(); }
        }
    }

    private void cleanup() {
        if (playerId != null) {
            System.out.println("Host: Cleaning up for client: " + playerId);
            hostServer.removeClient(this);
            synchronized (gameState) {
                if (gameState.getPlayer(playerId).isPresent()) {
                    gameState.removePlayer(playerId);
                    System.out.println("Host: Player " + playerId + " removed from game state.");
                    hostServer.broadcastPlayerLeft(playerId, gameState.deepCopy());
                } else System.out.println("Host: Player " + playerId + " already removed or not fully added.");
            }
            this.associatedPlayer = null;
        } else System.out.println("Host: Cleaning up uninitialized client: " + clientGameEventSocket.getRemoteSocketAddress());
        try { if (inStream != null) inStream.close(); } catch (IOException e) { /* ignore */ }
        try { if (outStream != null) outStream.close(); } catch (IOException e) { /* ignore */ }
        try { if (clientGameEventSocket != null && !clientGameEventSocket.isClosed()) clientGameEventSocket.close(); } catch (IOException e) { /* ignore */ }
        System.out.println("Host: Client " + (playerId != null ? playerId : clientGameEventSocket.getRemoteSocketAddress()) + " resources released.");
    }

    // Static inner class for the payload of CONNECTION_ACCEPTED message sent over socket
    public static class ConnectionAcceptedPayload implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final String assignedPlayerId;
        public final GameState initialGameState;
        public ConnectionAcceptedPayload(String assignedPlayerId, GameState initialGameState) {
            this.assignedPlayerId = assignedPlayerId;
            this.initialGameState = initialGameState;
        }
    }
}
