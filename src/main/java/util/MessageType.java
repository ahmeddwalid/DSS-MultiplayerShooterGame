package main.java.util;

import java.io.Serializable;

/**
 * Defines the types of messages that can be exchanged between host and clients.
 */
public enum MessageType implements Serializable {
    // Client to Host
    ACTION_REQUEST,         // A player wants to perform an action (shoot, heal, move)
    CLIENT_CONNECT,         // A new client is trying to connect
    CLIENT_DISCONNECT,      // A client is disconnecting
    ACK_TO_HOST,            // Client acknowledges a message from the host (e.g., for TO-Multicast)

    // Host to Client(s)
    GAME_STATE_UPDATE,      // Host sends updated game state to all clients
    ACTION_VALIDATION,      // Host informs client if an action was valid/invalid (optional, could be part of GameStateUpdate)
    EVENT_NOTIFICATION,     // Host notifies clients about an event that needs ACK (part of TO-Multicast)
    PLAYER_JOINED,          // Host informs clients that a new player has joined (includes IP map update)
    PLAYER_LEFT,            // Host informs clients that a player has left
    CONNECTION_ACCEPTED,    // Host confirms connection to a client, possibly with initial game state/ID
    CONNECTION_REJECTED,    // Host rejects a client connection (e.g., game full)
    HOST_ELECTION_MESSAGE,  // For Raft algorithm (VoteRequest, AppendEntries)
    ERROR_MESSAGE,          // General error message

    // Security related
    CERTIFICATE_SHARE,      // For sharing certificates

    // Add other message types as needed
    HEARTBEAT_HOST_TO_CLIENT, // Host sends heartbeat to clients
    HEARTBEAT_CLIENT_TO_HOST  // Client responds to heartbeat or sends its own
}
