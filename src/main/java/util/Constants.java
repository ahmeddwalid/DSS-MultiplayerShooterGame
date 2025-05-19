package main.java.util;

/**
 * Constants used throughout the game application.
 * This includes game rules, network settings, etc.
 */
public class Constants {

    // Game Grid
    public static final int GRID_SIZE = 10; // The game grid is 10x10

    // Player
    public static final int MAX_HEALTH = 100;
    public static final int MIN_HEALTH = 0;

    // Actions
    public static final int SHOOT_DAMAGE = 10; // Damage dealt by a shoot action
    public static final int HEAL_AMOUNT = 10;  // Health restored by a heal action
    public static final double MAX_ACTION_DISTANCE = 3.0; // Max Euclidean distance for shoot/heal

    // Network
    public static final int HOST_PORT = 12345; // Default port for the host server
    // Default timeout for waiting for ACKs or host responses (in milliseconds)
    public static final int DEFAULT_TIMEOUT_MS = 5000;


    // Security (Placeholders for key management)
    public static final String CA_PUBLIC_KEY_FILE = "ca_public_key.pem"; // Example
    public static final String KEY_ALGORITHM = "RSA";
    public static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    public static final String SYMMETRIC_KEY_ALGORITHM = "AES";
    public static final int SYMMETRIC_KEY_SIZE = 128; // bits

    // Raft (Placeholders for Raft implementation)
    public static final int ELECTION_TIMEOUT_MIN_MS = 150;
    public static final int ELECTION_TIMEOUT_MAX_MS = 300;
    public static final int HEARTBEAT_INTERVAL_MS = 50;

    // Add other constants as needed
}
