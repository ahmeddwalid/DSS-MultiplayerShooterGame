package main.java.rmi;

import main.java.core.Action;
import main.java.core.GameState; // For initial state in response

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI Remote interface for the HostServer.
 * Clients use this for initial interaction, like joining the game.
 */
public interface IGameHostService extends Remote {

    /**
     * Allows a client to request to join the game.
     * The client might provide some identifying information.
     * The host responds with necessary details for the client to fully connect
     * (e.g., assigned Player ID, port for socket communication, initial game state).
     * This method demonstrates "Nodes initialization and interaction" and "Parameter Passing" via RMI.
     *
     * @param clientRequest Data object containing information from the client (e.g., client's IP for logging).
     * @return PlayerJoinResponse Data object containing the assigned player ID, host's IP and port for socket communication, and initial game state.
     * @throws RemoteException If an RMI communication error occurs.
     */
    PlayerJoinResponse requestToJoinGame(PlayerJoinRequest clientRequest) throws RemoteException;

    /**
     * (Optional RMI Action Submission - demonstrates parameter passing for actions)
     * Allows a client to submit a game action to the host via RMI.
     * The primary mechanism for actions in this project remains sockets for real-time events,
     * but this method shows how RMI could be used.
     *
     * @param action The Action object (e.g., ShootAction, HealAction) performed by the client.
     * @throws RemoteException If an RMI communication error occurs.
     */
    void submitPlayerAction(Action action) throws RemoteException;
}
