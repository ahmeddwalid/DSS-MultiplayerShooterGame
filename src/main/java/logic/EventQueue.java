package main.java.logic;

import main.java.core.Action;
import main.java.network.Message; // Assuming Message class is in network package
import main.java.util.MessageType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Manages incoming actions from clients on the host side for Total Order Multicast.
 */
public class EventQueue {
    private final PriorityBlockingQueue<Action> pendingActions;
    private final LinkedBlockingQueue<Action> readyToProcessActions;
    private final Map<String, Set<String>> actionAcks;
    private final Map<String, Boolean> multicastStatus;
    private final Map<String, Action> actionsBeingProcessed; // Tracks action instances by ID

    private final Map<String, ?> activePlayersProvider;

    public EventQueue(Map<String, ?> activePlayersProvider) {
        this.pendingActions = new PriorityBlockingQueue<>();
        this.readyToProcessActions = new LinkedBlockingQueue<>();
        this.actionAcks = new ConcurrentHashMap<>();
        this.multicastStatus = new ConcurrentHashMap<>();
        this.actionsBeingProcessed = new ConcurrentHashMap<>();
        this.activePlayersProvider = activePlayersProvider;
    }

    public synchronized void addIncomingAction(Action action) {
        action.setArrivalTimestampHost(System.nanoTime());
        pendingActions.put(action); // Add to priority queue
        actionsBeingProcessed.put(action.getActionId(), action); // Track instance by ID
        actionAcks.put(action.getActionId(), new HashSet<>()); // Prepare for ACKs
        multicastStatus.put(action.getActionId(), false); // Mark as not yet multicast
        System.out.println("EventQueue: Added action " + action.getActionId() + " (" + action.getDescription() + ") from " + action.getPlayerId() + ". L=" + action.getLogicalTimestamp() + ". Pending queue size: " + pendingActions.size());
    }

    public synchronized Action getNextActionToMulticast() {
        Action action = pendingActions.peek(); // Peek at the highest priority action
        if (action != null && !multicastStatus.getOrDefault(action.getActionId(), true)) {
            // If it exists and hasn't been marked as multicast, return it
            return action;
        }
        return null; // No action ready for multicast or queue is empty
    }

    public synchronized void markActionAsMulticast(String actionId) {
        if (!multicastStatus.containsKey(actionId)) {
            System.err.println("EventQueue: Tried to mark unknown actionId " + actionId + " as multicast. Action not in multicastStatus map.");
            return;
        }

        multicastStatus.put(actionId, true); // Mark as multicast
        System.out.println("EventQueue: Marked action " + actionId + " as multicast.");

        Action actionToProcess = actionsBeingProcessed.get(actionId);
        if (actionToProcess == null) {
            System.err.println("EventQueue: Action " + actionId + " not found in actionsBeingProcessed map after marking as multicast. This is unexpected.");
            return;
        }

        int currentNumberOfPlayers = activePlayersProvider.size();
        int expectedAcks = Math.max(0, currentNumberOfPlayers - 1);
        System.out.println("EventQueue: For action " + actionId + ", current players: " + currentNumberOfPlayers + ", expected ACKs: " + expectedAcks);

        // If 0 ACKs were expected (e.g., single player game), and it has been "multicast",
        // it's ready to be processed immediately.
        if (expectedAcks == 0) {
            System.out.println("EventQueue: Action " + actionId + " (" + actionToProcess.getDescription() + ") requires 0 ACKs. Attempting to move to ready queue.");

            boolean isInPending = pendingActions.contains(actionToProcess);
            System.out.println("EventQueue: Is action " + actionId + " in pendingActions? " + isInPending);

            if (isInPending) {
                boolean removedFromPending = pendingActions.remove(actionToProcess);
                System.out.println("EventQueue: Attempted to remove action " + actionId + " from pendingActions. Success: " + removedFromPending);

                if (removedFromPending) {
                    if (!readyToProcessActions.contains(actionToProcess)) {
                        boolean addedToReady = readyToProcessActions.offer(actionToProcess);
                        System.out.println("EventQueue: Action " + actionId + " offered to readyToProcessActions. Success: " + addedToReady + ". Ready queue size: " + readyToProcessActions.size());
                    } else {
                        System.out.println("EventQueue: Action " + actionId + " (0 ACKs) was already in ready queue. Not adding again.");
                    }
                    // Clean up tracking as it's now ready
                    actionAcks.remove(actionId);
                    actionsBeingProcessed.remove(actionId); // Remove from this map as it's moving to ready
                } else {
                    // This is a problematic state: it was in pending, but remove failed.
                    // This could happen if the equals/hashCode contract is violated or if the queue's internal state is inconsistent.
                    // However, since we're using the same object reference, it should remove.
                    System.err.println("EventQueue: CRITICAL - Action " + actionId + " was in pendingActions, but remove() failed. This indicates a potential issue with the queue or Action object's identity/equality.");
                }
            } else {
                // If it's not in pending, it might have been processed by another path (e.g. receiveAck if logic was different)
                // or there's a logic flaw.
                if (readyToProcessActions.contains(actionToProcess)) {
                    System.out.println("EventQueue: Action " + actionId + " (0 ACKs) not in pending, but already found in ready queue.");
                } else if (!actionsBeingProcessed.containsKey(actionId)) {
                    System.out.println("EventQueue: Action " + actionId + " (0 ACKs) not in pending, and also no longer in actionsBeingProcessed. Likely fully processed.");
                } else {
                    System.out.println("EventQueue: Action " + actionId + " (0 ACKs) was not found in pendingActions. It might have been processed already or there's a logic gap.");
                }
            }
        }
    }

    public synchronized void receiveAck(String actionId, String ackPlayerId) {
        Set<String> acks = actionAcks.get(actionId);
        Action actionToProcess = actionsBeingProcessed.get(actionId);

        if (acks == null || actionToProcess == null) {
            System.err.println("EventQueue: Received ACK for unknown or already processed actionId: " + actionId + " from " + ackPlayerId);
            return;
        }

        // An ACK should only be from a player *other* than the one who initiated the action.
        if (actionToProcess.getPlayerId().equals(ackPlayerId)) {
            System.out.println("EventQueue: Player " + ackPlayerId + " (original sender) ACKed action " + actionId + ". This ACK is noted but doesn't count towards 'other player' ACKs.");
            // Do not add to the 'acks' set for the purpose of meeting the 'expectedAcks' count from others.
        } else {
            boolean newlyAcked = acks.add(ackPlayerId);
            if (newlyAcked) {
                System.out.println("EventQueue: Received ACK for action " + actionId + " ("+actionToProcess.getDescription()+") from " + ackPlayerId + ". Total ACKs for this action (from others): " + acks.size());
            }
        }

        int currentNumberOfPlayers = activePlayersProvider.size();
        int expectedAcks = Math.max(0, currentNumberOfPlayers - 1); // ACKs needed from *other* players

        if (acks.size() >= expectedAcks) {
            System.out.println("EventQueue: Action " + actionId + " ("+actionToProcess.getDescription()+") has " + acks.size() + " ACKs from others, needs " + expectedAcks + ". Attempting to move to ready.");

            boolean isInPending = pendingActions.contains(actionToProcess); // Check before trying to remove
            if (isInPending) {
                boolean removedFromPending = pendingActions.remove(actionToProcess);
                if (removedFromPending) {
                    if (!readyToProcessActions.contains(actionToProcess)) {
                        readyToProcessActions.offer(actionToProcess);
                        System.out.println("EventQueue: Action " + actionId + " moved to ready queue via ACK. Ready queue size: " + readyToProcessActions.size());
                    } else {
                        System.out.println("EventQueue: Action " + actionId + " (via ACK) was already in ready queue.");
                    }
                    actionAcks.remove(actionId);
                    actionsBeingProcessed.remove(actionId);
                } else {
                    System.err.println("EventQueue: CRITICAL - Action " + actionId + " (via ACK) was in pendingActions, but remove() failed.");
                }
            } else {
                if (readyToProcessActions.contains(actionToProcess)) {
                    System.out.println("EventQueue: Action " + actionId + " (via ACK) not in pending, but already found in ready queue.");
                } else if (!actionsBeingProcessed.containsKey(actionId)){
                    System.out.println("EventQueue: Action " + actionId + " (via ACK) not in pending, and also no longer in actionsBeingProcessed. Likely fully processed.");
                } else {
                    System.out.println("EventQueue: Action " + actionId + " (via ACK) was not found in pendingActions. It might have been processed already.");
                }
            }
        } else {
            System.out.println("EventQueue: Action " + actionId + " ("+actionToProcess.getDescription()+") has " + acks.size() + " ACKs from others, needs " + expectedAcks + ".");
        }
    }

    public Action getNextActionToProcess() throws InterruptedException {
        Action action = readyToProcessActions.take(); // Blocks until an element is available
        System.out.println("EventQueue: Dequeuing action " + action.getActionId() + " ("+action.getDescription()+") for processing. Ready queue size after take: " + readyToProcessActions.size());
        return action;
    }

    public boolean hasProcessableActions() {
        return !readyToProcessActions.isEmpty();
    }

    public int getPendingActionCount() {
        return pendingActions.size();
    }

    public int getReadyActionCount() {
        return readyToProcessActions.size();
    }
}
