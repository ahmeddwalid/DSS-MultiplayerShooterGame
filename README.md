
<div id="top"></div>  

<h3 align="center">Distributed Multiplayer Shooter Game</h3>

  <p align="center">  
    Distributed Systems Security  
    <br />  
 <a href="https://github.com/DSS-MultiplayerShooterGame"><strong>Explore the docs »</strong></a>  
 <br /> <br /> <a href="https://github.com/DSS-MultiplayerShooterGame">Report Bug</a>  
    ·  
    <a href="https://github.com/DSS-MultiplayerShooterGame">Request Feature</a>  
  </p>  
</div>  

## 1. Project Overview

### 1.1. Purpose
This project implements a multiplayer shooter game designed to demonstrate key concepts in distributed systems. The application allows one player to host a game session, becoming both the server and "Player1", while other players can join this session as clients. Initial client registration and discovery of game server details are handled via Java RMI, fulfilling requirements for "Nodes initialization and interaction" and "Parameter Passing" using RMI. Subsequent real-time game events, such as player actions and state updates, are managed through Java Sockets for efficiency. The system incorporates logical clocks for event ordering and an adapted total order multicast protocol for action processing over the socket channel.

### 1.2. Core Features Implemented
* **Player-as-Host Model**: The application can be launched to either create a new game (acting as host and Player1) or join an existing game.
* **Hybrid Communication Model**:
    * **Java RMI**: Utilized for the initial handshake where joining clients discover the host and receive their player ID and socket connection details.
    * **Java Sockets**: Employed for all continuous real-time game event communication after the RMI-based initialization.
* **Multiplayer Gameplay**: Supports multiple clients connecting to the player-hosted game.
* **Player Actions**: Players can shoot, heal, and move within the game.
* **2D Grid Environment**: Gameplay takes place on a 10x10 grid.
* **Logical Clocks**: Lamport logical clocks timestamp socket-based messages for event ordering.
* **Event Tie-Breaking**: A defined mechanism orders actions with identical logical timestamps received via sockets.
* **Adapted Total Order Multicast (TO-Multicast)**: Implemented for actions sent over sockets to ensure consistent processing order.
* **Authoritative Host**: The host player's application validates all actions and manages the true game state, preventing client-side cheating.
* **Sequential Player Naming**: Players are assigned IDs like "Player1", "Player2", etc., by the host.

### 1.3. Technologies Used
* **Remote Invocation**: Java RMI (`java.rmi.*`) for initial client-host registration and parameter exchange.
* **Network Sockets**: Java Sockets (`java.net.Socket`, `java.net.ServerSocket`) for TCP/IP based real-time game event communication.
* **Object Serialization**: `java.io.ObjectInputStream` and `java.io.ObjectOutputStream` for transmitting Java objects.
* **Concurrency**: Java Threads and `ExecutorService` for managing host services and client communication.

## 2. System Architecture

The game architecture revolves around a player initiating a game session and thus becoming the **Host**. Other players join this host.

### 2.1. Application Startup and Role Selection (`Main.java`)
* The application starts with `com.shootergamemultiplayer.Main.java`.
* A console menu prompts the user:
    1.  **Create a new game**: The user's application instance takes on host responsibilities.
    2.  **Join an existing game**: The user's application instance acts as a client to an existing host.

### 2.2. Hosting a Game
If "Create Game" is selected:
1. The `Main` application instantiates `HostServer`.
2.  `HostServer` logic (including RMI service setup and socket listener) is started in a background thread. This instance of `HostServer` is *the* game server.
3. The `Main` application then immediately instantiates a `ClientNode` locally. This `ClientNode` represents the host player's own game client.
4. This local `ClientNode` connects to its own `HostServer` logic (running in the same application process) using `localhost` for RMI lookup and subsequently for the socket connection. It effectively becomes "Player1".
5. The application displays the host's actual IP address for other remote players to use when they choose "Join Game".

### 2.3. Joining a Game (Client)
If "Join Game" is selected:
1. The `Main` application prompts the user for the Host's IP address and RMI port.
2. It then instantiates a `ClientNode`.
3. This `ClientNode` uses the provided details to perform an RMI lookup and join the game hosted by another player.

### 2.4. Two-Phase Connection for Joining Clients
1.  **Phase 1: RMI for Initialization & Discovery**
* The `HostServer` (running in the host player's application) exports an RMI service implementing `IGameHostService`.
    * A joining `ClientNode` looks up this RMI service using the host's IP and RMI port.
    * The client calls the `requestToJoinGame(PlayerJoinRequest)` remote method.
        * **Parameter Passing (Client->Host)**: `PlayerJoinRequest` carries info like the client's IP (for the host to log).
    * The host's RMI method:
        * Assigns a unique `PlayerID` (e.g., "Player2", "Player3").
        * Returns a `PlayerJoinResponse`.
            * **Parameter Passing (Host->Client)**: `PlayerJoinResponse` contains the `PlayerID`, the host's IP and *socket port* for game events, and an initial `GameState`.
2.  **Phase 2: Sockets for Real-time Game Events**
* The joining `ClientNode` uses the socket IP and port from the RMI response to establish a TCP socket connection to the `HostServer`'s game event listener.
    * The first message sent by the client over this socket is a `CLIENT_CONNECT` message containing its RMI-assigned `PlayerID`.
    * The `ClientHandler` on the host side receives this, identifies the client, creates the full `Player` object in the `GameState`, and may send a `CONNECTION_ACCEPTED` confirmation over the socket.
    * All further game communication (actions, ACKs, state updates) occurs over this socket connection.

## 3. Directory and Package Structure

* **`com.shootergamemultiplayer`** (Root package for launcher):
    * `Main.java`
* **`com.shootergamemultiplayer.core`**: `Player.java`, `Action.java`, `ShootAction.java`, `HealAction.java`, `MoveAction.java`, `Position.java`, `GameGrid.java`, `GameState.java`.
* **`com.shootergamemultiplayer.util`**: `Constants.java`, `Direction.java`, `MessageType.java`.
* **`com.shootergamemultiplayer.logic`**: `LogicalClock.java`, `EventQueue.java`, `GameLogicProcessor.java`.
* **`com.shootergamemultiplayer.network`**: `Message.java`, `HostServer.java`, `ClientHandler.java`, `ClientNode.java`.
* **`com.shootergamemultiplayer.rmi`**: `IGameHostService.java`, `PlayerJoinRequest.java`, `PlayerJoinResponse.java`.
* **`com.shootergamemultiplayer.security`**: `EncryptionUtil.java`.

## 4. Detailed Class Descriptions
## 2. The Main Entry Point: `Main.java`

* **Package**: `com.shootergamemultiplayer` (or your root package)
* **Purpose**: This is the first piece of code that runs when anyone starts the game application. It's like the main menu.
* **How it Works**:
    1. It prints a welcome message and asks the user if they want to:
        * **"1. Create a new game"**: If chosen, this player becomes the host.
        * **"2. Join an existing game"**: If chosen, this player will be a client joining someone else's game.
    2.  **If "Create Game"**:
        * It asks for the RMI port and game socket port the host should use (with defaults).
        * It creates an instance of `HostServer` (our server logic) and starts it in a **new background thread**. This is important so the server can run without freezing up the host player's own game.
        * It prints the host's IP address and RMI port, which the host player needs to tell their friends.
        * After a short pause (to let the server initialize), it creates an instance of `ClientNode` (our client logic) for the host player themselves. This `ClientNode` is configured to connect to `localhost` (its own computer) because the server is running on the same machine.
        * The `hostPlayerClient.start()` call then takes over the main thread, running the game for the host player.
    3.  **If "Join Game"**:
        * It asks the user for the IP address and RMI port of the game they want to join (the details their friend, the host, gave them).
        * It then creates an instance of `ClientNode` configured with these remote host details.
        * `clientNode.start()` then runs the game for this joining player.

## 3. The RMI Layer: Initial Connection (`com.shootergamemultiplayer.rmi`)

This package contains the parts needed for the RMI "handshake."

* **`IGameHostService.java` (The RMI Contract)**
* **Purpose**: This is an `interface` that defines what remote "functions" or "services" the host's game will offer to joining clients. Think of it as a list of services a business offers.
    * **Key Method**:
        * `PlayerJoinResponse requestToJoinGame(PlayerJoinRequest clientRequest)`: This is the main service. A client calls this to ask to join.
            * It takes a `PlayerJoinRequest` object (data from the client).
            * It returns a `PlayerJoinResponse` object (data from the host back to the client).
        * `void submitPlayerAction(Action action)`: An optional method showing how actions *could* be sent via RMI. In our current setup, actions primarily go through sockets after the initial join.
* It extends `java.rmi.Remote`, marking it as an RMI interface.

* **`PlayerJoinRequest.java` (Client's Info to Host)**
* **Purpose**: A simple data container (a "Data Transfer Object" or DTO) that the client sends to the host when calling `requestToJoinGame`.
    * **Contents**: `clientIpForSocket` (String) - The client tells the host its IP, mainly for the host to log or be aware of.
* It must implement `java.io.Serializable` so RMI can send it over the network.

* **`PlayerJoinResponse.java` (Host's Info to Client)**
* **Purpose**: Another DTO, this one is sent back from the host to the client as the result of the `requestToJoinGame` call.
    * **Contents**:
        * `assignedPlayerId` (String): The ID the host gives to the new player (e.g., "Player2").
        * `hostIpForSocket` (String): The IP address the client should use for the *next step* – connecting via Sockets.
        * `hostSocketPort` (int): The port number for the socket connection.
        * `initialGameState` (`GameState`): A snapshot of the game when the player joins.
* Also `Serializable`.

## 4. The Host's Brain: `HostServer.java` (`com.shootergamemultiplayer.network`)

This class is the heart of the server-side logic when a player chooses to host.

* **Dual Role**:
    1.  **RMI Service Provider**: It `implements IGameHostService`, meaning it provides the actual code for the `requestToJoinGame` method that clients call remotely.
    2.  **Socket Server**: It also runs a `ServerSocket` to listen for and manage the direct socket connections from clients for ongoing game communication.

* **Constructor (`HostServer(int socketPort, int rmiPort)`)**:
    * Takes the port numbers for its socket listener and its RMI service.
    * Initializes core components:  
      * `LogicalClock`: The host's own clock.  
      * `GameState`: The one true record of everything in the game.  
      * `EventQueue`: To manage incoming actions from clients (via sockets).  
      * `GameLogicProcessor`: To process actions and update the game state.
        * Thread pools for handling multiple clients and background tasks.
    * It also has an `AtomicInteger playerNumberCounter` to generate "Player1", "Player2", etc.

* **`start()` Method (Key Steps)**:
    1.  **RMI Setup**:
        * `UnicastRemoteObject.exportObject(this, 0)`: Makes this `HostServer` instance capable of receiving remote RMI calls.
        * `LocateRegistry.createRegistry(rmiPort)`: Starts an RMI registry on the specified `rmiPort` if one isn't already running. The registry is like a phonebook for RMI services.
        * `registry.bind("GameHostService", stub)`: Registers this host's service in the RMI registry under the name "GameHostService". Clients will use this name to find it.
    2.  **Start Game Logic Threads**:
        * Starts the `GameLogicProcessor` in its own thread (so it can continuously process actions from the `EventQueue` without blocking the main server).
        * Starts the `manageActionMulticastingForAcks` method in another thread (to handle sending out notifications for ACKs).
    3.  **Start Socket Listener**:
        * Creates a `ServerSocket` that listens on the `socketPort` for incoming socket connection requests from clients (after they've done the RMI part).
        * Enters a loop (`while (!serverSocketForGameEvents.isClosed())`):
            * `serverSocketForGameEvents.accept()`: Waits for a client to try and connect via socket.
            * When a client connects, it creates a new `ClientHandler` object for that specific client's socket and submits it to a thread pool to be run.

* **`requestToJoinGame(PlayerJoinRequest clientRequest)` (RMI Method)**:
    * This is the code that runs when a client calls this method via RMI.
    * It gets the next player number (e.g., 1, then 2, etc.) and creates a `PlayerID` like "Player1".
    * It figures out its own IP address.
    * It creates a `PlayerJoinResponse` object containing:
        * The new `PlayerID`.
        * Its own IP address (for the client to connect to via socket).
        * The `socketPort` it's listening on for game events.
        * A `deepCopy()` of the current `GameState`.
    * It returns this `PlayerJoinResponse` back to the calling client.
        * **Important**: At this RMI stage, the player isn't fully "in the game" in terms of having an active `ClientHandler` or being in the `connectedClients` list. That happens when their socket connects.

* **`submitPlayerAction(Action action)` (RMI Method - Optional Path)**:
    * If a client were to send an action via RMI (the project primarily uses sockets for this), this method would receive it.
    * It would update the host's clock and add the action to the `EventQueue`, just like actions received over sockets.

* **`addClient(ClientHandler handler)`**:
    * Called by a `ClientHandler` *after* the client has connected via socket and identified itself with the PlayerID it got from RMI.
    * Adds the `ClientHandler` (which represents an active socket connection) to the `connectedClients` map.
    * If the game was `WAITING_FOR_PLAYERS` and enough players have now joined (currently 1 for testing), it changes the `gameState.gameStatus` to `IN_PROGRESS` and calls `broadcastGameState()` to tell everyone the game has started.

* **Other Key Methods**:
    * `manageActionMulticastingForAcks()`: Sends `EVENT_NOTIFICATION` messages for actions.
    * `removeClient()`: Removes a disconnected client.
    * `broadcastGameState()`, `broadcastPlayerJoined()`, `broadcastPlayerLeft()`: Send updates to all connected (socket) clients.
    * `stop()`: Tries to clean up resources.

## 5. The Client's Brain: `ClientNode.java` (`com.shootergamemultiplayer.network`)

This class handles everything for a player who is playing the game, whether they are the host player or a joining player.

* **Constructor (`ClientNode(String rmiHostIp, int rmiHostPort)`)**:
    * Takes the RMI host's IP and port. These are `localhost` and the chosen RMI port if this client is for the host player, or the remote host's details if joining.
    * Initializes its own `LogicalClock` and `Scanner` for user input.

* **`start()` Method (Key Steps - The Two-Phase Connection)**:
    1.  **RMI Join Phase**:
        * `LocateRegistry.getRegistry(...)` and `registry.lookup("GameHostService")`: Connects to the RMI registry on the host and gets a "stub" (a local representative) of the remote `IGameHostService`.
        * `PlayerJoinRequest joinRequest = new PlayerJoinRequest(...)`: Creates the data object to send with the join request.
        * `PlayerJoinResponse joinResponse = gameHostService.requestToJoinGame(joinRequest)`: Makes the actual RMI call. This is a blocking call; the client waits here until the host responds.
        * It then unpacks the `joinResponse` to get its assigned `playerId`, the `gameServerSocketIp` and `gameServerSocketPort` (where the host is listening for game events via sockets), and the `initialGameState`.
    2.  **Socket Connection Phase**:
        * `this.gameEventSocket = new Socket(gameServerSocketIp, gameServerSocketPort)`: Creates a new TCP socket connection to the host's game event server.
        * `this.outStream = new ObjectOutputStream(...)`, `this.inStream = new ObjectInputStream(...)`: Sets up streams to send and receive Java `Message` objects over this socket.
    3.  **Socket Identification**:
        * The client ticks its `logicalClock`.
        * It creates a `Message` of type `CLIENT_CONNECT`. The *payload* of this message is its `playerId` (the one it just got from RMI).
        * It sends this identification message to the host over the newly established socket. This tells the `ClientHandler` on the host side who this socket connection belongs to.
    4.  **Socket Confirmation**:
        * It waits to receive a `Message` back from the host over the socket. It expects this to be a `CONNECTION_ACCEPTED` message, confirming the socket link is established and recognized. The payload of this might contain an updated `GameState`.
    5.  **Start Game Loops**:
        * `new Thread(this::listenToServer).start()`: Starts a new background thread that will continuously listen for messages (like game updates, ACK requests) from the host over the socket.
        * `handleUserInput()`: Starts a loop in the current thread to read commands from the player via the console.

* **`listenToServer()`**:
    * Runs in a loop, blocking on `inStream.readObject()` until a `Message` arrives from the host.
    * When a message is received, it updates its `logicalClock` using the timestamp from the host's message.
    * Calls `processServerMessage()` to handle the message.

* **`processServerMessage(Message message)`**:
    * A `switch` statement based on `message.getMessageType()`:  
      * `GAME_STATE_UPDATE`: Updates `this.currentGameState` with the payload and calls `displayGameState()` and `checkIfGameOver()`.  
      * `EVENT_NOTIFICATION`: The host is asking for an ACK. The payload is the `Action` that needs ACKing. The client calls `sendAckToHost()` with the `actionId`.  
      * `PLAYER_JOINED` / `PLAYER_LEFT`: Updates game state and display.  
      * `CONNECTION_REJECTED`: Sets `isRunning` to false.

* **`handleUserInput()`**:
    * Runs in a loop, reading lines from the console.
    * If "quit" or "exit", sets `isRunning` to false.
    * If the game is "FINISHED", stops.
    * Calls `parseInputAction()` to try and convert the typed string into an `Action` object.
    * If an `Action` is created, it calls `sendMessageToServer()` to send it to the host (via socket).

* **`parseInputAction(String input)`**:
    * Splits the input string (e.g., "shoot Player2") into parts.
    * The command ("shoot", "heal", "move") is converted to lowercase for matching.
    * Player IDs (like "Player2") are kept with their original casing.
    * Creates the appropriate `Action` subclass (`ShootAction`, `HealAction`, `MoveAction`), timestamping it with the client's current `logicalClock.getCurrentTime()`.
    * Handles "help" and "grid" commands locally.

* **`sendMessageToServer(MessageType type, Object payload)`**:
    * Ticks the client's `logicalClock`.
    * Creates a new `Message` object with the given type, payload, its `playerId`, and the current logical clock time.
    * Writes this `Message` object to the `outStream` (to the socket).

* **`displayGameState()`**: Prints a simple text representation of the grid and player statuses to the console.
* **`shutdown()`**: Tries to send a `CLIENT_DISCONNECT` message and closes socket/streams.

## 6. Host-Side Client Management: `ClientHandler.java` (`com.shootergamemultiplayer.network`)

Each time the `HostServer` accepts a new *socket* connection for game events, it creates a `ClientHandler` instance and runs it in a new thread.

* **Constructor**: Receives the `Socket` for this specific client, and references to the `HostServer`, `hostClock`, `EventQueue`, and the shared `GameState`.
* **`run()` Method (Key Initial Steps)**:
    1.  **Expect Identification**: It waits to read the first `Message` from the client over the socket. It expects this to be a `CLIENT_CONNECT` message where the payload is the `playerId` that this client received via RMI.
    2.  **Set PlayerID and Create Player Object**:
        * It extracts the `playerId` from the received message. This `playerId` is now associated with this `ClientHandler` and its socket.
        * It then checks if a `Player` object for this `playerId` already exists in the `GameState`. (In the current logic, the RMI `requestToJoinGame` in `HostServer` *doesn't* add the player to `GameState`, only assigns the ID. So, the `ClientHandler` will typically find it doesn't exist yet).
        * It creates a new `Player` object using this `playerId`, calculates an initial position, and adds this `Player` object to the shared `GameState`. It also stores this `Player` object in its `associatedPlayer` field.
    3.  **Send Socket Confirmation**: It sends a `CONNECTION_ACCEPTED` message back to the client over the socket, confirming the socket is linked and ready for game events. This payload might include the latest `GameState`.
    4.  **Register with HostServer**: It calls `hostServer.addClient(this)`. This is important because `HostServer.addClient()` is what adds this `ClientHandler` to the `connectedClients` map (used for broadcasting) and potentially changes the game status to `IN_PROGRESS`.
    5.  **Broadcast Join**: It calls `hostServer.broadcastPlayerJoined()` to inform all clients (including this new one, though they just got a state) about this player fully joining the game session.
* **Ongoing Communication**: After setup, the `run()` method enters a loop:
    * Reads `Message` objects from its client's `inStream`.
    * Updates the `hostClock` using the timestamp from the client's message.
    * Calls `processClientMessage()` to handle the message.
* **`processClientMessage(Message message)`**:
    * A `switch` based on `message.getMessageType()`:  
      * `ACTION_REQUEST`: If the game is `IN_PROGRESS`, it passes the `Action` payload to `eventQueue.addIncomingAction()`.  
      * `ACK_TO_HOST`: Passes the `actionId` (payload) and `senderId` to `eventQueue.receiveAck()`.  
      * `CLIENT_DISCONNECT`: Closes the socket (cleanup is handled in `finally`).
* **`sendMessage(Message message)`**: Writes a `Message` to this client's `outStream`.
* **`cleanup()`**: Called when the client disconnects or an error occurs. It calls `hostServer.removeClient(this)`, removes the player from `GameState`, broadcasts `PLAYER_LEFT`, and closes socket/streams.

## 7. Core Game Logic Components (`com.shootergamemultiplayer.logic`)

These run on the host side.

* **`LogicalClock.java`**:
    * Implements Lamport's logical clock.
        * `tick()`: Increments the clock by 1 (usually called before sending a message).
        * `update(receivedTimestamp)`: Sets local clock to `max(local_current_time, received_timestamp) + 1` (called after receiving a message).

* **`EventQueue.java`**:
    * **Purpose**: Manages the queue of actions received from clients to ensure they are processed in a consistent order according to the TO-Multicast protocol.
    * `pendingActions` (`PriorityBlockingQueue<Action>`): Stores actions that have been received but are awaiting ACKs or are not yet ready for multicast. Actions are ordered by `Action.compareTo()` (logical time, then host arrival time, then action ID).
    * `actionsBeingProcessed` (`Map<String, Action>`): Stores the actual `Action` object instance by its ID, useful for direct retrieval.
    * `multicastStatus` (`Map<String, Boolean>`): Tracks if an action's `EVENT_NOTIFICATION` has been sent out.
    * `actionAcks` (`Map<String, Set<String>>`): For each `actionId`, stores the set of `playerId`s who have sent an ACK.
    * `readyToProcessActions` (`LinkedBlockingQueue<Action>`): Stores actions that have received all necessary ACKs and are ready for the `GameLogicProcessor`.
    * **Key Methods**:
        * `addIncomingAction()`: Adds a new action from a client to `pendingActions` and `actionsBeingProcessed`.
        * `getNextActionToMulticast()`: Called by `HostServer` to get the next action to send out for ACKs.
        * `markActionAsMulticast()`: Updates `multicastStatus`. If an action requires 0 ACKs (e.g., single player), this method directly moves it to `readyToProcessActions`.
        * `receiveAck()`: Records an ACK. If all required ACKs for an action are received, it moves the action from `pendingActions` to `readyToProcessActions`.
        * `getNextActionToProcess()`: Called by `GameLogicProcessor` to get the next action to execute (blocks if the ready queue is empty).

* **`GameLogicProcessor.java`**:
    * **Purpose**: Executes the actual game rules and updates the `GameState`. Runs in its own thread.
    * **`run()` method**:
        * Continuously tries to get an action from `eventQueue.getNextActionToProcess()`.
        * Calls `processAction()` for the retrieved action.
        * If `processAction()` indicates the state changed, it calls `hostServer.broadcastGameState()`.
    * **`processAction(Action action)`**:
        * Validates the actor (player performing the action).
        * Based on action type (`ShootAction`, `HealAction`, `MoveAction`):
            * Performs specific validation (e.g., distance for shoot/heal: `gameState.getGrid().getDistance(actor, target) <= Constants.MAX_ACTION_DISTANCE`).
            * Updates player health or position in the `GameState`.
            * Logs the outcome.
        * Calls `checkGameOver()`.
        * Returns `true` if the game state was modified.
    * `checkGameOver()`: Determines if only one player (or zero) remains alive and updates `gameState.gameStatus` and `winnerPlayerId` accordingly.

## 8. Core Game Entities (`com.shootergamemultiplayer.core`)

These are the main data-holding classes.

* **`GameState.java`**:
    * The central repository of all game information:  
      * `players` (Map<String, Player>): All players currently in the game.  
      * `grid` (`GameGrid`): The game board.  
      * `hostLogicalClock` (long): The host's logical clock value when this state snapshot was relevant.  
      * `gameStatus` (String): e.g., "WAITING\_FOR\_PLAYERS", "IN\_PROGRESS", "FINISHED".  
      * `winnerPlayerId` (String).
        * `deepCopy()`: Essential for creating independent snapshots of the state to send to clients, preventing issues with concurrent modifications.

* **`Player.java`**:
    * Attributes: `playerId` (String), `health` (int), `position` (`Position`), `ipAddress` (String), `port` (int).
    * Methods: `takeDamage()`, `heal()`, `isAlive()`, getters/setters.

* **`Action.java`** (Abstract):
    * Base for all actions.
    * Attributes: `playerId` (who did it), `logicalTimestamp` (when they did it, by their clock), `actionId` (unique ID like "Player1\_randomUUID"), `arrivalTimestampHost` (when host received it).
        * `compareTo()`: Defines the order for the `EventQueue` (logical time, then host arrival, then action ID).
        * `getDescription()`: Abstract method for a human-readable description.
    * Subclasses: `ShootAction` (has `targetPlayerId`), `HealAction` (has `targetPlayerId`), `MoveAction` (has `Direction`).

* **`GameGrid.java`**: Represents the 10x10 grid. Methods: `isValidPosition()`, `getDistance()`, `calculateNewPosition()`.
* **`Position.java`**: Simple (x, y) coordinate holder.

## 9. Message Structure: `Message.java` (`com.shootergamemultiplayer.network`)

* A `Serializable` wrapper for all communication over sockets.
* Attributes:
    * `messageType` (`MessageType` enum): What kind of message this is.
    * `payload` (Object): The actual data being sent (e.g., an `Action` object, a `GameState` object, a `String` for an ACK).
    * `senderId` (String): The `PlayerID` of the sender.
    * `logicalTimestamp` (long): The sender's logical clock time when the message was created.

## 10. Utilities (`com.shootergamemultiplayer.util`)

* **`Constants.java`**: Defines fixed values like `GRID_SIZE`, `MAX_HEALTH`, `HOST_PORT` for sockets.
* **`Direction.java`**: Enum `UP, DOWN, LEFT, RIGHT` with a `fromString()` helper.
* **`MessageType.java`**: Enum for all types of socket messages (e.g., `ACTION_REQUEST`, `GAME_STATE_UPDATE`, `EVENT_NOTIFICATION`, `ACK_TO_HOST`, `CLIENT_CONNECT`, `CONNECTION_ACCEPTED`).

## 11. Security Stubs (`com.shootergamemultiplayer.security`)

* **`EncryptionUtil.java`**:
    * Contains methods for AES symmetric encryption (`encrypt`, `decrypt`).
    * Initializes a predictable `sharedSymmetricKey`.
        * **Important Note**: In the current implementation state, these encryption methods are **not actively used** to encrypt/decrypt the `Message` objects or their payloads during socket transmission, nor are RMI calls explicitly encrypted by application-level code (RMI can have its own transport security, but that's not configured here). This part of the "encryption" requirement is structurally present as a utility but not operationally integrated.

## 5. Key Mechanisms Explained

### 5.1. Application Startup and Player Role Selection
The `Main.java` class serves as the unified entry point.
* **Hosting**: If "Create Game" is chosen, `Main` instantiates `HostServer`. The `HostServer`'s `start()` method is invoked in a new background thread. This initializes the RMI service and starts the socket listener. `Main` then immediately creates a `ClientNode` instance for the host player, configuring it to connect to `localhost` (its own `HostServer` instance). The host player's IP is displayed for others.
* **Joining**: If "Join Game" is chosen, `Main` prompts for the host's IP and RMI port. It then instantiates `ClientNode` with these details.

### 5.2. RMI for Node Initialization and Interaction
This addresses the "Nodes initialization and interaction" RMI rubric item.
* **Service Export & Lookup**: The `HostServer` exports itself as a remote object implementing `IGameHostService` and binds it to an RMI registry. Joining `ClientNode`s perform an RMI lookup to obtain a stub to this remote service.
* **Join Protocol**:
    1.  `ClientNode` calls `IGameHostService.requestToJoinGame(PlayerJoinRequest)`.
    2.  `HostServer` (as the service implementor) receives this call, assigns a `PlayerID`.
    3.  `HostServer` returns a `PlayerJoinResponse` containing the `PlayerID`, its own IP and *socket port* for game events, and the initial `GameState`.

### 5.3. RMI for Parameter Passing
This addresses the "Parameter Passing" RMI rubric item.
* **`PlayerJoinRequest`**: This object is passed *from* the `ClientNode` *to* the `HostServer` during the `requestToJoinGame` RMI call. It encapsulates parameters like the client's IP.
* **`PlayerJoinResponse`**: This object is returned *from* the `HostServer` *to* the `ClientNode`. It encapsulates parameters like the assigned `PlayerID`, the host's socket IP and port, and the `GameState`.
* **`Action` objects (optional RMI path)**: The `IGameHostService.submitPlayerAction(Action action)` method demonstrates passing complex `Action` objects (like `ShootAction`) as parameters directly via RMI, although the primary path for actions in this implementation remains sockets.

### 5.4. Transition from RMI to Sockets
1. After the `ClientNode` receives the `PlayerJoinResponse` via RMI, it has the necessary `PlayerID` and the host's specific IP/port for the game event socket server.
2. The `ClientNode` then establishes a standard TCP `Socket` connection to this endpoint.
3. The first `Message` the `ClientNode` sends over this newly established socket is of type `CLIENT_CONNECT`, with its RMI-assigned `PlayerID` as the payload.
4. The `ClientHandler` on the host side, upon receiving this first socket message, extracts the `PlayerID`. It uses this ID to:  
   a.  Set its own `playerId` field.  
   b.  Create the `Player` object in the shared `GameState` (or link to a placeholder if RMI created one).  
   c.  Send a `CONNECTION_ACCEPTED` message back over the socket.  
   d.  Register itself with `HostServer.addClient()`.
5. All subsequent real-time game communication (player actions, host notifications, ACKs, game state updates) occurs over this socket connection using the custom `Message` protocol.

*(Mechanisms like Logical Clocks (for socket messages), Event Ordering & Tie-Breaking (for socket actions), TO-Multicast (for socket actions), Game State Management, Action Processing, and Cheating Prevention operate as described in the previous "Comprehensive Documentation (Reverted)", but now within the context of the socket communication channel established after RMI initialization.)*
