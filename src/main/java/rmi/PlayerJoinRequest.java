package main.java.rmi;

import java.io.Serializable;

/**
 * Data Transfer Object (DTO) sent by the client when requesting to join the game via RMI.
 * This demonstrates parameter passing from client to host.
 */
public class PlayerJoinRequest implements Serializable {
    private static final long serialVersionUID = 1L; // Good practice for Serializable classes
    private String clientIpForSocket; // Example: client's IP, for the host to log or be aware of.

    public PlayerJoinRequest(String clientIpForSocket) {
        this.clientIpForSocket = clientIpForSocket;
    }

    public String getClientIpForSocket() {
        return clientIpForSocket;
    }

    // Add setters if the object needs to be modified after creation,
    // or keep it immutable if constructed with all necessary data.
}