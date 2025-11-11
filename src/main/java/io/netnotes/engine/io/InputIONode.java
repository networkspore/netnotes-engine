package io.netnotes.engine.io;

import java.io.PipedOutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;


/**
 * InputIONode - Bidirectional IO node for content-agnostic packet routing.
 * 
 * Features:
 * - Source-based routing without content inspection
 * - Lazy decryption/parsing (JIT by consumers)
 * - Bidirectional: can read AND write
 * - Multiple encrypted sources with different keys
 * - Pipeline doesn't need encryption keys
 * 
 * Architecture:
 * [Writer] -> [PipedOutputStream] -> [Reader] -> [RoutedPacket Queue]
 *                                                       |
 *                                                       v
 *                                              [Source Handlers]
 *                                              (decrypt/parse JIT)
 */
public class InputIONode implements AutoCloseable {
    private final String nodeId;
    private final PipedOutputStream outputStream;
    private final InputPacketWriter writer;
    private final InputPacketReader reader;
    private final BlockingQueue<CompletableFuture<byte[]>> writeQueue;
    private final BlockingQueue<RoutedPacket> routedQueue;
    
    // Control flags
    private volatile boolean enabled = true;
    
    /**
     * Create an IO node with bidirectional capability
     */
    public InputIONode(
            String nodeId,
            BlockingQueue<CompletableFuture<byte[]>> writeQueue,
            BlockingQueue<RoutedPacket> routedQueue,
            Executor executor) throws Exception {
        this.nodeId = nodeId;
        this.writeQueue = writeQueue;
        this.routedQueue = routedQueue;
        
        // Create piped output stream (writer writes to this)
        this.outputStream = new PipedOutputStream();
        
        // Create writer (writes to outputStream on dedicated thread)
        this.writer = new InputPacketWriter(writeQueue, outputStream, executor);
        
        // Create reader with routed packet queue
        this.reader = new InputPacketReader(outputStream, routedQueue, executor);
    }
    
    /**
     * Create an IO node with default queues
     */
    public InputIONode(String nodeId, Executor executor) throws Exception {
        this(
            nodeId,
            new LinkedBlockingQueue<>(),
            new LinkedBlockingQueue<>(),
            executor
        );
    }
    
    /**
     * Start the node (begin processing)
     */
    public void start() {
        writer.start();
        reader.start();
    }
    
    /**
     * Stop the node
     */
    public void stop() {
        writer.stop();
        reader.stop();
    }
    
    /**
     * Register a handler for a specific source ID.
     * Handler receives raw packet bytes and returns InputRecord.
     * Handler is responsible for decryption/parsing.
     * 
     * Example:
     * node.registerSourceHandler(1, InputPacketReader.createPlaintextHandler());
     * node.registerSourceHandler(2, InputPacketReader.createEncryptedHandler(myEncryption));
     */
    public void registerSourceHandler(int sourceId, Function<byte[], InputRecord> handler) {
        reader.registerSourceHandler(sourceId, handler);
    }
    
    /**
     * Unregister a source handler
     */
    public void unregisterSourceHandler(int sourceId) {
        reader.unregisterSourceHandler(sourceId);
    }
    
    /**
     * Set default handler for unknown sources
     */
    public void setDefaultHandler(Function<byte[], InputRecord> handler) {
        reader.setDefaultHandler(handler);
    }
    
    /**
     * Get the write queue for sending packets
     */
    public BlockingQueue<CompletableFuture<byte[]>> getWriteQueue() {
        return writeQueue;
    }
    
    /**
     * Get the routed packet queue for consuming packets
     * Packets contain raw bytes - decrypt/parse as needed
     */
    public BlockingQueue<RoutedPacket> getRoutedQueue() {
        return routedQueue;
    }
    
    /**
     * Enable or disable this node
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    @Override
    public void close() {
        stop();
        writer.close();
    }
    
    /**
     * Helper: Create a source packet writer for this node.
     * Automatically prepends the 9-byte source header.
     */
    public SourcePacketWriter createSourceWriter(int sourceId) {
        return new SourcePacketWriter(sourceId, writeQueue);
    }
    
    /**
     * Source-specific packet writer that automatically adds source header
     */
    public static class SourcePacketWriter {
        private final int sourceId;
        private final BlockingQueue<CompletableFuture<byte[]>> writeQueue;
        
        public SourcePacketWriter(int sourceId, BlockingQueue<CompletableFuture<byte[]>> writeQueue) {
            this.sourceId = sourceId;
            this.writeQueue = writeQueue;
        }
        
        /**
         * Write a packet with automatic source header
         */
        public void writePacket(byte[] packet) throws InterruptedException {
            byte[] fullPacket = prependSourceHeader(packet);
            CompletableFuture<byte[]> future = CompletableFuture.completedFuture(fullPacket);
            writeQueue.put(future);
        }
        
        /**
         * Write a packet asynchronously
         */
        public CompletableFuture<Void> writePacketAsync(CompletableFuture<byte[]> packetFuture) {
            return packetFuture.thenAccept(packet -> {
                try {
                    writePacket(packet);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
        }
        
        /**
         * Prepend 9-byte source header to packet
         * Format: [STRING:11][length:4][sourceId:4]
         */
        private byte[] prependSourceHeader(byte[] packet) {
            byte[] fullPacket = new byte[9 + packet.length];
            
            // Header: STRING type
            fullPacket[0] = 11; // STRING type
            
            // Length = 4 (big-endian)
            fullPacket[1] = 0x00;
            fullPacket[2] = 0x00;
            fullPacket[3] = 0x00;
            fullPacket[4] = 0x04;
            
            // Source ID as 4-byte integer (big-endian)
            fullPacket[5] = (byte) ((sourceId >> 24) & 0xFF);
            fullPacket[6] = (byte) ((sourceId >> 16) & 0xFF);
            fullPacket[7] = (byte) ((sourceId >> 8) & 0xFF);
            fullPacket[8] = (byte) (sourceId & 0xFF);
            
            // Copy packet
            System.arraycopy(packet, 0, fullPacket, 9, packet.length);
            
            return fullPacket;
        }
    }
}
