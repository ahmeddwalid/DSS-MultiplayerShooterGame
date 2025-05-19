package main.java.core;

import main.java.util.Constants;
import java.io.Serializable;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Objects;

/**
 * Represents a player in the game.
 * Implements Serializable to be part of the GameState sent over the network.
 */
public class Player implements Serializable {
    private static final long serialVersionUID = 1L; // For Serializable version control

    private final String playerId; // Unique identifier for the player
    private int health;
    private Position position; // Current position on the grid

    // For bonus TLS/SSL imitation (can be transient if not serialized directly, or handle serialization carefully)
    private transient PublicKey publicKey; // Player's public key
    private transient Certificate certificate; // Player's certificate

    private String ipAddress; // Player's IP address, managed by host
    private int port; // Player's port, if needed for direct P2P or specific client listening

    /**
     * Constructs a new Player.
     * @param playerId Unique ID for the player.
     * @param initialPosition The starting position of the player.
     * @param initialHealth The starting health of the player.
     */
    public Player(String playerId, Position initialPosition, int initialHealth) {
        this.playerId = playerId;
        this.position = initialPosition;
        this.health = Math.min(Constants.MAX_HEALTH, Math.max(Constants.MIN_HEALTH, initialHealth));
    }

    // Getters
    public String getPlayerId() {
        return playerId;
    }

    public int getHealth() {
        return health;
    }

    public Position getPosition() {
        return position;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    // Setters
    public void setHealth(int health) {
        this.health = Math.min(Constants.MAX_HEALTH, Math.max(Constants.MIN_HEALTH, health));
    }

    public void setPosition(Position position) {
        // Add validation if necessary (e.g., within grid bounds),
        // though host should primarily validate moves.
        this.position = position;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setPort(int port) {
        this.port = port;
    }


    // Game logic methods
    /**
     * Applies damage to the player. Health will not go below MIN_HEALTH.
     * @param amount The amount of damage to take.
     */
    public void takeDamage(int amount) {
        if (amount > 0) {
            this.health = Math.max(Constants.MIN_HEALTH, this.health - amount);
        }
    }

    /**
     * Heals the player. Health will not go above MAX_HEALTH.
     * @param amount The amount of health to restore.
     */
    public void heal(int amount) {
        if (amount > 0) {
            this.health = Math.min(Constants.MAX_HEALTH, this.health + amount);
        }
    }

    /**
     * Checks if the player is alive.
     * @return true if health is greater than MIN_HEALTH, false otherwise.
     */
    public boolean isAlive() {
        return this.health > Constants.MIN_HEALTH;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(playerId, player.playerId); // Players are unique by ID
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }

    @Override
    public String toString() {
        return "Player{" +
                "playerId='" + playerId + '\'' +
                ", health=" + health +
                ", position=" + position +
                (isAlive() ? "" : ", status=DEFEATED") +
                '}';
    }
}
