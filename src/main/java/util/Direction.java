package main.java.util;

import java.io.Serializable;

/**
 * Represents the possible directions for a move action.
 */
public enum Direction implements Serializable {
    UP,
    DOWN,
    LEFT,
    RIGHT;

    /**
     * Parses a string to a Direction enum. Case-insensitive.
     * @param input The string input (e.g., "UP", "down").
     * @return The corresponding Direction, or null if no match.
     */
    public static Direction fromString(String input) { // Corrected method name to lowercase 'f'
        if (input == null) {
            return null;
        }
        try {
            return Direction.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid direction string: " + input);
            return null;
        }
    }
}
