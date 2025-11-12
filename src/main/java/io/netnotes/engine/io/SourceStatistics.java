package io.netnotes.engine.io;

/**
 * SourceStatistics - Tracks performance metrics for an input source
 *     
 */
public class SourceStatistics {
        private volatile long packetCount = 0;
        private volatile long byteCount = 0;
        private volatile long errorCount = 0;
        private volatile long lastPacketTime = 0;
        
        public void incrementPacketCount() {
            packetCount++;
            lastPacketTime = System.nanoTime();
        }
        
        public void addBytes(long bytes) {
            byteCount += bytes;
        }
        
        public void incrementErrorCount() {
            errorCount++;
        }
        
        public long getPacketCount() { return packetCount; }
        public long getByteCount() { return byteCount; }
        public long getErrorCount() { return errorCount; }
        public long getLastPacketTime() { return lastPacketTime; }
    }