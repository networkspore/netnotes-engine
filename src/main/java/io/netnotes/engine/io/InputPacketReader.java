package io.netnotes.engine.io;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;


/**
 * Content-agnostic packet reader with source-based routing.
 * 
 * Packet format:
 * [9-byte source header: STRING(sourceId)]
 * [Main packet: ENCRYPTED or OBJECT]
 * 
 * The reader:
 * 1. Reads the source ID
 * 2. Reads the raw packet bytes
 * 3. Routes to registered handlers WITHOUT interpretation
 * 4. Handlers decrypt/parse JIT (just-in-time)
 * 
 * This enables:
 * - Multiple encrypted sources with different keys
 * - Content-agnostic pipeline (just routes bytes)
 * - Lazy decryption (only when needed)
 * - Bi-directional IO network
 */
public final class InputPacketReader {
    private final PipedOutputStream m_outputStream;
    private final BlockingQueue<RoutedPacket> m_routedQueue;
    private final Executor m_decodeExecutor;
    private volatile Thread m_readerThread;
    
    // Source-based routing: sourceId -> handler function
    private final ConcurrentHashMap<Integer, Function<byte[], InputRecord>> m_sourceHandlers;
    
    // Default handler for unknown sources
    private Function<byte[], InputRecord> m_defaultHandler;
    
    public InputPacketReader(
            PipedOutputStream outputStream,
            BlockingQueue<RoutedPacket> routedQueue,
            Executor decodeExecutor) {
        this.m_outputStream = Objects.requireNonNull(outputStream);
        this.m_routedQueue = Objects.requireNonNull(routedQueue);
        this.m_decodeExecutor = Objects.requireNonNull(decodeExecutor);
        this.m_sourceHandlers = new ConcurrentHashMap<>();
    }
    
    /**
     * Register a handler for a specific source.
     * Handler receives raw packet bytes and returns InputRecord.
     * Handler is responsible for decryption/parsing.
     */
    public void registerSourceHandler(int sourceId, Function<byte[], InputRecord> handler) {
        m_sourceHandlers.put(sourceId, handler);
    }
    
    /**
     * Unregister a source handler
     */
    public void unregisterSourceHandler(int sourceId) {
        m_sourceHandlers.remove(sourceId);
    }
    
    /**
     * Set default handler for unknown sources
     */
    public void setDefaultHandler(Function<byte[], InputRecord> handler) {
        this.m_defaultHandler = handler;
    }
    
    /**
     * Start reading packets from the input stream
     */
    public void start() {
        CompletableFuture.runAsync(this::readLoop, m_decodeExecutor);
    }
    
    /**
     * Main read loop - routes packets without interpretation
     */
    private void readLoop() {
        m_readerThread = Thread.currentThread();
        
        try (PipedInputStream inputStream = new PipedInputStream(m_outputStream, 8192);
             NoteBytesReader reader = new NoteBytesReader(inputStream)) {
            
            while (!Thread.currentThread().isInterrupted()) {
                // Read 9-byte source header: [STRING:11][length:4][sourceId:4]
                NoteBytesReadOnly sourceHeader = reader.nextNoteBytesReadOnly();
                if (sourceHeader == null) break;
                
                // Verify it's a STRING type with length 4
                if (sourceHeader.getType() != NoteBytesMetaData.INTEGER_TYPE ||
                    sourceHeader.byteLength() != 4) {
                    System.err.println("InputPacketReader: Invalid source header");
                    continue;
                }
                
                // Extract source ID (4-byte integer)
                int sourceId = sourceHeader.getAsInt();
                
                // Read the main packet (ENCRYPTED or OBJECT) - WITHOUT parsing
                NoteBytesReadOnly mainPacket = reader.nextNoteBytesReadOnly();
                if (mainPacket == null) {
                    System.err.println("InputPacketReader: Missing main packet for source " + sourceId);
                    continue;
                }
                
                // Get raw bytes of the packet
                byte[] packetBytes = mainPacket.get();
                
                // Route the packet
                RoutedPacket routed = new RoutedPacket(sourceId, packetBytes);
                m_routedQueue.put(routed);
                
                // Optionally, invoke handler immediately if registered
                Function<byte[], InputRecord> handler = m_sourceHandlers.get(sourceId);
                if (handler == null) {
                    handler = m_defaultHandler;
                }
                
                if (handler != null) {
                    try {
                        // Handler decrypts/parses JIT
                        InputRecord record = handler.apply(packetBytes);
                        if (record != null) {
                            // Handler successfully processed - update routed packet
                            routed.setProcessedRecord(record);
                        }
                    } catch (Exception e) {
                        System.err.println("InputPacketReader: Handler error for source " + sourceId);
                        e.printStackTrace();
                        routed.setError(e);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("InputPacketReader: Interrupted");
        } catch (Exception e) {
            System.err.println("InputPacketReader: Error in read loop");
            e.printStackTrace();
        } finally {
            System.out.println("InputPacketReader: Stopped");
        }
    }
    
    public void stop() {
        if (m_readerThread != null) {
            m_readerThread.interrupt();
        }
    }
    
    /**
     * Routed packet container - holds raw bytes and optional processed record
     */
    public static class RoutedPacket {
        private final int sourceId;
        private final byte[] rawPacket;
        private volatile InputRecord processedRecord;
        private volatile Exception error;
        
        public RoutedPacket(int sourceId, byte[] rawPacket) {
            this.sourceId = sourceId;
            this.rawPacket = rawPacket;
        }
        
        public int getSourceId() {
            return sourceId;
        }
        
        public byte[] getRawPacket() {
            return rawPacket;
        }
        
        /**
         * Get processed record if handler already processed it
         */
        public InputRecord getProcessedRecord() {
            return processedRecord;
        }
        
        void setProcessedRecord(InputRecord record) {
            this.processedRecord = record;
        }
        
        /**
         * Check if packet type is ENCRYPTED (26)
         */
        public boolean isEncrypted() {
            return rawPacket.length > 0 && rawPacket[0] == NoteBytesMetaData.NOTE_BYTES_ENCRYPTED_TYPE;
        }
        
        /**
         * Get the packet type byte
         */
        public byte getPacketType() {
            return rawPacket.length > 0 ? rawPacket[0] : -1;
        }
        
        /**
         * Get error if handler failed
         */
        public Exception getError() {
            return error;
        }
        
        void setError(Exception error) {
            this.error = error;
        }
        
        /**
         * Parse the packet lazily (for unencrypted packets)
         */
        public InputRecord parseUnencrypted() {
            if (isEncrypted()) {
                throw new IllegalStateException("Cannot parse encrypted packet without decryption");
            }
            
            // Parse as NoteBytesObject
            NoteBytesReadOnly packetObj = NoteBytesReadOnly.readNote(rawPacket, 0);
            
            if (packetObj.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                throw new IllegalStateException("Expected OBJECT type, got: " + packetObj.getType());
            }
            
            // Create source ID wrapper
            NoteBytesReadOnly sourceIdNB = new NoteBytesReadOnly(sourceId);
            
            return InputRecord.fromNoteBytes(sourceIdNB, packetObj);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("RoutedPacket{sourceId=").append(sourceId);
            sb.append(", encrypted=").append(isEncrypted());
            sb.append(", bytes=").append(rawPacket.length);
            if (processedRecord != null) {
                sb.append(", processed=").append(processedRecord.type());
            }
            if (error != null) {
                sb.append(", error=").append(error.getMessage());
            }
            sb.append("}");
            return sb.toString();
        }
    }
    
    /**
     * Helper: Create a plaintext handler (no decryption)
     */
    public static Function<byte[], InputRecord> createPlaintextHandler() {
        return (packetBytes) -> {
            RoutedPacket dummy = new RoutedPacket(0, packetBytes);
            return dummy.parseUnencrypted();
        };
    }
    
    /**
     * Helper: Create an encrypted handler with decryption key
     */
    public static Function<byte[], InputRecord> createEncryptedHandler(
            EncryptionSession encryptionSession) {
        return (packetBytes) -> {
            // Check if encrypted
            if (packetBytes.length < 5 || 
                packetBytes[0] != NoteBytesMetaData.NOTE_BYTES_ENCRYPTED_TYPE) {
                throw new IllegalStateException("Expected ENCRYPTED packet");
            }
            
            // Extract ciphertext length (bytes 1-4)
            int cipherLen = ((packetBytes[1] & 0xFF) << 24) |
                           ((packetBytes[2] & 0xFF) << 16) |
                           ((packetBytes[3] & 0xFF) << 8) |
                           (packetBytes[4] & 0xFF);
            
            if (packetBytes.length < 5 + cipherLen) {
                throw new IllegalStateException("Incomplete encrypted packet");
            }
            
            // Extract ciphertext (starts at byte 5)
            byte[] ciphertext = new byte[cipherLen];
            System.arraycopy(packetBytes, 5, ciphertext, 0, cipherLen);
            
            // Decrypt
            byte[] plaintext = encryptionSession.decrypt(ciphertext);
            if (plaintext == null) {
                throw new IllegalStateException("Decryption failed");
            }
            
            // Parse decrypted packet
            RoutedPacket dummy = new RoutedPacket(0, plaintext);
            return dummy.parseUnencrypted();
        };
    }
    
    /**
     * Encryption session interface (to be implemented by client)
     */
    public interface EncryptionSession {
        byte[] decrypt(byte[] ciphertext);
    }
}