package main.java.security; // Or your project's security package

import main.java.util.Constants; // Assuming Constants is in main.java.util

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator; // Keep for potential key generation utilities
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.NoSuchAlgorithmException; // Keep for KeyGenerator
import java.util.Base64;

/**
 * Utility class for handling encryption/decryption and object serialization for messages.
 * Uses AES for symmetric encryption of message payloads.
 * Assumes a pre-shared or constant symmetric key as per project requirements.
 */
public class EncryptionUtil {

    private static final SecretKey sharedSymmetricKey;
    private static final String AES_CIPHER_MODE = "AES/ECB/PKCS5Padding"; // Explicit mode and padding

    static {
        try {
            // For project simplicity, use a predictable, constant key.
            // "assume the keys don't change"
            // Key must be 16 bytes for AES-128, 24 for AES-192, 32 for AES-256.
            // Constants.SYMMETRIC_KEY_SIZE is 128 bits (16 bytes).
            byte[] keyBytes = new byte[Constants.SYMMETRIC_KEY_SIZE / 8];
            // Simple predictable key for consistent encryption/decryption across instances.
            for(int i = 0; i < keyBytes.length; i++) {
                keyBytes[i] = (byte)(i + 1); // Example: 1, 2, 3,...
            }
            sharedSymmetricKey = new SecretKeySpec(keyBytes, Constants.SYMMETRIC_KEY_ALGORITHM); // "AES"
            System.out.println("EncryptionUtil: Shared Symmetric Key initialized for payload encryption.");
        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to initialize shared symmetric key: " + e.getMessage());
            // This is a fatal error if encryption is required.
            throw new RuntimeException("Symmetric key initialization failed, cannot proceed with encryption.", e);
        }
    }

    /**
     * Serializes an Object into a byte array.
     * @param object The object to serialize.
     * @return Byte array representation of the object, or null on error.
     */
    public static byte[] serializeObject(Object object) {
        if (object == null) return null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(object);
            return bos.toByteArray();
        } catch (Exception e) {
            System.err.println("EncryptionUtil (serializeObject) Error: " + e.getMessage());
            // e.printStackTrace(); // For debugging
            return null;
        }
    }

    /**
     * Deserializes a byte array back into an Object.
     * @param bytes The byte array to deserialize.
     * @return The deserialized Object, or null on error.
     */
    public static Object deserializeObject(byte[] bytes) {
        if (bytes == null) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (Exception e) {
            System.err.println("EncryptionUtil (deserializeObject) Error: " + e.getMessage());
            // e.printStackTrace(); // For debugging
            return null;
        }
    }

    /**
     * Encrypts a byte array using the shared symmetric AES key.
     * @param data The plaintext byte array to encrypt.
     * @return The encrypted byte array, or null on error.
     */
    public static byte[] encryptBytes(byte[] data) {
        if (sharedSymmetricKey == null || data == null) {
            System.err.println("EncryptionUtil (encryptBytes): Key or data is null.");
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(AES_CIPHER_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, sharedSymmetricKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            System.err.println("EncryptionUtil (encryptBytes) Error: " + e.getMessage());
            // e.printStackTrace(); // For debugging
            return null;
        }
    }

    /**
     * Decrypts a byte array using the shared symmetric AES key.
     * @param encryptedData The encrypted byte array.
     * @return The decrypted plaintext byte array, or null on error.
     */
    public static byte[] decryptBytes(byte[] encryptedData) {
        if (sharedSymmetricKey == null || encryptedData == null) {
            System.err.println("EncryptionUtil (decryptBytes): Key or data is null.");
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(AES_CIPHER_MODE);
            cipher.init(Cipher.DECRYPT_MODE, sharedSymmetricKey);
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            System.err.println("EncryptionUtil (decryptBytes) Error: " + e.getMessage());
            // e.printStackTrace(); // For debugging
            return null;
        }
    }

    // The Base64 string encryption/decryption methods can be kept for other uses if needed,
    // but for object payloads, byte[] to byte[] encryption is more direct.
    public static String encryptString(String data) {
        byte[] encryptedBytes = encryptBytes(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return (encryptedBytes != null) ? Base64.getEncoder().encodeToString(encryptedBytes) : null;
    }

    public static String decryptString(String encryptedData) {
        byte[] decodedEncryptedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedBytes = decryptBytes(decodedEncryptedBytes);
        return (decryptedBytes != null) ? new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8) : null;
    }
}
