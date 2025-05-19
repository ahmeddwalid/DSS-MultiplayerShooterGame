package main.java.logic; // Or your project's equivalent package for logic components

import java.io.Serializable; // Import to allow LogicalClock objects to be part of other serializable objects if needed
import java.util.concurrent.atomic.AtomicLong; // Import for a thread-safe long value

/**
 * Implements a Lamport Logical Clock for event ordering in a distributed system.
 * Each node (HostServer and each ClientNode) in the game will maintain its own instance of this clock.
 * This clock helps provide a partial ordering of events across different nodes.
 *
 * The two main rules of Lamport clocks are implemented here:
 * 1. Before sending an event (message), a process increments its local clock.
 * 2. When a process receives a message with a timestamp, it updates its local clock
 * to be max(local_clock, received_timestamp) + 1.
 */
public class LogicalClock implements Serializable {
    // serialVersionUID is important for Serializable classes, especially if you plan to send
    // the clock state itself or if it's part of an object that gets serialized.
    private static final long serialVersionUID = 1L; // Version for serialization

    // currentTime holds the current logical time for this node.
    // AtomicLong is used to ensure that operations on the clock's time (like incrementing)
    // are thread-safe, which is good practice, especially if multiple threads in a node
    // might interact with its clock (though in this project, clock access is mostly synchronized
    // or happens within a single thread context per node for send/receive).
    private final AtomicLong currentTime;

    /**
     * Constructor for LogicalClock.
     * Initializes the clock time to 0.
     */
    public LogicalClock() {
        this.currentTime = new AtomicLong(0); // Start clock at 0
    }

    /**
     * Implements Lamport's first rule: Increment the local clock.
     * This method should be called by a node *before* it sends a message
     * or performs a significant local event that needs to be ordered.
     * The value returned is the new, incremented time, which should be used
     * as the timestamp for the event/message being sent.
     *
     * @return The new (incremented) logical time.
     */
    public long tick() {
        // Atomically increments the current time by 1 and returns the updated value.
        return currentTime.incrementAndGet();
    }

    /**
     * Implements Lamport's second rule: Update the local clock based on a received timestamp.
     * This method should be called by a node when it receives a message
     * carrying a logical timestamp from another node.
     * The local clock is advanced to be greater than both its current time
     * and the received timestamp.
     *
     * Rule: L_new = max(L_current, L_received) + 1
     *
     * The 'synchronized' keyword ensures that if multiple threads were to try and update
     * the clock concurrently (e.g., a node receiving multiple messages processed by different threads),
     * the update operation (read-modify-write of currentTime) is atomic and consistent.
     *
     * @param receivedTime The logical timestamp from the message that was just received.
     * @return The new (updated) logical time of this node's clock.
     */
    public synchronized long update(long receivedTime) {
        // Get the current local time.
        long localTime = currentTime.get();
        // Find the maximum of the local time and the timestamp from the received message.
        long maxTime = Math.max(localTime, receivedTime);
        // Set the local clock to this maximum value plus 1.
        currentTime.set(maxTime + 1);
        // Return the new local time.
        return currentTime.get();
    }

    /**
     * Gets the current value of the logical clock without incrementing it.
     * Useful for reading the current time for local decision-making or logging,
     * or for when an event occurs that doesn't involve sending a message immediately.
     *
     * @return The current logical time.
     */
    public long getCurrentTime() {
        return currentTime.get();
    }

    /**
     * Allows setting the clock to a specific time.
     * This is generally not part of the standard Lamport clock rules for ongoing operation
     * but can be useful for initialization, testing, or specific synchronization scenarios
     * if a system needs to reset or align clocks (though that's beyond basic Lamport).
     * In this project, it's not actively used during normal game flow but provided for completeness.
     *
     * @param time The new time value to set the clock to.
     */
    public void setTime(long time) { // Corrected return type from 'voidsetTime' to 'setTime'
        currentTime.set(time);
    }

    /**
     * Provides a string representation of the clock's current time.
     * Useful for logging and debugging.
     * @return A string like "LogicalClock{time=5}".
     */
    @Override
    public String toString() {
        return "LogicalClock{" + "time=" + currentTime.get() + '}';
    }
}
