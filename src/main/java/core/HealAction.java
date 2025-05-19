package main.java.core;

/**
 * Represents a heal action performed by a player.
 */
public class HealAction extends Action {
    private static final long serialVersionUID = 1L;

    private final String targetPlayerId; // Can be self or another player

    /**
     * Constructs a HealAction.
     * @param playerId The ID of the healer.
     * @param logicalTimestamp The logical timestamp of this action.
     * @param actionId Unique ID for this action.
     * @param targetPlayerId The ID of the player being healed.
     */
    public HealAction(String playerId, long logicalTimestamp, String actionId, String targetPlayerId) {
        super(playerId, logicalTimestamp, actionId);
        this.targetPlayerId = targetPlayerId;
    }

    public String getTargetPlayerId() {
        return targetPlayerId;
    }

    @Override
    public String getDescription() {
        return playerId + " HEALS " + targetPlayerId;
    }

    @Override
    public String toString() {
        return super.toString() + ", target='" + targetPlayerId + '\'';
    }
}
