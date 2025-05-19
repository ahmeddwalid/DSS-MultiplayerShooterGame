package main.java.core;

import main.java.util.Direction;

/**
 * Represents a move action performed by a player.
 */
public class MoveAction extends Action {
    private static final long serialVersionUID = 1L;

    private final Direction direction;

    /**
     * Constructs a MoveAction.
     * @param playerId The ID of the player moving.
     * @param logicalTimestamp The logical timestamp of this action.
     * @param actionId Unique ID for this action.
     * @param direction The direction of the move.
     */
    public MoveAction(String playerId, long logicalTimestamp, String actionId, Direction direction) {
        super(playerId, logicalTimestamp, actionId);
        this.direction = direction;
    }

    public Direction getDirection() {
        return direction;
    }

    @Override
    public String getDescription() {
        return playerId + " MOVES " + direction;
    }

    @Override
    public String toString() {
        return super.toString() + ", direction=" + direction;
    }
}
