package main.java.core;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a 2D position on the game grid.
 * Implements Serializable to be part of network messages if needed directly.
 */
public class Position implements Serializable {
    private static final long serialVersionUID = 1L; // For Serializable version control

    private int x;
    private int y;

    /**
     * Constructs a new Position.
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     */
    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // Getters
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    // Setters could be added if positions are mutable after creation,
    // but often immutable positions are safer for game state.
    // For this project, let's allow modification for simplicity in Player.setPosition
    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }


    /**
     * Calculates the Euclidean distance to another position.
     * @param other The other position.
     * @return The Euclidean distance.
     */
    public double distanceTo(Position other) {
        if (other == null) {
            return Double.MAX_VALUE; // Or throw IllegalArgumentException
        }
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return x == position.x && y == position.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
