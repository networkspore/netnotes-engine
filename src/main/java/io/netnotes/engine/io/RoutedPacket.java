package io.netnotes.engine.io;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * RoutedPacket - Combines sourceId + packet for routing.
 * 
 * This is the fundamental unit of the routing system:
 * - sourceId: INTEGER NoteBytesReadOnly identifying the source
 * - packet: NoteBytesReadOnly containing the actual data (OBJECT, ENCRYPTED, etc.)
 * 
 * The registry routes packets based solely on sourceId, without inspecting content.
 */
public final class RoutedPacket {
    private final NoteBytesReadOnly sourceId;
    private final NoteBytesReadOnly packet;
    
    /**
     * Create a routed packet
     * 
     * @param sourceId Must be INTEGER type NoteBytesReadOnly
     * @param packet The packet data (any type)
     */
    public RoutedPacket(NoteBytesReadOnly sourceId, NoteBytesReadOnly packet) {
        if (sourceId == null || packet == null) {
            throw new IllegalArgumentException("sourceId and packet cannot be null");
        }
        
        // Validate that sourceId is INTEGER type
        if (sourceId.getType() != io.netnotes.engine.noteBytes.processing.NoteBytesMetaData.INTEGER_TYPE) {
            throw new IllegalArgumentException("sourceId must be INTEGER type, got: " + sourceId.getType());
        }
        
        this.sourceId = sourceId;
        this.packet = packet;
    }
    
    /**
     * Get the source identifier
     */
    public NoteBytesReadOnly getSourceId() {
        return sourceId;
    }
    
    /**
     * Get the packet data
     */
    public NoteBytesReadOnly getPacket() {
        return packet;
    }
    
    /**
     * Get sourceId as integer (convenience method)
     */
    public int getSourceIdAsInt() {
        return sourceId.getAsInt();
    }
    
    /**
     * Get packet type (convenience method)
     */
    public byte getPacketType() {
        return packet.getType();
    }
    
    @Override
    public String toString() {
        return String.format("RoutedPacket{sourceId=%d, packetType=%d, size=%d}",
            getSourceIdAsInt(), getPacketType(), packet.byteLength());
    }
}