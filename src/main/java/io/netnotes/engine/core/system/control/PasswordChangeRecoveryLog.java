package io.netnotes.engine.core.system.control;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

/**
 * Password Change Recovery Log
 * 
 * Log Format:
 * # Password Change Recovery Log
 * # Started: 2025-11-26T10:30:00Z
 * # Total Files: 150
 * # Batch Size: 10
 * 
 * STARTED|2025-11-26T10:30:01Z|/data/file1.dat|12345
 * SUCCESS|2025-11-26T10:30:02Z|/data/file1.dat|12345
 * STARTED|2025-11-26T10:30:02Z|/data/file2.dat|67890
 * FAILED|2025-11-26T10:30:03Z|/data/file2.dat|67890|Error message here
 * STARTED|2025-11-26T10:30:03Z|/data/file3.dat|11111
 * # INTERRUPTED: Process terminated
 * 
 * States:
 * - STARTED: File re-encryption began
 * - SUCCESS: File re-encrypted and .tmp swapped successfully
 * - FAILED: Explicit error during re-encryption
 * - (no entry after STARTED): Process interrupted/crashed
 */
public class PasswordChangeRecoveryLog {
    
    private final File logFile;
    private FileWriter writer;
    
    // Recovery state
    private Instant startTime;
    private int totalFiles;
    private int batchSize;
    private final Map<String, FileRecoveryState> fileStates = new LinkedHashMap<>();
    
    public PasswordChangeRecoveryLog(File logFile) {
        this.logFile = logFile;
    }
    
    /**
     * Initialize new recovery log
     */
    public void initialize(int totalFiles, int batchSize) throws IOException {
        this.totalFiles = totalFiles;
        this.batchSize = batchSize;
        this.startTime = Instant.now();
        
        writer = new FileWriter(logFile, false); // Overwrite
        writer.write("# Password Change Recovery Log\n");
        writer.write("# Started: " + startTime + "\n");
        writer.write("# Total Files: " + totalFiles + "\n");
        writer.write("# Batch Size: " + batchSize + "\n");
        writer.write("\n");
        writer.flush();
    }
    
    /**
     * Log file processing started
     */
    public void logStarted(String filePath, long fileSize) throws IOException {
        Instant now = Instant.now();
        String line = String.format("STARTED|%s|%s|%d\n", now, filePath, fileSize);
        writer.write(line);
        writer.flush();
    }
    
    /**
     * Log file processing succeeded
     */
    public void logSuccess(String filePath, long fileSize) throws IOException {
        Instant now = Instant.now();
        String line = String.format("SUCCESS|%s|%s|%d\n", now, filePath, fileSize);
        writer.write(line);
        writer.flush();
    }
    
    /**
     * Log file processing failed
     */
    public void logFailed(String filePath, long fileSize, String errorMessage) 
            throws IOException {
        Instant now = Instant.now();
        // Escape pipe characters in error message
        String safeError = errorMessage.replace("|", "\\|");
        String line = String.format("FAILED|%s|%s|%d|%s\n", 
            now, filePath, fileSize, safeError);
        writer.write(line);
        writer.flush();
    }
    
    /**
     * Mark log as completed (all files processed)
     */
    public void markComplete() throws IOException {
        writer.write("\n# COMPLETED: " + Instant.now() + "\n");
        writer.flush();
        close();
    }
    
    /**
     * Mark log as interrupted
     */
    public void markInterrupted() throws IOException {
        writer.write("\n# INTERRUPTED: " + Instant.now() + "\n");
        writer.flush();
        close();
    }
    
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
    
    /**
     * Parse existing recovery log
     */
    public static RecoveryAnalysis parseLog(File logFile) throws IOException {
        if (!logFile.exists()) {
            return null;
        }
        
        List<String> lines = Files.readAllLines(logFile.toPath());
        
        RecoveryAnalysis analysis = new RecoveryAnalysis();
        analysis.logFile = logFile;
        
        for (String line : lines) {
            line = line.trim();
            
            if (line.isEmpty() || line.startsWith("#")) {
                // Parse header comments
                if (line.startsWith("# Started:")) {
                    analysis.startTime = Instant.parse(
                        line.substring("# Started:".length()).trim());
                } else if (line.startsWith("# Total Files:")) {
                    analysis.totalFiles = Integer.parseInt(
                        line.substring("# Total Files:".length()).trim());
                } else if (line.startsWith("# COMPLETED:")) {
                    analysis.completed = true;
                } else if (line.startsWith("# INTERRUPTED:")) {
                    analysis.interrupted = true;
                }
                continue;
            }
            
            // Parse log entry: STATUS|timestamp|filepath|size[|error]
            String[] parts = line.split("\\|", 5);
            if (parts.length < 4) {
                continue; // Invalid line
            }
            
            String status = parts[0];
            Instant timestamp = Instant.parse(parts[1]);
            String filePath = parts[2];
            long fileSize = Long.parseLong(parts[3]);
            String error = parts.length > 4 ? parts[4] : null;
            
            FileRecoveryState state = analysis.fileStates
                .computeIfAbsent(filePath, k -> new FileRecoveryState(filePath));
            
            switch (status) {
                case "STARTED":
                    state.started = true;
                    state.startTime = timestamp;
                    state.fileSize = fileSize;
                    analysis.filesStarted++;
                    break;
                    
                case "SUCCESS":
                    state.succeeded = true;
                    state.endTime = timestamp;
                    analysis.filesSucceeded++;
                    break;
                    
                case "FAILED":
                    state.failed = true;
                    state.endTime = timestamp;
                    state.errorMessage = error;
                    analysis.filesFailed++;
                    break;
            }
        }
        
        // Identify interrupted files (STARTED but no SUCCESS/FAILED)
        for (FileRecoveryState state : analysis.fileStates.values()) {
            if (state.started && !state.succeeded && !state.failed) {
                analysis.filesInterrupted++;
            }
        }
        
        return analysis;
    }
    
    /**
     * File recovery state
     */
    public static class FileRecoveryState {
        public final String filePath;
        public boolean started = false;
        public boolean succeeded = false;
        public boolean failed = false;
        public Instant startTime;
        public Instant endTime;
        public long fileSize;
        public String errorMessage;
        
        public FileRecoveryState(String filePath) {
            this.filePath = filePath;
        }
        
        public boolean isInterrupted() {
            return started && !succeeded && !failed;
        }
        
        public boolean isCompleted() {
            return succeeded || failed;
        }
    }
    
    /**
     * Recovery analysis result
     */
    public static class RecoveryAnalysis {
        public File logFile;
        public Instant startTime;
        public int totalFiles;
        public int filesStarted = 0;
        public int filesSucceeded = 0;
        public int filesFailed = 0;
        public int filesInterrupted = 0;
        public boolean completed = false;
        public boolean interrupted = false;
        public Map<String, FileRecoveryState> fileStates = new LinkedHashMap<>();
        
        public boolean needsRecovery() {
            return !completed && (filesInterrupted > 0 || filesFailed > 0);
        }
        
        public String getSummary() {
            return String.format(
                "Password Change Recovery Analysis:\n" +
                "Started: %s\n" +
                "Total Files: %d\n" +
                "Files Started: %d\n" +
                "Files Succeeded: %d\n" +
                "Files Failed: %d\n" +
                "Files Interrupted: %d\n" +
                "Status: %s\n",
                startTime,
                totalFiles,
                filesStarted,
                filesSucceeded,
                filesFailed,
                filesInterrupted,
                completed ? "Completed" : 
                    (interrupted ? "Interrupted" : "Unknown")
            );
        }
    }
}