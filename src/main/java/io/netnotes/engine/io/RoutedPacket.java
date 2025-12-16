package io.netnotes.engine.io;

import io.netnotes.engine.io.process.FlowProcessId;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

import java.util.Objects;

/**
 * RoutedPacket - Universal routing packet with hierarchical addressing.
 * 
 * Architecture Evolution:
 * - OLD: sourceId (INTEGER) + packet data
 * - NEW: ContextPath + metadata + packet data
 * - ProcessId is now derived from ContextPath endpoint
 * 
 * Benefits:
 * - Unified addressing across InputSources and Processes
 * - Hierarchical routing (can route to /daemon/* or /process/worker/*)
 * - Supports both point-to-point and multicast patterns
 * - Metadata for correlation IDs, types, routing hints
 * 
 * Examples:
 * - /daemon/usb/keyboard/0  -> Input source
 * - /process/database/main  -> Process
 * - /window/main/canvas     -> UI element
 * - /network/tcp/connection/42 -> Network connection
 */
public final class RoutedPacket {
    private final ContextPath sourcePath;
    private final ContextPath destinationPath;  // Optional: explicit destination
    private final NoteBytesReadOnly payload;
    private final NoteBytesMap metadata;
    private final long timestamp;
    
    // Routing modes
    private final RoutingMode routingMode;
    
    /**
     * Create a routed packet with source path only (registry determines destination)
     */
    public RoutedPacket(ContextPath sourcePath, NoteBytesReadOnly payload) {
        this(sourcePath, null, payload, new NoteBytesMap(), RoutingMode.REGISTRY);
    }
    
    /**
     * Create a routed packet with explicit destination
     */
    public RoutedPacket(
            ContextPath sourcePath, 
            ContextPath destinationPath,
            NoteBytesReadOnly payload) {
        this(sourcePath, destinationPath, payload, new NoteBytesMap(), RoutingMode.DIRECT);
    }
    
    /**
     * Full constructor with metadata
     */
    public RoutedPacket(
            ContextPath sourcePath,
            ContextPath destinationPath,
            NoteBytesReadOnly payload,
            NoteBytesMap metadata,
            RoutingMode routingMode) {
        
        Objects.requireNonNull(sourcePath, "sourcePath cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        
        this.sourcePath = sourcePath;
        this.destinationPath = destinationPath;
        this.payload = payload;
        this.metadata = new NoteBytesMap(metadata);
        this.routingMode = routingMode;
        this.timestamp = System.currentTimeMillis();
    }
    
    // ===== PATH-BASED ACCESSORS =====
    
    /**
     * Get source path (full hierarchical address)
     */
    public ContextPath getSourcePath() {
        return sourcePath;
    }
    
    /**
     * Get destination path (null if registry routing)
     */
    public ContextPath getDestinationPath() {
        return destinationPath;
    }
    
  

    /**
     * Get ProcessId from source path
     * Useful when routing to processes
    */
    public FlowProcessId getSourceProcessId() {
        return new FlowProcessId(sourcePath);
    }
    
    /**
     * Get ProcessId from destination path
  */
    public FlowProcessId getDestinationProcessId() {
        if (destinationPath == null) {
            throw new IllegalStateException("No destination path set");
        }
        return new FlowProcessId(destinationPath);
    }
    
    // ===== PAYLOAD ACCESSORS =====
    
    public NoteBytesReadOnly getPayload() {
        return payload;
    }
    
    /**
     * @deprecated Use getPayload() instead
     */
    @Deprecated
    public NoteBytesReadOnly getPacket() {
        return payload;
    }
    
    public byte getPayloadType() {
        return payload.getType();
    }
    
    /**
     * Convenience: Get payload as string (if it's a string type)
     */
    public String getPayloadAsString() {
        // Assuming NoteBytesReadOnly has a string conversion method
        return payload.toString();
    }
    
    // ===== METADATA MANAGEMENT =====
    
    /**
     * Check if metadata key exists
    
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    } */

    public boolean hasMetadata(NoteBytes key) {
        return metadata.containsKey(key);
    }
    
    /**
     * Get metadata value
     */
    public NoteBytes getMetadata(NoteBytes key) {
        return metadata.get(key);
    }

    public ContextPath getMetadataAsPath(NoteBytes key){
        NoteBytes value = metadata.get(key);
        if(key != null && key.getType() == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE){
            return ContextPath.fromNoteBytes(value);
        }else if( key != null && ByteDecoding.isStringType(value.getType())){
            return ContextPath.parse(value.getAsString());
        }
        return null;
    }
    
    /**
     * Get metadata as string
    
    public String getMetadataString(String key) {
        NoteBytes value = metadata.get(key);
        return value != null ? value.toString() : null;
    } */
    
    /**
     * Get metadata as integer
     */
    public Integer getMetadataInt(NoteBytes key) {
        NoteBytes value = metadata.get(key);
        return ByteDecoding.forceAsBigDecimal(value).intValue();
    }
    
    /**
     * Get all metadata
     */
    public NoteBytesMap getAllMetadata() {
        return new NoteBytesMap(metadata);
    }
    
    // ===== ROUTING CONTROL =====
    
    public RoutingMode getRoutingMode() {
        return routingMode;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Check if this packet should be routed hierarchically
     * (matches all paths under a prefix)
     */
    public boolean isHierarchicalRouting() {
        return routingMode == RoutingMode.HIERARCHICAL;
    }
    
    /**
     * Check if this is a multicast packet
     */
    public boolean isMulticast() {
        return routingMode == RoutingMode.MULTICAST;
    }
    
    // ===== IMMUTABLE BUILDERS =====
    
    /**
     * Create a new packet with additional metadata
     */
    public RoutedPacket withMetadata(NoteBytes key, NoteBytes value) {
        NoteBytesMap newMetadata = new NoteBytesMap(this.metadata);
        newMetadata.put(key, value);
        return new RoutedPacket(sourcePath, destinationPath, payload, newMetadata, routingMode);
    }
    
    /**
     * Create a new packet with multiple metadata entries
     */
    public RoutedPacket withMetadata(NoteBytesMap additionalMetadata) {
        NoteBytesMap newMetadata = new NoteBytesMap(this.metadata);
        newMetadata.putAll(additionalMetadata);
        return new RoutedPacket(sourcePath, destinationPath, payload, newMetadata, routingMode);
    }
    
    /**
     * Create a new packet with explicit destination
     */
    public RoutedPacket withDestination(ContextPath destination) {
        return new RoutedPacket(sourcePath, destination, payload, metadata, RoutingMode.DIRECT);
    }
    
    /**
     * Create a new packet with different routing mode
     */
    public RoutedPacket withRoutingMode(RoutingMode mode) {
        return new RoutedPacket(sourcePath, destinationPath, payload, metadata, mode);
    }
    
    /**
     * Create a reply packet (swaps source and destination)
     */
    public RoutedPacket createReply(NoteBytesReadOnly replyPayload) {
        if (destinationPath == null) {
            // Original was registry-routed, reply goes back to source
            return new RoutedPacket(
                this.destinationPath != null ? this.destinationPath : ContextPath.ROOT,
                this.sourcePath,
                replyPayload,
                metadata,
                RoutingMode.DIRECT
            );
        }
        
        // Swap source and destination
        return new RoutedPacket(
            this.destinationPath,
            this.sourcePath,
            replyPayload,
            metadata,
            RoutingMode.DIRECT
        );
    }
    
    // ===== STATIC FACTORIES =====

    public static RoutedPacket create(ContextPath sourcePath, NoteBytesPair... payload) {
        return create(sourcePath, new NoteBytesObject(payload));
    }

    public static RoutedPacket create(ContextPath sourcePath, NoteBytesMap payload) {
        return create(sourcePath, payload.toNoteBytes());
    }
    
    public static RoutedPacket create(ContextPath sourcePath, NoteBytes payload) {
        return create(sourcePath, payload.readOnly());
    }
    
    /**
     * Create a simple packet from path and payload
     */
    public static RoutedPacket create(ContextPath sourcePath, NoteBytesReadOnly payload) {
        return new RoutedPacket(sourcePath, payload);
    }
    
    /**
     * Create packet with explicit destination
     */
    public static RoutedPacket createDirect(
            ContextPath sourcePath,
            ContextPath destinationPath,
            NoteBytesReadOnly payload) {
        return new RoutedPacket(sourcePath, destinationPath, payload);
    }
    
    /**
     * Create multicast packet (goes to all listeners on a path prefix)
     */
    public static RoutedPacket createMulticast(
            ContextPath sourcePath,
            ContextPath targetPrefix,
            NoteBytesReadOnly payload) {
        return new RoutedPacket(
            sourcePath,
            targetPrefix,
            payload,
            new NoteBytesMap(),
            RoutingMode.MULTICAST
        );
    }
    
    /**
     * Create hierarchical packet (routes to all under source path prefix)
     */
    public static RoutedPacket createHierarchical(
            ContextPath sourcePath,
            NoteBytesReadOnly payload) {
        return new RoutedPacket(
            sourcePath,
            null,
            payload,
            new NoteBytesMap(),
            RoutingMode.HIERARCHICAL
        );
    }

    
    // ===== ROUTING MODES =====
    
    public enum RoutingMode {
        /**
         * Registry determines destination based on source path.
         * Classic publish-subscribe pattern.
         */
        REGISTRY,
        
        /**
         * Explicit point-to-point routing.
         * destinationPath must be set.
         */
        DIRECT,
        
        /**
         * Multicast to all listeners under destinationPath prefix.
         * Example: /daemon/* reaches all daemon subsystems
         */
        MULTICAST,
        
        /**
         * Hierarchical routing up/down the path tree.
         * Can route to parent or children based on path relationship.
         */
        HIERARCHICAL,
        
        /**
         * Broadcast to all registered destinations.
         * Ignores paths entirely.
         */
        BROADCAST
    }
    
    // ===== PATH MATCHING HELPERS =====
    
    /**
     * Check if this packet should be delivered to a destination path
     */
    public boolean matchesDestination(ContextPath targetPath) {
        return switch (routingMode) {
            case REGISTRY -> destinationPath == null || targetPath.equals(destinationPath);
            case DIRECT -> targetPath.equals(destinationPath);
            case MULTICAST -> targetPath.startsWith(destinationPath);
            case HIERARCHICAL -> targetPath.startsWith(sourcePath) || sourcePath.startsWith(targetPath);
            case BROADCAST -> true;
        };
    }
    
    /**
     * Check if this packet comes from a specific path prefix
     */
    public boolean isFromPath(ContextPath pathPrefix) {
        return sourcePath.startsWith(pathPrefix);
    }
    
    /**
     * Get the relative path from source to destination
     */
    public ContextPath getRelativePath() {
        if (destinationPath == null) return null;
        return destinationPath.relativeTo(sourcePath);
    }
    
    // ===== SERIALIZATION HINTS =====
    
    /**
     * Convert to wire format (for network transmission)
     * Format: [sourcePath][destPath?][metadata][payload]
     */
    public byte[] toWireFormat() {
        // Implementation would serialize the packet for transmission
        // This is a placeholder showing the structure
        throw new UnsupportedOperationException("Not yet implemented");
    }
    
    /**
     * Create from wire format
     */
    public static RoutedPacket fromWireFormat(byte[] data) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
    
    // ===== UTILITIES =====
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("RoutedPacket{");
        sb.append("source=").append(sourcePath);
        
        if (destinationPath != null) {
            sb.append(", dest=").append(destinationPath);
        }
        
        sb.append(", mode=").append(routingMode);
        sb.append(", payloadType=").append(payload.getType());
        sb.append(", size=").append(payload.byteLength());
        
        if (!metadata.isEmpty()) {
            sb.append(", metadata=").append(metadata.keySet());
        }
        
        sb.append(", age=").append(System.currentTimeMillis() - timestamp).append("ms");
        sb.append("}");
        
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RoutedPacket other)) return false;
        
        return Objects.equals(sourcePath, other.sourcePath) &&
               Objects.equals(destinationPath, other.destinationPath) &&
               Objects.equals(payload, other.payload) &&
               routingMode == other.routingMode;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sourcePath, destinationPath, routingMode);
    }
}