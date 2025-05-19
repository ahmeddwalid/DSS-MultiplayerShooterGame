package main.java.network; // Or your project's equivalent package for network classes

// Core game object imports
import main.java.core.*;

// Logic components
import main.java.logic.LogicalClock;

// RMI specific imports
import main.java.rmi.IGameHostService;    // The remote interface for the host's RMI service
import main.java.rmi.PlayerJoinRequest;  // Data Transfer Object for sending join request parameters
import main.java.rmi.PlayerJoinResponse; // Data Transfer Object for receiving join response parameters

// Utility imports
import main.java.util.Constants;    // For game constants like default ports
import main.java.util.Direction;    // Enum for movement directions
import main.java.util.MessageType;  // Enum for types of messages sent over sockets

// Standard Java IO and Networking imports
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;         // To get the client's own IP address
import java.net.Socket;              // For socket-based game event communication
import java.net.UnknownHostException;  // For handling errors with host IP resolution

// RMI specific imports for registry lookup
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

// Standard Java utility imports
import java.util.Map;               // For iterating over players in GameState
import java.util.Scanner;           // For reading user input from the console
import java.util.UUID;              // For generating unique action IDs

/**
 * Represents the client-side application for a player in the multiplayer shooter game.
 * This class handles:
 * 1. Initial connection to the HostServer via RMI to register and get game details.
 * 2. Subsequent socket-based communication with the HostServer for real-time game events.
 * 3. User input for game actions (move, shoot, heal).
 * 4. Displaying the game state to the user.
 * 5. Managing its own logical clock for timestamping actions.
 */
public class ClientNode {
    // --- Configuration for RMI Connection ---
    private String rmiHostIp;           // IP address of the HostServer's RMI registry
    private int rmiHostPort;            // Port number of the HostServer's RMI registry

    // --- Configuration for Socket Connection (details obtained via RMI) ---
    private String gameServerSocketIp;  // IP address of the HostServer's game event socket listener
    private int gameServerSocketPort;   // Port number of the HostServer's game event socket listener

    // --- Network Resources for Socket Communication ---
    private Socket gameEventSocket;         // The socket connection to the host for game events
    private ObjectOutputStream outStream;   // Output stream for sending Message objects to the host via socket
    private ObjectInputStream inStream;    // Input stream for receiving Message objects from the host via socket

    // --- Player and Game State ---
    private String playerId;                // Unique ID assigned to this client by the HostServer (via RMI)
    private GameState currentGameState;     // Local copy of the current game state, updated from host messages
    private final LogicalClock logicalClock; // This client's Lamport logical clock

    // --- User Input ---
    private final Scanner userInputScanner; // Scanner to read commands from the console

    // --- Control Flag ---
    private volatile boolean isRunning = true; // Flag to control the main loops (input and listening)

    /**
     * Constructor for ClientNode.
     * Initializes RMI connection parameters, logical clock, and user input scanner.
     *
     * @param rmiHostIp   The IP address of the host's RMI registry.
     * @param rmiHostPort The port number of the host's RMI registry.
     */
    public ClientNode(String rmiHostIp, int rmiHostPort) {
        this.rmiHostIp = rmiHostIp;
        this.rmiHostPort = rmiHostPort;
        this.logicalClock = new LogicalClock(); // Each client has its own clock
        this.userInputScanner = new Scanner(System.in); // For console input
    }

    /**
     * Starts the client node.
     * This method orchestrates the two-phase connection:
     * 1. RMI call to the host to join the game and get necessary details.
     * 2. Socket connection to the host for ongoing game event communication.
     * It then starts a listening thread for server messages and a loop for user input.
     */
    public void start() {
        try {
            // --- Step 1: RMI Interaction for Joining the Game ---
            // This demonstrates "Nodes initialization and interaction" using RMI.
            System.out.println("Client: Attempting RMI lookup for GameHostService at " + rmiHostIp + ":" + rmiHostPort + "...");
            // Get a reference to the RMI registry running on the host.
            Registry registry = LocateRegistry.getRegistry(rmiHostIp, rmiHostPort);
            // Look up the remote object (HostServer's service) by its bound name.
            IGameHostService gameHostService = (IGameHostService) registry.lookup("GameHostService");
            System.out.println("Client: RMI GameHostService remote object obtained.");

            // Prepare the request payload for the RMI call.
            // This demonstrates "Parameter Passing" from client to host via RMI.
            String clientOwnIpForRequest = InetAddress.getLocalHost().getHostAddress(); // Get client's own IP
            PlayerJoinRequest joinRequest = new PlayerJoinRequest(clientOwnIpForRequest);
            System.out.println("Client: Sending RMI requestToJoinGame with client IP: " + clientOwnIpForRequest);
            // Make the remote method call.
            PlayerJoinResponse joinResponse = gameHostService.requestToJoinGame(joinRequest);

            // Process the response from the RMI call.
            // This demonstrates "Parameter Passing" from host to client via RMI.
            if (joinResponse == null) {
                System.err.println("Client: RMI join request returned null. Cannot proceed.");
                shutdown();
                return;
            }

            // Store the details received from the host.
            this.playerId = joinResponse.getAssignedPlayerId();
            this.gameServerSocketIp = joinResponse.getHostIpForSocket();   // Host's IP for the game socket
            this.gameServerSocketPort = joinResponse.getHostSocketPort(); // Host's port for the game socket
            this.currentGameState = joinResponse.getInitialGameState();   // Initial snapshot of the game

            System.out.println("Client (RMI): Successfully joined. Player ID: " + this.playerId);
            System.out.println("Client (RMI): Host provided game event socket: " + this.gameServerSocketIp + ":" + this.gameServerSocketPort);
            System.out.println("Client (RMI): Initial GameState received via RMI.");

            // Validate critical information from RMI response.
            if (this.playerId == null || this.gameServerSocketIp == null || this.currentGameState == null) {
                System.err.println("Client: RMI join response missing critical data (PlayerID, Socket IP/Port, or GameState). Shutting down.");
                shutdown();
                return;
            }
            displayGameState(); // Show the initial state to the player.

            // --- Step 2: Socket Connection for Game Events ---
            // Using the IP and port received via RMI, establish a TCP socket connection.
            System.out.println("Client: Attempting socket connection to game event server at " + gameServerSocketIp + ":" + gameServerSocketPort + "...");
            this.gameEventSocket = new Socket(gameServerSocketIp, gameServerSocketPort);
            // Setup object streams for sending/receiving Message objects over the socket.
            this.outStream = new ObjectOutputStream(gameEventSocket.getOutputStream());
            this.inStream = new ObjectInputStream(gameEventSocket.getInputStream());
            System.out.println("Client: Game event socket connection established.");

            // --- Step 3: Identify this Client over the Socket ---
            // Send the first message over the socket to tell the host which player this socket belongs to.
            // The PlayerID was assigned via RMI.
            logicalClock.tick(); // Tick clock before first socket send
            Message clientIdentificationMsg = new Message(
                    MessageType.CLIENT_CONNECT, // Type indicating this is an identification message
                    this.playerId,              // Payload is the PlayerID
                    this.playerId,              // Sender is this client
                    logicalClock.getCurrentTime() // Timestamped with client's logical clock
            );
            this.outStream.writeObject(clientIdentificationMsg);
            this.outStream.flush(); // Ensure the message is sent immediately
            System.out.println("Client: Sent client identification (PlayerID: " + this.playerId + ") over game event socket.");

            // --- Step 4: Expect Socket Connection Confirmation from Host ---
            // The host's ClientHandler, upon receiving the CLIENT_CONNECT message,
            // should associate the socket with the player and send back a confirmation.
            Message socketConfirmationMsg = (Message) this.inStream.readObject(); // Wait for host's confirmation
            if (socketConfirmationMsg.getMessageType() == MessageType.CONNECTION_ACCEPTED) {
                System.out.println("Client: Game event socket connection confirmed by host. Payload: " + socketConfirmationMsg.getPayload());
                logicalClock.update(socketConfirmationMsg.getLogicalTimestamp()); // Update clock from host's message

                // The payload of CONNECTION_ACCEPTED might contain an updated GameState.
                if (socketConfirmationMsg.getPayload() instanceof ClientHandler.ConnectionAcceptedPayload) {
                    ClientHandler.ConnectionAcceptedPayload acceptedPayload = (ClientHandler.ConnectionAcceptedPayload) socketConfirmationMsg.getPayload();
                    // Sanity check: PlayerID in socket confirmation should match RMI-assigned ID.
                    if (!this.playerId.equals(acceptedPayload.assignedPlayerId)) {
                        System.err.println("Client: PlayerID mismatch! RMI_ID=" + this.playerId + ", Socket_Confirm_ID=" + acceptedPayload.assignedPlayerId);
                        // This would indicate a problem in the host's logic.
                    }
                    this.currentGameState = acceptedPayload.initialGameState; // Update state if provided
                    displayGameState();
                }
            } else {
                // If not CONNECTION_ACCEPTED, it's unexpected. Log and proceed, but it might indicate issues.
                System.err.println("Client: Did not receive expected CONNECTION_ACCEPTED over socket. Received: " + socketConfirmationMsg.getMessageType() + ". Proceeding with caution.");
            }

            // --- Step 5: Start Game Event Listening and User Input Threads ---
            // Start a new thread to continuously listen for messages from the host over the socket.
            Thread listeningThread = new Thread(this::listenToServer);
            listeningThread.setDaemon(true); // Set as daemon so it doesn't prevent JVM exit
            listeningThread.start();

            // Start handling user input in the main thread (or current thread).
            handleUserInput();

        } catch (Exception e) { // Catch RMI, IO, ClassNotFound, etc.
            System.err.println("Client: Critical error during RMI/socket setup or game loop: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for debugging
            shutdown(); // Attempt graceful shutdown
        }
    }

    /**
     * Dedicated thread method to listen for incoming messages from the HostServer over the game event socket.
     * Updates the local game state and logical clock based on received messages.
     */
    private void listenToServer() {
        try {
            // Loop as long as the client is running and the socket is valid and open.
            while (isRunning && gameEventSocket != null && !gameEventSocket.isClosed()) {
                // Read the next Message object from the input stream (blocks until one arrives).
                Message serverMessage = (Message) inStream.readObject();
                if (serverMessage != null) {
                    // Update this client's logical clock based on the host's message timestamp.
                    logicalClock.update(serverMessage.getLogicalTimestamp());
                    System.out.println("\nClient: Received message from Host (Host L=" + serverMessage.getLogicalTimestamp() +
                            ", Client L after update=" + logicalClock.getCurrentTime() +
                            "): " + serverMessage.getMessageType());

                    // Process the received message based on its type.
                    processServerMessage(serverMessage);

                    // Re-display the input prompt unless it was just an EVENT_NOTIFICATION (ACK request),
                    // as the user might be in the middle of typing a command.
                    if (serverMessage.getMessageType() != MessageType.EVENT_NOTIFICATION) {
                        displayPrompt();
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // Handle network errors or issues deserializing messages.
            if (isRunning) { // Only log if the error wasn't due to an intentional shutdown.
                if (e.getMessage() != null && e.getMessage().contains("Connection reset")) {
                    System.err.println("\nClient: Connection to host lost (connection reset).");
                } else if (e.getMessage() != null &&
                        (e.getMessage().equalsIgnoreCase("Socket closed") || e.getMessage().contains("Socket closed")) ||
                        e instanceof java.io.EOFException) {
                    System.err.println("\nClient: Connection to host closed (EOF or socket closed).");
                } else {
                    System.err.println("\nClient: Error listening to server: " + e.getMessage());
                    // e.printStackTrace(); // For debugging
                }
                isRunning = false; // Signal other loops (like userInput) to stop.
            }
        } finally {
            System.out.println("Client: Server listening thread stopped.");
            isRunning = false; // Ensure isRunning is false if this thread exits.
        }
    }

    /**
     * Processes messages received from the HostServer based on their MessageType.
     * @param message The Message object received from the host.
     */
    private void processServerMessage(Message message) {
        switch (message.getMessageType()) {
            case GAME_STATE_UPDATE:
                // Host sent an update to the overall game state.
                if (message.getPayload() instanceof GameState) {
                    currentGameState = (GameState) message.getPayload();
                    System.out.println("Client: Game State Updated.");
                    displayGameState(); // Refresh the local display
                    checkIfGameOver();  // Check if the update resulted in game over
                }
                break;
            case EVENT_NOTIFICATION:
                // Host is notifying this client about an action from another player that needs acknowledgment.
                if (message.getPayload() instanceof Action) {
                    Action actionToAck = (Action) message.getPayload();
                    System.out.println("Client: Received EVENT_NOTIFICATION for action: " + actionToAck.getDescription() +
                            " (ID: " + actionToAck.getActionId() + ")");
                    sendAckToHost(actionToAck.getActionId()); // Send ACK back to host
                }
                break;
            case PLAYER_JOINED:
                // Host is informing that a new player (or this player initially) has joined.
                if (message.getPayload() instanceof HostServer.PlayerJoinedPayload) {
                    HostServer.PlayerJoinedPayload payload = (HostServer.PlayerJoinedPayload) message.getPayload();
                    currentGameState = payload.updatedGameState; // Update local state with the one from payload
                    System.out.println("Client: Player " + payload.joinedPlayerId + " has joined the game!");
                    displayGameState();
                }
                break;
            case PLAYER_LEFT:
                // Host is informing that a player has left.
                if (message.getPayload() instanceof HostServer.PlayerLeftPayload) {
                    HostServer.PlayerLeftPayload payload = (HostServer.PlayerLeftPayload) message.getPayload();
                    currentGameState = payload.updatedGameState; // Update local state
                    System.out.println("Client: Player " + payload.leftPlayerId + " has left the game.");
                    displayGameState();
                }
                break;
            case CONNECTION_REJECTED: // Host rejected the connection (could be RMI or socket phase)
                System.err.println("Client: Connection REJECTED by host. Reason: " +
                        (message.getPayload() instanceof String ? message.getPayload() : "Unknown reason"));
                isRunning = false; // Stop the client
                break;
            default:
                // Handle any other message types if defined.
                System.out.println("Client: Received unhandled message type from Host: " + message.getMessageType());
        }
    }

    /**
     * Checks the current game state to see if the game has finished.
     * If so, prints a game over message and sets the client to stop running.
     */
    private void checkIfGameOver() {
        if (currentGameState != null && "FINISHED".equals(currentGameState.getGameStatus())) {
            System.out.println("================ GAME OVER ================");
            if (currentGameState.getWinnerPlayerId() != null) {
                if (currentGameState.getWinnerPlayerId().equals("DRAW")) {
                    System.out.println("The game is a DRAW!");
                } else if (currentGameState.getWinnerPlayerId().equals(this.playerId)) {
                    System.out.println("Congratulations! YOU ARE THE WINNER!");
                } else {
                    System.out.println("Player " + currentGameState.getWinnerPlayerId() + " is the winner!");
                }
            } else {
                // This case should ideally not happen if host sets winner to "DRAW" or a player ID.
                System.out.println("The game has finished. No specific winner declared.");
            }
            System.out.println("==========================================");
            isRunning = false; // Stop the client loops
        }
    }

    /**
     * Handles user input from the console in a loop.
     * Parses commands and sends corresponding actions to the server.
     */
    private void handleUserInput() {
        System.out.println("\nClient: Type 'help' for commands.");
        if (isRunning) displayPrompt(); // Show initial prompt

        // Loop as long as the client is running and there's input from the user.
        while (isRunning && userInputScanner.hasNextLine()) {
            if (!isRunning) break; // Double-check running flag
            String input = userInputScanner.nextLine().trim();
            if (!isRunning) break; // Check again after reading input

            // Handle quit/exit commands.
            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                isRunning = false;
                break;
            }
            // Prevent sending actions if game is already over.
            if (currentGameState != null && "FINISHED".equals(currentGameState.getGameStatus())) {
                System.out.println("Client: Game is over. No more actions can be sent.");
                isRunning = false;
                break;
            }

            // Parse the input into an Action object.
            Action action = parseInputAction(input); // 'input' still has original casing for parameters
            if (action != null) {
                // If parsing was successful, send the action to the server via socket.
                sendMessageToServer(MessageType.ACTION_REQUEST, action);

                // --- Optional: Demonstrate sending action via RMI ---
                // This section can be uncommented to show RMI being used for action submission
                // as an alternative or additional way to fulfill "Parameter Passing" via RMI.
                // For the current project structure, sending actions via socket is the primary path.
                /*
                try {
                    System.out.println("Client: Attempting to send action via RMI...");
                    Registry registry = LocateRegistry.getRegistry(rmiHostIp, rmiHostPort);
                    IGameHostService gameHostService = (IGameHostService) registry.lookup("GameHostService");
                    // The 'action' object already has its logicalTimestamp set by parseInputAction
                    // based on the client's clock *before* this RMI call.
                    gameHostService.submitPlayerAction(action);
                    System.out.println("Client: Action submitted via RMI.");
                } catch (Exception e) {
                    System.err.println("Client: Error sending action via RMI: " + e.getMessage());
                    // Decide on fallback or error display
                }
                */
            } else if (!input.isEmpty() && !input.equalsIgnoreCase("help") && !input.equalsIgnoreCase("grid")) {
                // If input was not empty but didn't parse to a known action (and wasn't help/grid)
                System.out.println("Client: Invalid command or parameters. Type 'help' for options.");
            }
            if (isRunning) displayPrompt(); // Re-display prompt for next command
        }
        System.out.println("Client: User input handling stopped.");
        if(isRunning) isRunning = false; // Ensure isRunning is false if loop exits for other reasons
    }

    /**
     * Parses the raw user input string into a specific Action object.
     * @param input The raw string command from the user.
     * @return An Action subclass instance (ShootAction, HealAction, MoveAction) or null if parsing fails.
     */
    private Action parseInputAction(String input) {
        String[] parts = input.split("\\s+"); // Split input by any whitespace
        if (parts.length == 0 || parts[0].isEmpty()) return null; // No command

        String command = parts[0].toLowerCase(); // Command is case-insensitive (shoot, heal, move)

        // PlayerID must be set (i.e., client successfully joined via RMI) to create actions.
        String actionPlayerId = this.playerId;
        if (actionPlayerId == null) {
            System.err.println("Client: PlayerID not yet assigned. Cannot create action. Ensure RMI join was successful.");
            return null;
        }
        // Generate a unique ID for this specific action instance (for tie-breaking on host).
        String actionId = actionPlayerId + "_" + UUID.randomUUID().toString().substring(0, 8);

        try {
            switch (command) {
                case "shoot":
                    if (parts.length == 2) {
                        String targetId = parts[1]; // Target ID is case-sensitive
                        // Optional: Client-side check if target exists (host will validate definitively)
                        if (currentGameState != null && currentGameState.getPlayer(targetId).isEmpty()){
                            System.out.println("Client: Target player '" + targetId + "' not found in current game state (client view).");
                            return null; // Prevent sending if client knows target is invalid
                        }
                        // Create ShootAction with current logical time
                        return new ShootAction(actionPlayerId, logicalClock.getCurrentTime(), actionId, targetId);
                    }
                    break;
                case "heal":
                    if (parts.length == 2) {
                        String targetId = parts[1]; // Target ID is case-sensitive
                        if (currentGameState != null && currentGameState.getPlayer(targetId).isEmpty()){
                            System.out.println("Client: Target player '" + targetId + "' not found in current game state (client view).");
                            return null;
                        }
                        return new HealAction(actionPlayerId, logicalClock.getCurrentTime(), actionId, targetId);
                    }
                    break;
                case "move":
                    if (parts.length == 2) {
                        // Direction parsing handles case-insensitivity.
                        Direction dir = Direction.fromString(parts[1].toUpperCase());
                        if (dir != null) {
                            return new MoveAction(actionPlayerId, logicalClock.getCurrentTime(), actionId, dir);
                        } else {
                            // Direction.fromString already prints an error for invalid direction.
                            return null;
                        }
                    }
                    break;
                case "help":
                    System.out.println("Available commands:");
                    System.out.println("  shoot <TargetPlayerID>");
                    System.out.println("  heal <TargetPlayerID>");
                    System.out.println("  move <UP|DOWN|LEFT|RIGHT>");
                    System.out.println("  grid - Display the current game grid and player info.");
                    System.out.println("  quit - Exit the game.");
                    return null; // Not an action to send to server
                case "grid":
                    displayGameState();
                    return null; // Not an action to send to server
            }
        } catch (Exception e) {
            // Catch any unexpected errors during parsing.
            System.out.println("Client: Error parsing command '" + input + "': " + e.getMessage());
            return null;
        }
        // If command is not recognized or parameters are incorrect for recognized commands.
        // System.out.println("Client: Unknown command or incorrect parameters: '" + input + "'. Type 'help'.");
        return null;
    }

    /**
     * Sends an ACK_TO_HOST message to the server for a given action ID.
     * @param actionId The ID of the action being acknowledged.
     */
    private void sendAckToHost(String actionId) {
        System.out.println("Client: Sending ACK to Host for actionId: " + actionId);
        sendMessageToServer(MessageType.ACK_TO_HOST, actionId); // Payload is the actionId string
    }

    /**
     * Sends a generic Message object to the HostServer via the socket connection.
     * Ticks the logical clock before sending.
     * @param type The MessageType of the message.
     * @param payload The payload object of the message.
     */
    private void sendMessageToServer(MessageType type, Object payload) {
        // Ensure socket is valid and client has a PlayerID before sending.
        if (gameEventSocket == null || gameEventSocket.isClosed()){
            System.out.println("Client: Cannot send message, game event socket is closed or not initialized.");
            isRunning = false; // Assume connection is lost
            return;
        }
        if (playerId == null) {
            System.err.println("Client: PlayerID not set, cannot send message. RMI join might have failed or socket identification pending.");
            return;
        }

        logicalClock.tick(); // Increment local clock before sending
        Message message = new Message(type, payload, playerId, logicalClock.getCurrentTime());

        try {
            outStream.writeObject(message);
            outStream.flush();  // Ensure message is sent immediately
            outStream.reset();  // Important for ObjectOutputStream when sending modified objects repeatedly
        } catch (IOException e) {
            System.err.println("Client: Error sending message over socket: " + e.getMessage());
            isRunning = false; // Assume connection lost
        }
    }

    /**
     * Displays the current game state to the console in a simple text format.
     * Shows player information and a character-based grid.
     */
    private void displayGameState() {
        if (currentGameState == null) {
            System.out.println("Client: No game state information available to display.");
            return;
        }
        System.out.println("\n--- Game State (Client: " + playerId + ", Your LClock: " + logicalClock.getCurrentTime() + ") ---");
        System.out.println("Host Clock in this State: " + currentGameState.getHostLogicalClock() +
                ", Game Status: " + currentGameState.getGameStatus());
        if ("FINISHED".equals(currentGameState.getGameStatus()) && currentGameState.getWinnerPlayerId() != null) {
            System.out.println("Winner: " + currentGameState.getWinnerPlayerId());
        }

        int gridSize = Constants.GRID_SIZE;
        char[][] displayGrid = new char[gridSize][gridSize];
        // Initialize grid with empty spots
        for(int i = 0; i < gridSize; i++) {
            for(int j = 0; j < gridSize; j++) {
                displayGrid[i][j] = '.';
            }
        }

        Map<String, Player> playersMap = currentGameState.getPlayers();
        System.out.println(playersMap.isEmpty() ? "No players currently in the game." : "Players:");

        for (Player p : playersMap.values()){
            // Display player details
            char playerGridChar = p.getPlayerId().equals(this.playerId) ? 'Y' : // 'Y' for "You"
                    (p.getPlayerId().startsWith("Player") && p.getPlayerId().length() > "Player".length() ?
                            p.getPlayerId().charAt("Player".length()) : // Use the number part of "PlayerX"
                            p.getPlayerId().toUpperCase().charAt(0)); // Fallback to first char of ID

            System.out.printf("  ID: %s, Health: %3d, Position: %-7s, IP: %-15s %s %s\n",
                    p.getPlayerId(),
                    p.getHealth(),
                    p.getPosition().toString(),
                    p.getIpAddress() != null ? p.getIpAddress() : "N/A", // Handle null IP
                    (p.getPlayerId().equals(this.playerId) ? "(You)" : ""),
                    (p.isAlive() ? "" : "[DEAD x_x]")
            );
            // Place player on the display grid if alive and position is valid
            if(p.isAlive() && currentGameState.getGrid().isValidPosition(p.getPosition())){
                displayGrid[p.getPosition().getY()][p.getPosition().getX()] = playerGridChar;
            }
        }

        // Print the grid
        System.out.println("\nGame Grid (" + gridSize + "x" + gridSize + "):");
        for(int i = 0; i < gridSize; i++){
            System.out.print("  "); // Indentation for grid rows
            for(int j = 0; j < gridSize; j++) {
                System.out.print(displayGrid[i][j] + " ");
            }
            System.out.println();
        }
        System.out.println("------------------------------------");
    }

    /**
     * Displays the input prompt for the user.
     */
    private void displayPrompt() {
        if (isRunning) { // Only show prompt if client is supposed to be running
            System.out.print("\n" + (playerId != null ? playerId : "Client") + "> ");
        }
    }

    /**
     * Shuts down the client node, closing network resources and stopping loops.
     */
    public void shutdown() {
        System.out.println("Client: Shutting down...");
        boolean previouslyRunning = isRunning;
        isRunning = false; // Signal all loops to terminate

        // Attempt to send a graceful disconnect message if client was running and connected
        if (previouslyRunning && gameEventSocket != null && !gameEventSocket.isClosed() && outStream != null && playerId != null) {
            try {
                System.out.println("Client: Attempting to send disconnect message (socket)...");
                logicalClock.tick();
                Message disconnectMsg = new Message(MessageType.CLIENT_DISCONNECT, "Client initiated disconnect", playerId, logicalClock.getCurrentTime());
                outStream.writeObject(disconnectMsg);
                outStream.flush();
                outStream.reset();
            } catch (IOException e) {
                System.err.println("Client: Error sending socket disconnect message: " + e.getMessage());
            }
        }

        // Close network resources, ignoring errors during shutdown
        try { if (outStream != null) outStream.close(); } catch (IOException e) { /* ignore */ }
        try { if (inStream != null) inStream.close(); } catch (IOException e) { /* ignore */ }
        try { if (gameEventSocket != null && !gameEventSocket.isClosed()) gameEventSocket.close(); } catch (IOException e) { /* ignore */ }

        // Closing System.in (userInputScanner) can cause issues in IDEs if multiple clients share console.
        // Rely on isRunning flag to stop the input loop.
        // if (userInputScanner != null) userInputScanner.close();

        System.out.println("Client: Disconnected and resources released.");
    }

    /**
     * Main method for launching the ClientNode.
     * Expects RMI host IP and RMI port as command-line arguments.
     * Defaults to localhost:1099 if arguments are not provided.
     * @param args Command-line arguments: args[0] = RMI Host IP, args[1] = RMI Host Port.
     */
    public static void main(String[] args) {
        String rmiHostArg = "localhost"; // Default RMI host
        int rmiPortArg = 1099;         // Default RMI registry port

        if (args.length >= 2) {
            rmiHostArg = args[0];
            try {
                rmiPortArg = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid RMI port number provided: '" + args[1] + "'. Using default RMI port " + rmiPortArg);
            }
        } else if (args.length == 1) {
            rmiHostArg = args[0];
            System.out.println("RMI port not specified, using default " + rmiPortArg + " for host " + rmiHostArg);
        } else {
            System.out.println("Usage: java main.java.network.ClientNode <RMI_host_ip> <RMI_host_port>");
            System.out.println("Defaulting to RMI host: " + rmiHostArg + " and RMI port: " + rmiPortArg);
        }

        ClientNode client = new ClientNode(rmiHostArg, rmiPortArg);
        // Add a shutdown hook for graceful termination (e.g., on Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (client.isRunning) { // Check if already shutting down
                System.out.println("Client: Shutdown hook activated.");
                client.shutdown();
            }
        }));

        client.start(); // Start the client logic

        // Main thread might finish here, but the client (listening thread, input loop) continues
        // until isRunning becomes false.
        System.out.println("Client: Main method finished. Client application might still be running via its threads.");
    }
}
