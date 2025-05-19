package main.java.core;

/**
 * Represents a shoot action performed by a player.
 */
public class ShootAction extends Action {
    private static final long serialVersionUID = 1L;

    private final String targetPlayerId;

    /**
     * Constructs a ShootAction.
     * @param playerId The ID of the shooter.
     * @param logicalTimestamp The logical timestamp of this action.
     * @param actionId Unique ID for this action.
     * @param targetPlayerId The ID of the player being targeted.
     */
    public ShootAction(String playerId, long logicalTimestamp, String actionId, String targetPlayerId) {
        super(playerId, logicalTimestamp, actionId);
        this.targetPlayerId = targetPlayerId;
    }

    public String getTargetPlayerId() {
        return targetPlayerId;
    }

    @Override
    public String getDescription() {
        return playerId + " SHOOTS " + targetPlayerId;
    }

    @Override
    public String toString() {
        return super.toString() + ", target='" + targetPlayerId + '\'';
    }
}
