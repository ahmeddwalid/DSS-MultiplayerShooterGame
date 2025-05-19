package main.java.network; // Or your project's equivalent package for network classes

// Core game object imports
import main.java.core.Action;
import main.java.core.GameState;
import main.java.core.Player;
import main.java.core.Position; // Though not directly used in this file, GameState uses it

// Logic components for game processing
import main.java.logic.EventQueue;
import main.java.logic.GameLogicProcessor;
import main.java.logic.LogicalClock;

// RMI specific imports
import main.java.rmi.IGameHostService;    // The remote interface this server implements
import main.java.rmi.PlayerJoinRequest;  // Data Transfer Object for RMI join request
import main.java.rmi.PlayerJoinResponse; // Data Transfer Object for RMI join response

// Utility imports
import main.java.util.Constants;    // For game constants like default ports
import main.java.util.MessageType;  // Enum for types of messages sent over sockets

// Standard Java IO and Networking imports
import java.io.IOException;
import java.net.InetAddress;         // To get the host's own IP address
import java.net.ServerSocket;        // For listening for socket-based game connections
import java.net.Socket;              // For individual client socket connections

// RMI specific imports for registry and remote object management
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject; // For exporting this class as an RMI remote object

// Standard Java utility imports
import java.util.Map;                       // For managing connected clients
import java.util.concurrent.ConcurrentHashMap; // Thread-safe map for client handlers
import java.util.concurrent.ExecutorService;    // For managing thread pools
import java.util.concurrent.Executors;          // Factory methods for ExecutorService
import java.util.concurrent.atomic.AtomicInteger; // For thread-safe sequential player ID generation

/**
 * The main server class for the Host player's application.
 * This class performs dual roles:
 * 1. Implements {@link IGameHostService} to act as an RMI service endpoint,
 * allowing new clients to join the game and get initial parameters.
 * 2. Runs a {@link ServerSocket} to listen for and manage socket connections
 * from clients for real-time game event communication (actions, state updates).
 *
 * It orchestrates the overall game flow, manages the authoritative game state,
 * and coordinates distributed operations like Total Order Multicast for actions.
 */
public class HostServer implements IGameHostService {
    // --- Network Configuration ---
    private final int socketPort; // Port number for the game event ServerSocket
    private final int rmiPort;    // Port number for the RMI registry

    // --- Network Resources ---
    private ServerSocket serverSocketForGameEvents; // Listens for client socket connections for game play

    // --- Core Game Components ---
    private final GameState gameState;          // The single, authoritative state of the game
    private final LogicalClock hostClock;       // The host's Lamport logical clock
    private final EventQueue eventQueue;        // Manages incoming actions for TO-Multicast processing
    private final GameLogicProcessor gameLogicProcessor; // Processes acknowledged actions and updates game state

    // --- Concurrency Management ---
    private final ExecutorService clientHandlerPool;  // Thread pool for managing ClientHandler threads (sockets)
    private final ExecutorService gameLogicPool;      // Thread pool for running the GameLogicProcessor
    private final ExecutorService eventMulticastPool; // Thread pool for managing action notification multicasts

    // --- Client Management ---
    // Stores active ClientHandlers, keyed by PlayerID, for socket-based communication
    private final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    // A unique identifier for messages originating from the host itself
    public static final String HOST_ID_FOR_MESSAGES = "HostServerNode";
    // Counter to generate sequential PlayerIDs (e.g., "Player1", "Player2")
    private final AtomicInteger playerNumberCounter = new AtomicInteger(1);

    /**
     * Constructor for HostServer.
     * Initializes all core components and sets up ports.
     *
     * @param socketPort The port number for the game event socket listener.
     * @param rmiPort    The port number for the RMI registry.
     */
    public HostServer(int socketPort, int rmiPort) {
        this.socketPort = socketPort;
        this.rmiPort = rmiPort;

        // Initialize core game logic components
        this.hostClock = new LogicalClock();
        this.gameState = new GameState(Constants.GRID_SIZE); // Create the initial empty game state
        // EventQueue needs a reference to connectedClients to know how many ACKs are expected
        this.eventQueue = new EventQueue(connectedClients);
        // GameLogicProcessor needs references to GameState, EventQueue, and this HostServer (to trigger broadcasts)
        this.gameLogicProcessor = new GameLogicProcessor(gameState, eventQueue, this);

        // Initialize thread pools for various concurrent tasks
        this.clientHandlerPool = Executors.newCachedThreadPool(); // Handles varying numbers of clients
        this.gameLogicPool = Executors.newSingleThreadExecutor(); // Game logic processing is sequential
        this.eventMulticastPool = Executors.newSingleThreadExecutor(); // Action notification multicasting is sequential

        System.out.println("HostServer: Instance created. Game Socket Port: " + socketPort + ", RMI Port: " + rmiPort);
    }

    /**
     * Generates the next sequential player number for assigning PlayerIDs.
     * This is called by ClientHandler when a new client connects via socket and identifies itself.
     * (Correction: In the RMI model, this is called by the RMI `requestToJoinGame` method).
     * @return The next integer for player numbering (e.g., 1, 2, 3...).
     */
    private int getNextPlayerNumber() {
        return playerNumberCounter.getAndIncrement(); // Atomically increments and returns the value
    }

    // --- IGameHostService RMI Method Implementations ---

    /**
     * RMI remote method called by clients wishing to join the game.
     * This method handles the "Nodes initialization and interaction" and "Parameter Passing"
     * requirements for RMI.
     *
     * @param clientRequest A {@link PlayerJoinRequest} object containing information from the client
     * (e.g., client's IP for host logging).
     * @return A {@link PlayerJoinResponse} object containing the assigned PlayerID for the client,
     * the IP and port where the host is listening for game event socket connections,
     * and an initial snapshot of the {@link GameState}.
     * @throws RemoteException If any RMI communication error occurs.
     */
    @Override
    public PlayerJoinResponse requestToJoinGame(PlayerJoinRequest clientRequest) throws RemoteException {
        String clientReportedIp = clientRequest.getClientIpForSocket();
        System.out.println("HostServer (RMI): Received requestToJoinGame from client (reported IP: " + clientReportedIp + ")");

        // Assign a new, unique PlayerID for this client.
        String newPlayerId = "Player" + getNextPlayerNumber();
        System.out.println("HostServer (RMI): Assigning PlayerID: " + newPlayerId + " for RMI request.");

        String hostIpForSocketConnection;
        try {
            // Get the host's own IP address to tell the client where to connect for sockets.
            hostIpForSocketConnection = InetAddress.getLocalHost().getHostAddress();
        } catch (IOException e) {
            System.err.println("HostServer (RMI): Could not get local IP address for socket response.");
            // If host can't get its IP, client can't connect via socket.
            throw new RemoteException("Server error: Could not determine own IP for socket communication.", e);
        }

        // Prepare the response. The GameState sent is a snapshot at this moment.
        // The player is not yet added to the `connectedClients` map or fully integrated
        // into the live `gameState`'s player list; that happens when their socket connects.
        synchronized (gameState) { // Synchronize for consistent deepCopy of gameState
            PlayerJoinResponse response = new PlayerJoinResponse(
                    newPlayerId,
                    hostIpForSocketConnection,
                    this.socketPort, // The port for game event sockets, NOT the RMI port
                    gameState.deepCopy() // Send a snapshot of the current game state
            );
            System.out.println("HostServer (RMI): Responding to " + newPlayerId + " with socket info: " +
                    hostIpForSocketConnection + ":" + this.socketPort);
            return response;
        }
    }

    /**
     * (Optional RMI Action Submission - primarily for demonstrating RMI parameter passing with Action objects)
     * RMI remote method that allows a client to submit a game action.
     * The main pathway for actions in this project is intended to be sockets for real-time performance.
     *
     * @param action The {@link Action} object (e.g., ShootAction, HealAction) performed by the client.
     * @throws RemoteException If any RMI communication error occurs.
     */
    @Override
    public void submitPlayerAction(Action action) throws RemoteException {
        if (action == null) {
            System.err.println("HostServer (RMI): Received null action via RMI.");
            return;
        }
        System.out.println("HostServer (RMI): Received action from " + action.getPlayerId() +
                ": " + action.getDescription() + " via RMI.");

        // Update host's logical clock based on the action's timestamp.
        // This assumes the Action object passed via RMI was correctly timestamped by the client.
        hostClock.update(action.getLogicalTimestamp());

        // Add the action to the event queue for processing, same as if received via socket.
        if ("IN_PROGRESS".equals(gameState.getGameStatus())) {
            eventQueue.addIncomingAction(action);
        } else {
            System.out.println("HostServer (RMI): Action request from " + action.getPlayerId() +
                    " (via RMI) ignored. Game status is: " + gameState.getGameStatus());
        }
    }
    // --- End of RMI Method Implementations ---

    /**
     * Gets the current count of actively connected (socket-based) clients.
     * @return The number of clients in the `connectedClients` map.
     */
    public int getConnectedClientCount() {
        return connectedClients.size();
    }

    /**
     * Starts the HostServer.
     * This involves:
     * 1. Setting up and binding the RMI service.
     * 2. Starting background threads for game logic processing and action multicasting.
     * 3. Starting the ServerSocket to listen for incoming socket connections for game events.
     */
    public void start() {
        System.out.println("HostServer: Initializing systems...");

        try {
            // 1. Setup RMI Service
            // Export this HostServer instance, making it available for remote calls.
            // Port 0 means an anonymous port will be chosen for the stub's communication.
            IGameHostService stub = (IGameHostService) UnicastRemoteObject.exportObject(this, 0);

            // Create or get a reference to the RMI registry on the specified rmiPort.
            // This is where clients will look up the service.
            Registry registry = LocateRegistry.createRegistry(rmiPort);

            // Bind the exported stub to a name in the RMI registry. Clients use this name.
            registry.bind("GameHostService", stub);
            System.out.println("HostServer: RMI GameHostService bound to name 'GameHostService' on RMI port " + rmiPort);
        } catch (Exception e) { // Catch RemoteException, AlreadyBoundException, etc.
            System.err.println("HostServer: RMI service startup critical error: " + e.getMessage());
            e.printStackTrace(); // Print full trace for debugging
            return; // Cannot continue if RMI setup fails.
        }

        // 2. Start Game Logic Background Threads
        gameState.setGameStatus("WAITING_FOR_PLAYERS"); // Initial game status

        final GameLogicProcessor currentProcessorInstance = this.gameLogicProcessor;
        if (currentProcessorInstance == null) {
            System.err.println("CRITICAL ERROR: HostServer.start() - gameLogicProcessor is null before submitting to pool! Aborting GLP start.");
            return;
        }
        System.out.println("HostServer: Submitting GameLogicProcessor to its thread pool.");
        gameLogicPool.submit(currentProcessorInstance::run); // Start the game logic processing loop

        System.out.println("HostServer: Submitting Action Multicast Manager to its thread pool.");
        eventMulticastPool.submit(this::manageActionMulticastingForAcks); // Start the action notification loop

        // 3. Start Socket Listener for Game Events
        try {
            serverSocketForGameEvents = new ServerSocket(socketPort); // Create server socket for game events
            String actualSocketIp = InetAddress.getLocalHost().getHostAddress();
            System.out.println("HostServer: Game Event Socket Listener started on IP " + actualSocketIp +
                    ", Port " + socketPort + ". Waiting for identified client socket connections...");

            // Main loop to accept incoming socket connections for game events.
            while (!serverSocketForGameEvents.isClosed()) {
                try {
                    Socket clientSocket = serverSocketForGameEvents.accept(); // Blocks until a client connects
                    System.out.println("HostServer: Accepted game event socket connection from: " + clientSocket.getRemoteSocketAddress());

                    // Create a new handler for this client's socket connection.
                    // The ClientHandler will read the RMI-assigned PlayerID from the first message
                    // sent by the client over this socket.
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this, hostClock, eventQueue, gameState);
                    clientHandlerPool.submit(clientHandler); // Run the handler in a new thread
                } catch (IOException e) {
                    if (serverSocketForGameEvents.isClosed()) {
                        System.out.println("HostServer: Game event server socket has been closed. No longer accepting connections.");
                    } else {
                        System.err.println("HostServer: Error accepting a client's game event socket connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("HostServer: Could not start game event socket server on port " + socketPort + ": " + e.getMessage());
            e.printStackTrace(); // Print full trace for debugging
        } finally {
            stop(); // Ensure server resources are cleaned up if the loop exits
        }
    }

    /**
     * Manages the process of taking actions from the EventQueue's pending list
     * and multicasting them (as EVENT_NOTIFICATION) to other clients for acknowledgment.
     * This is part of the TO-Multicast protocol. Runs in its own thread.
     */
    private void manageActionMulticastingForAcks() {
        System.out.println("HostServer: Action Multicast Manager thread started.");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // Get the next action that needs to be multicast for ACKs.
                Action actionToMulticast = eventQueue.getNextActionToMulticast();
                if (actionToMulticast != null) {
                    System.out.println("HostServer: Multicasting action " + actionToMulticast.getActionId() +
                            " ("+ actionToMulticast.getDescription() +") for ACKs.");
                    hostClock.tick(); // Tick host's clock before sending notification
                    Message ackRequestMessage = new Message(
                            MessageType.EVENT_NOTIFICATION, // Type indicating this is an ACK request
                            actionToMulticast,             // Payload is the original action
                            HOST_ID_FOR_MESSAGES,          // Sender is the host
                            hostClock.getCurrentTime()     // Timestamped with host's clock
                    );

                    int sentToCount = 0;
                    // Send this notification to all *other* connected clients.
                    for (ClientHandler handler : connectedClients.values()) {
                        // Ensure player is associated and not sending to the action originator.
                        if (handler.getAssociatedPlayer() != null &&
                                !handler.getAssociatedPlayer().getPlayerId().equals(actionToMulticast.getPlayerId())) {
                            handler.sendMessage(ackRequestMessage);
                            sentToCount++;
                        }
                    }
                    System.out.println("HostServer: Multicast for action " + actionToMulticast.getActionId() +
                            " sent to " + sentToCount + " other clients.");
                    // Mark the action as having been multicast in the EventQueue.
                    eventQueue.markActionAsMulticast(actionToMulticast.getActionId());
                } else {
                    // If no action is ready for multicast, pause briefly to avoid busy-waiting.
                    Thread.sleep(50); // Polling interval
                }
            }
        } catch (InterruptedException e) {
            System.out.println("HostServer: Action Multicast Manager thread interrupted.");
            Thread.currentThread().interrupt(); // Preserve interrupt status
        }
        System.out.println("HostServer: Action Multicast Manager thread stopped.");
    }

    /**
     * Registers a fully initialized ClientHandler (after socket identification) with the HostServer.
     * This typically happens after the ClientHandler has received the client's RMI-assigned PlayerID
     * over the socket and has created the associated Player object.
     * This method may trigger the game to start if enough players have joined.
     *
     * @param handler The ClientHandler instance for the connected client.
     */
    public void addClient(ClientHandler handler) {
        // Ensure the handler has a valid PlayerID and an associated Player object.
        if (handler.getPlayerId() != null && handler.getAssociatedPlayer() != null) {
            connectedClients.put(handler.getPlayerId(), handler); // Add to map of active socket clients
            System.out.println("HostServer: Client " + handler.getPlayerId() +
                    " (socket fully associated) registered. Total active clients: " + connectedClients.size());

            // Check if the game can now transition from WAITING_FOR_PLAYERS to IN_PROGRESS.
            if ("WAITING_FOR_PLAYERS".equals(gameState.getGameStatus())) {
                // Condition to start the game (e.g., at least 1 player for testing).
                if (connectedClients.size() >= 1) {
                    synchronized (gameState) { // Synchronize to prevent race conditions on gameStatus
                        if ("WAITING_FOR_PLAYERS".equals(gameState.getGameStatus())) { // Double-check
                            gameState.setGameStatus("IN_PROGRESS");
                            System.out.println("HostServer: Minimum players ("+ connectedClients.size() +
                                    ") reached. Game status is now IN_PROGRESS!");
                            broadcastGameState(); // Inform all clients (including new one) of the started game state.
                        }
                    }
                }
            }
        } else {
            System.err.println("HostServer: Attempt to add client handler with null playerId or uninitialized associated player object.");
        }
    }

    /**
     * Removes a client handler from the list of connected clients, typically when a client disconnects.
     * @param handler The ClientHandler of the disconnected client.
     */
    public void removeClient(ClientHandler handler) {
        if (handler.getPlayerId() != null) {
            ClientHandler removedHandler = connectedClients.remove(handler.getPlayerId());
            if (removedHandler != null) {
                System.out.println("HostServer: Client " + handler.getPlayerId() + " removed from active list. Total clients: " + connectedClients.size());
            } else {
                System.out.println("HostServer: Attempted to remove client " + handler.getPlayerId() + " but they were not in the active list.");
            }
        }
    }

    /**
     * Broadcasts the current (deep-copied) game state to all actively connected (socket) clients.
     * Called after actions are processed or significant game events occur.
     */
    public void broadcastGameState() {
        synchronized (gameState) { // Ensure a consistent snapshot is sent
            hostClock.tick(); // Tick host's clock before broadcasting state
            gameState.setHostLogicalClock(hostClock.getCurrentTime()); // Stamp the state with host's current logical time

            Message gameStateMessage = new Message(
                    MessageType.GAME_STATE_UPDATE,
                    gameState.deepCopy(), // Send a copy to prevent modification issues and ensure snapshot
                    HOST_ID_FOR_MESSAGES,
                    hostClock.getCurrentTime()
            );
            System.out.println("HostServer: Broadcasting GameState Update (Host L=" + hostClock.getCurrentTime() +
                    ", Status: " + gameState.getGameStatus() + ")");
            for (ClientHandler client : connectedClients.values()) {
                client.sendMessage(gameStateMessage);
            }
        }
    }

    /**
     * Broadcasts a notification that a new player has successfully joined the game (socket connected and identified).
     * @param newPlayer The Player object of the newly joined player.
     * @param currentGameState A snapshot of the game state at the time of joining.
     */
    public void broadcastPlayerJoined(Player newPlayer, GameState currentGameState) {
        hostClock.tick();
        Message joinMessage = new Message(
                MessageType.PLAYER_JOINED,
                new PlayerJoinedPayload(newPlayer.getPlayerId(), currentGameState.deepCopy()), // Payload includes ID and state
                HOST_ID_FOR_MESSAGES,
                hostClock.getCurrentTime()
        );
        System.out.println("HostServer: Broadcasting PLAYER_JOINED for " + newPlayer.getPlayerId());
        for (ClientHandler client : connectedClients.values()) {
            client.sendMessage(joinMessage);
        }
    }

    /**
     * Broadcasts a notification that a player has left the game.
     * @param leftPlayerId The ID of the player who left.
     * @param currentGameState A snapshot of the game state after the player left.
     */
    public void broadcastPlayerLeft(String leftPlayerId, GameState currentGameState) {
        hostClock.tick();
        Message leftMessage = new Message(
                MessageType.PLAYER_LEFT,
                new PlayerLeftPayload(leftPlayerId, currentGameState.deepCopy()), // Payload includes ID and state
                HOST_ID_FOR_MESSAGES,
                hostClock.getCurrentTime()
        );
        System.out.println("HostServer: Broadcasting PLAYER_LEFT for " + leftPlayerId);
        for (ClientHandler client : connectedClients.values()) {
            client.sendMessage(leftMessage);
        }
    }

    /**
     * Stops the HostServer, closing network resources and shutting down thread pools.
     */
    public void stop() {
        System.out.println("HostServer: Stopping all services...");
        try {
            // Close the game event server socket
            if (serverSocketForGameEvents != null && !serverSocketForGameEvents.isClosed()) {
                serverSocketForGameEvents.close();
                System.out.println("HostServer: Game Event ServerSocket closed.");
            }
            // Unbind RMI service and unexport object (important for clean RMI shutdown)
            try {
                Registry registry = LocateRegistry.getRegistry(rmiPort);
                registry.unbind("GameHostService"); // Unbind by the name used
                UnicastRemoteObject.unexportObject(this, true); // true to force unexport
                System.out.println("HostServer: RMI service unbound and unexported.");
            } catch (Exception e) {
                System.err.println("HostServer: Error during RMI service unbind/unexport: " + e.getMessage());
            }
        } catch (IOException e) {
            System.err.println("HostServer: Error closing game event server socket: " + e.getMessage());
        }

        // Shutdown all thread pools
        clientHandlerPool.shutdownNow();
        gameLogicPool.shutdownNow();
        eventMulticastPool.shutdownNow();
        System.out.println("HostServer: All thread pools shut down. HostServer stopped.");
    }

    /**
     * Main method to start the HostServer.
     * Called by {@code Main.java} when a player chooses to "Create Game".
     * This specific main method in HostServer itself might not be directly used if Main.java is the sole entry point,
     * but it's kept here for potential direct testing or alternative launching.
     * It now expects RMI port and optionally game socket port as arguments.
     * @param args Command-line arguments: args[0] = RMI Port (optional), args[1] = Game Socket Port (optional).
     */
    public static void main(String[] args) {
        int gameSockPortArg = Constants.HOST_PORT; // Default from Constants
        int rmiRegPortArg = 1099;         // Default RMI Registry port

        if (args.length > 0) {
            try {
                rmiRegPortArg = Integer.parseInt(args[0]);
                System.out.println("HostServer main: Using RMI Registry port from argument: " + rmiRegPortArg);
            } catch (NumberFormatException e) {
                System.err.println("Invalid RMI port argument: '" + args[0] + "'. Using default " + rmiRegPortArg);
            }
        }
        if (args.length > 1) {
            try {
                gameSockPortArg = Integer.parseInt(args[1]);
                System.out.println("HostServer main: Using Game Socket port from argument: " + gameSockPortArg);
            } catch (NumberFormatException e) {
                System.err.println("Invalid Game Socket port argument: '" + args[1] + "'. Using default " + gameSockPortArg);
            }
        }

        HostServer server = new HostServer(gameSockPortArg, rmiRegPortArg);
        // Add a shutdown hook for graceful termination (e.g., on Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start(); // Start the RMI service and socket listener
    }

    // --- Static Inner Classes for Message Payloads ---
    /**
     * Payload for the PLAYER_JOINED message.
     */
    public static class PlayerJoinedPayload implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final String joinedPlayerId;
        public final GameState updatedGameState; // Includes the new player

        public PlayerJoinedPayload(String joinedPlayerId, GameState updatedGameState) {
            this.joinedPlayerId = joinedPlayerId;
            this.updatedGameState = updatedGameState;
        }
    }

    /**
     * Payload for the PLAYER_LEFT message.
     */
    public static class PlayerLeftPayload implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final String leftPlayerId;
        public final GameState updatedGameState; // Reflects player removal

        public PlayerLeftPayload(String leftPlayerId, GameState updatedGameState) {
            this.leftPlayerId = leftPlayerId;
            this.updatedGameState = updatedGameState;
        }
    }
}
