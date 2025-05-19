package main.java.rmi;

import main.java.core.GameState; // To send initial game state
import java.io.Serializable;

/**
 * Data Transfer Object (DTO) sent by the host in response to a client's join request via RMI.
 * This demonstrates parameter passing from host to client.
 */
public class PlayerJoinResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String assignedPlayerId;
    private final String hostIpForSocket;   // The IP address the client should use for the subsequent socket connection
    private final int hostSocketPort;       // The port number the client should use for socket communication
    private final GameState initialGameState; // The initial state of the game

    public PlayerJoinResponse(String assignedPlayerId, String hostIpForSocket, int hostSocketPort, GameState initialGameState) {
        this.assignedPlayerId = assignedPlayerId;
        this.hostIpForSocket = hostIpForSocket;
        this.hostSocketPort = hostSocketPort;
        this.initialGameState = initialGameState;
    }

    public String getAssignedPlayerId() {
        return assignedPlayerId;
    }

    public String getHostIpForSocket() {
        return hostIpForSocket;
    }

    public int getHostSocketPort() {
        return hostSocketPort;
    }

    public GameState getInitialGameState() {
        return initialGameState;
    }
}
