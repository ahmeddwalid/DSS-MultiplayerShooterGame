package main.java.logic;

import main.java.core.Player;
import main.java.core.Position;
import main.java.util.Direction;

import java.io.Serializable;

/**
 * Represents the game grid and provides utility functions related to it.
 * This class itself might not need to be serialized if its state is simple (like just size)
 * or derived. Player positions are stored in Player objects within GameState.
 */
public class GameGrid implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int size;

    public GameGrid(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }

    /**
     * Checks if a given position is within the bounds of the grid.
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @return true if the position is valid, false otherwise.
     */
    public boolean isValidPosition(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size;
    }

    /**
     * Checks if a given Position object is within the bounds of the grid.
     * @param position The Position object to check.
     * @return true if the position is valid, false otherwise.
     */
    public boolean isValidPosition(Position position) {
        if (position == null) return false;
        return isValidPosition(position.getX(), position.getY());
    }

    /**
     * Calculates the Euclidean distance between two players.
     * @param player1 The first player.
     * @param player2 The second player.
     * @return The Euclidean distance, or Double.MAX_VALUE if inputs are invalid.
     */
    public double getDistance(Player player1, Player player2) {
        if (player1 == null || player2 == null || player1.getPosition() == null || player2.getPosition() == null) {
            return Double.MAX_VALUE; // Or throw an exception
        }
        return player1.getPosition().distanceTo(player2.getPosition());
    }

    /**
     * Calculates the Euclidean distance between two positions.
     * @param pos1 The first position.
     * @param pos2 The second position.
     * @return The Euclidean distance.
     */
    public double getDistance(Position pos1, Position pos2) {
        if (pos1 == null || pos2 == null) {
            return Double.MAX_VALUE;
        }
        return pos1.distanceTo(pos2);
    }

    /**
     * Calculates the new position after a move in a given direction.
     * Does not validate if the new position is within grid bounds here;
     * that should be checked by GameLogicProcessor.
     * @param currentPosition The current position.
     * @param direction The direction to move.
     * @return The new Position.
     */
    public Position calculateNewPosition(Position currentPosition, Direction direction) {
        if (currentPosition == null || direction == null) {
            throw new IllegalArgumentException("Position and Direction cannot be null.");
        }
        int newX = currentPosition.getX();
        int newY = currentPosition.getY();

        switch (direction) {
            case UP:
                newY--; // Assuming (0,0) is top-left, so UP decreases Y
                break;
            case DOWN:
                newY++; // DOWN increases Y
                break;
            case LEFT:
                newX--; // LEFT decreases X
                break;
            case RIGHT:
                newX++; // RIGHT increases X
                break;
        }
        return new Position(newX, newY);
    }
}
