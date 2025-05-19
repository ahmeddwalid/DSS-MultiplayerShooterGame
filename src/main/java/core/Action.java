package main.java.core;

import java.io.Serializable;

/**
 * Abstract base class for all player actions.
 * Actions are serializable to be sent over the network.
 */
public abstract class Action implements Serializable, Comparable<Action> {
    private static final long serialVersionUID = 1L;

    protected final String playerId;      // ID of the player initiating the action
    protected final long logicalTimestamp; // Logical clock value of the sender at the time of sending
    protected final String actionId;       // Unique ID for this action instance (for tie-breaking)
    protected long arrivalTimestampHost; // Physical time of arrival at host (set by host)

    /**
     * Constructor for an Action.
     * @param playerId The ID of the player performing the action.
     * @param logicalTimestamp The logical timestamp of the action.
     * @param actionId A unique identifier for this specific action.
     */
    public Action(String playerId, long logicalTimestamp, String actionId) {
        this.playerId = playerId;
        this.logicalTimestamp = logicalTimestamp;
        this.actionId = actionId;
    }

    // Getters
    public String getPlayerId() {
        return playerId;
    }

    public long getLogicalTimestamp() {
        return logicalTimestamp;
    }

    public String getActionId() {
        return actionId;
    }

    public long getArrivalTimestampHost() {
        return arrivalTimestampHost;
    }

    // Setter for host to record arrival time
    public void setArrivalTimestampHost(long arrivalTimestampHost) {
        this.arrivalTimestampHost = arrivalTimestampHost;
    }

    /**
     * Compares this action with another action for ordering in the event queue.
     * Implements the tie-breaking logic:
     * 1. Logical Timestamp (L)
     * 2. Arrival Time at Host (T)
     * 3. Action ID (or Player ID if action IDs are not globally unique enough)
     */
    @Override
    public int compareTo(Action other) {
        // Compare by logical timestamp
        if (this.logicalTimestamp < other.logicalTimestamp) return -1;
        if (this.logicalTimestamp > other.logicalTimestamp) return 1;

        // Logical timestamps are equal, compare by arrival time at host
        if (this.arrivalTimestampHost < other.arrivalTimestampHost) return -1;
        if (this.arrivalTimestampHost > other.arrivalTimestampHost) return 1;

        // Arrival times are equal, compare by actionId (or playerId as a final tie-breaker)
        // Assuming actionId is unique and comparable (e.g., "playerID_sequenceNumber")
        return this.actionId.compareTo(other.actionId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "playerId='" + playerId + '\'' +
                ", L=" + logicalTimestamp +
                ", actionId='" + actionId + '\'' +
                ", T_host=" + arrivalTimestampHost +
                '}';
    }

    // Abstract method to be implemented by subclasses to describe the action
    public abstract String getDescription();
}
