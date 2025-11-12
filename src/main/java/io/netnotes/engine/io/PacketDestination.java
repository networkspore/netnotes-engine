package io.netnotes.engine.io;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * Interface for packet destinations that receive routed packets.
 * Destinations handle their own decryption, parsing, and processing.
 */
@FunctionalInterface
public interface PacketDestination {
    /**
     * Handle a packet routed to this destination.
     * 
     * @param sourceId The source that sent this packet (as NoteBytesReadOnly)
     * @param packet The packet data (could be OBJECT, ENCRYPTED, etc.)
     */
    void handlePacket(NoteBytesReadOnly sourceId, NoteBytesReadOnly packet);
}