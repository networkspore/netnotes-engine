package io.netnotes.engine.io;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

/**
 * Routed packet container - holds raw bytes and optional processed record
 */
public class RoutedPacket {
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
    
    public boolean isRecordProcessed(){
        return this.processedRecord != null;
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

