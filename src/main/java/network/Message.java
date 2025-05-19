package main.java.network; // Or your project's equivalent package for network classes

import main.java.util.MessageType;
import main.java.security.EncryptionUtil; // Make sure this points to your updated EncryptionUtil

import java.io.Serializable;
// import java.util.Arrays; // Only needed if you were to log byte arrays directly in toString

/**
 * Represents a generic message exchanged between the host and clients over Sockets.
 * The 'payload' of this message is encrypted during transmission to ensure confidentiality.
 * The message metadata (type, sender, timestamp) remains unencrypted for routing and processing.
 */
public class Message implements Serializable {
    // serialVersionUID is important for Serializable classes, especially if versions change.
    // Increment this if the class structure changes significantly in a way that affects serialization.
    private static final long serialVersionUID = 20250518L; // Example: YYYYMMDDL

    private final MessageType messageType;  // The type of this message (e.g., ACTION_REQUEST, GAME_STATE_UPDATE)
    private final String senderId;          // The PlayerID of the node sending this message
    private final long logicalTimestamp;    // The sender's logical clock value at the time of sending

    // This field will store the payload after it has been serialized into bytes and then encrypted.
    // This is what actually gets transmitted as part of the Message object when it's serialized.
    private byte[] encryptedPayloadData;

    // This field is marked 'transient', meaning it will NOT be serialized and sent over the network.
    // It's used on the receiving side to cache the decrypted and deserialized payload object
    // once getPayload() is called for the first time.
    private transient Object actualPayloadObject;

    /**
     * Constructs a new Message.
     * The provided payloadObject will be serialized to bytes, then encrypted using AES,
     * and the resulting encrypted byte array will be stored for transmission.
     *
     * @param messageType      The type defining the purpose of this message.
     * @param payloadObject    The actual data object to be sent (e.g., an Action, GameState, String).
     * This object MUST implement {@link java.io.Serializable}.
     * @param senderId         The unique identifier of the message sender (e.g., "Player1", "HostServerNode").
     * @param logicalTimestamp The sender's logical clock value at the moment this message is created.
     */
    public Message(MessageType messageType, Object payloadObject, String senderId, long logicalTimestamp) {
        this.messageType = messageType;
        this.senderId = senderId;
        this.logicalTimestamp = logicalTimestamp;

        // Cache the original object in the transient field. This is mainly for the sender's convenience
        // if they need to access the payload immediately after creating the Message object,
        // though typically a Message is created right before being written to an output stream.
        this.actualPayloadObject = payloadObject;

        // Encrypt the payload for network transmission.
        if (payloadObject != null) {
            // Step 1: Serialize the payload object into a byte array.
            byte[] serializedPayload = EncryptionUtil.serializeObject(payloadObject);
            if (serializedPayload != null) {
                // Step 2: Encrypt the serialized byte array.
                this.encryptedPayloadData = EncryptionUtil.encryptBytes(serializedPayload);
                if (this.encryptedPayloadData == null) {
                    // This is a critical failure if encryption is mandatory.
                    System.err.println("Message Constructor: CRITICAL - Payload encryption failed for object of type " +
                            payloadObject.getClass().getSimpleName() +
                            ". The message will be sent with a null encrypted payload, likely causing errors on receipt.");
                    // Consider throwing a RuntimeException here if encryption failure is unacceptable.
                    // throw new RuntimeException("Payload encryption failed for message type " + messageType);
                }
            } else {
                // Serialization itself failed.
                System.err.println("Message Constructor: CRITICAL - Payload serialization failed for object of type " +
                        payloadObject.getClass().getSimpleName() +
                        ". The message will be sent with a null encrypted payload.");
                // Consider throwing a RuntimeException here.
            }
        } else {
            // If the original payload object is null, there's nothing to encrypt.
            this.encryptedPayloadData = null;
        }
    }

    /**
     * Gets the type of this message.
     * @return The {@link MessageType}.
     */
    public MessageType getMessageType() {
        return messageType;
    }

    /**
     * Gets the ID of the sender of this message.
     * @return The sender's PlayerID or host ID.
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * Gets the logical timestamp of when this message was created by the sender.
     * @return The logical clock value.
     */
    public long getLogicalTimestamp() {
        return logicalTimestamp;
    }

    /**
     * Retrieves the actual payload object associated with this message.
     * If this {@code Message} instance was received over the network (i.e., deserialized),
     * this method will, on its first call, trigger the decryption of the
     * {@code encryptedPayloadData} and then deserialize it back into the original Java object.
     * Subsequent calls will return the cached, already processed object.
     *
     * @return The decrypted and deserialized payload object. This can be {@code null} if the original
     * payload was {@code null}, or if decryption or deserialization fails.
     * The caller should typically cast this {@code Object} to the expected type
     * based on the {@code messageType}.
     */
    public Object getPayload() {
        // If the actualPayloadObject is already populated (e.g., if this is the sender's instance
        // or if getPayload() has been called before on a received instance), return it directly.
        if (this.actualPayloadObject != null) {
            return this.actualPayloadObject;
        }

        // If actualPayloadObject is null, but we have encrypted data, this means we are on the
        // receiving side and need to decrypt and deserialize.
        if (this.encryptedPayloadData != null) {
            // Step 1: Decrypt the byte array.
            byte[] decryptedSerializedPayload = EncryptionUtil.decryptBytes(this.encryptedPayloadData);
            if (decryptedSerializedPayload != null) {
                // Step 2: Deserialize the decrypted byte array back into an Object.
                this.actualPayloadObject = EncryptionUtil.deserializeObject(decryptedSerializedPayload);
                if (this.actualPayloadObject == null) {
                    System.err.println("Message getPayload: CRITICAL - Payload deserialization failed after successful decryption for message type " + this.messageType + " from " + this.senderId);
                }
            } else {
                System.err.println("Message getPayload: CRITICAL - Payload decryption failed for message type " + this.messageType + " from " + this.senderId);
            }
            // Return the object (which might be null if decryption/deserialization failed).
            return this.actualPayloadObject;
        }

        // If there was no encryptedPayloadData to begin with (meaning the original payloadObject was null),
        // then the actual payload is indeed null.
        return null;
    }

    /**
     * Provides a string representation of the Message object, primarily for logging.
     * It indicates whether an encrypted payload is present rather than attempting to
     * display the payload itself (which would trigger decryption).
     *
     * @return A string summary of the message.
     */
    @Override
    public String toString() {
        String payloadStatus;
        if (actualPayloadObject != null) {
            // If payload has been accessed and deserialized, show its class name
            payloadStatus = actualPayloadObject.getClass().getSimpleName();
        } else if (encryptedPayloadData != null) {
            payloadStatus = "[EncryptedData present, length: " + encryptedPayloadData.length + "]";
        } else {
            payloadStatus = "[NoPayload/Null]";
        }

        return "Message{" +
                "type=" + messageType +
                ", senderId='" + senderId + '\'' +
                ", L=" + logicalTimestamp + // 'L' for Logical clock
                ", payloadStatus=" + payloadStatus +
                '}';
    }
}
