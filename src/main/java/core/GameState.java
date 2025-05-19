package main.java.core;

import main.java.logic.GameGrid;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the overall state of the game.
 * This includes all players, their health, positions, etc.
 * Implements Serializable to be sent over the network (e.g., for new clients or full updates).
 */
public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;

    // Using ConcurrentHashMap for thread-safe access if GameState is modified by multiple threads on host.
    private final Map<String, Player> players;
    private final GameGrid grid; // Contains grid dimensions and utility methods
    private long hostLogicalClock; // Host's logical clock, reflects the "time" of this state snapshot

    // Potentially track game status, like "WAITING_FOR_PLAYERS", "IN_PROGRESS", "FINISHED"
    private String gameStatus;
    private String winnerPlayerId;


    public GameState(int gridSize) {
        this.players = new ConcurrentHashMap<>();
        this.grid = new GameGrid(gridSize);
        this.hostLogicalClock = 0;
        this.gameStatus = "INITIALIZING";
    }

    // Player management
    public void addPlayer(Player player) {
        if (player != null) {
            players.put(player.getPlayerId(), player);
        }
    }

    public Optional<Player> getPlayer(String playerId) {
        return Optional.ofNullable(players.get(playerId));
    }

    public void removePlayer(String playerId) {
        players.remove(playerId);
    }

    public void updatePlayer(Player player) {
        if (player != null && players.containsKey(player.getPlayerId())) {
            players.put(player.getPlayerId(), player); // Overwrites existing player with updated state
        }
    }

    /**
     * Returns an unmodifiable view of the players map.
     * This is safer than returning the original map.
     * @return An unmodifiable map of player IDs to Player objects.
     */
    public Map<String, Player> getPlayers() {
        return Collections.unmodifiableMap(players);
    }

    // Grid access
    public GameGrid getGrid() {
        return grid;
    }

    // Host logical clock
    public long getHostLogicalClock() {
        return hostLogicalClock;
    }

    public void setHostLogicalClock(long hostLogicalClock) {
        this.hostLogicalClock = hostLogicalClock;
    }

    public void incrementHostLogicalClock() {
        this.hostLogicalClock++;
    }

    // Game Status
    public String getGameStatus() {
        return gameStatus;
    }

    public void setGameStatus(String gameStatus) {
        this.gameStatus = gameStatus;
    }

    public String getWinnerPlayerId() {
        return winnerPlayerId;
    }

    public void setWinnerPlayerId(String winnerPlayerId) {
        this.winnerPlayerId = winnerPlayerId;
    }


    /**
     * Creates a deep copy of the game state for safe sharing or modification.
     * This is important if you want to pass around a snapshot that won't be affected
     * by concurrent modifications to the original GameState object.
     * Note: PublicKey and Certificate in Player are currently transient and won't be deep copied by this method.
     * If they need to be part of the serialized state, their handling needs to be adjusted.
     */
    public GameState deepCopy() {
        GameState copy = new GameState(this.grid.getSize());
        copy.hostLogicalClock = this.hostLogicalClock;
        copy.gameStatus = this.gameStatus;
        copy.winnerPlayerId = this.winnerPlayerId;

        for (Map.Entry<String, Player> entry : this.players.entrySet()) {
            Player originalPlayer = entry.getValue();
            // Create a new Player object for the copy
            Player copiedPlayer = new Player(originalPlayer.getPlayerId(),
                    new Position(originalPlayer.getPosition().getX(), originalPlayer.getPosition().getY()),
                    originalPlayer.getHealth());
            copiedPlayer.setIpAddress(originalPlayer.getIpAddress());
            copiedPlayer.setPort(originalPlayer.getPort());
            // Note: PublicKey and Certificate are transient in Player and won't be copied here.
            // If they need to be part of the state, this needs more careful handling.
            copy.addPlayer(copiedPlayer);
        }
        return copy;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("GameState{")
                .append("hostLogicalClock=").append(hostLogicalClock)
                .append(", gameStatus='").append(gameStatus).append('\'');
        if (winnerPlayerId != null) {
            sb.append(", winner='").append(winnerPlayerId).append('\'');
        }
        sb.append(", players=[\n");
        players.values().forEach(p -> sb.append("  ").append(p.toString()).append("\n"));
        sb.append("]}");
        return sb.toString();
    }
}
