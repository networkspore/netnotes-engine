package io.netnotes.engine.messaging;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReader;
import io.netnotes.engine.noteBytes.NoteBytesWriter;


public class StreamUtils {

    public static final int BUFFER_SIZE = 128 * 1024;
    public static final int PIPE_BUFFER_SIZE = 1024 * 1024;


    public static class StreamProgressTracker {
        private final AtomicLong bytesProcessed = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile long totalBytes = -1; // -1 means unknown
        
        public long getBytesProcessed() { return bytesProcessed.get(); }
        public void addBytesProcessed(long bytes) { bytesProcessed.addAndGet(bytes); }
        public boolean isCancelled() { return cancelled.get(); }
        public void cancel() { cancelled.set(true); }
        public void setTotalBytes(long total) { this.totalBytes = total; }
        public long getTotalBytes() { return totalBytes; }
        public double getProgress() { 
            return totalBytes > 0 ? (double) bytesProcessed.get() / totalBytes : -1; 
        }
    }



    public static void streamCopy(InputStream input, OutputStream output, 
                StreamProgressTracker progressTracker) throws IOException {
        byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
        int length = 0;
        
        while ((length = input.read(buffer)) != -1) {
            if (progressTracker != null && progressTracker.isCancelled()) {
                throw new IOException("Operation cancelled");
            }
            
            output.write(buffer, 0, length);
            
            if (progressTracker != null) {
                progressTracker.addBytesProcessed(length);
            }
        }
    }

    public static void pipedOutputDuplicate(PipedOutputStream pipedOutput, PipedOutputStream output1, PipedOutputStream output2, 
                StreamProgressTracker progressTracker) throws IOException {
        byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
        int length = 0;
        
        try(PipedInputStream input = new PipedInputStream(pipedOutput, PIPE_BUFFER_SIZE)){
            while ((length = input.read(buffer)) != -1) {
                if (progressTracker != null && progressTracker.isCancelled()) {
                    throw new IOException("Operation cancelled");
                }
                output1.write(buffer, 0, length);
                output2.write(buffer, 0, length);
                if (progressTracker != null) {
                    progressTracker.addBytesProcessed(length);
                }
            }
        }
    }

    /**
     * Write error message to reply stream
     */
    public static void writeMessageToStreamAndClose(PipedOutputStream stream, NoteBytes message) throws IOException {
        try( 
            NoteBytesEphemeral errorReply = message instanceof NoteBytesEphemeral ? (NoteBytesEphemeral) message : new NoteBytesEphemeral(message);
            NoteBytesWriter writer = new NoteBytesWriter(stream);
        ) {

            writer.write(errorReply);
        }
    }


    public static void safeClose(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                // Log but don't throw - we're cleaning up
                System.err.println("Warning: Error closing resource: " + e.getMessage());
            }
        }
    }


    public static CompletableFuture<Void> readMessageHeaderExample(
            NoteBytesReadOnly identityPrivateKey,
            PipedOutputStream encryptedInputStream,
            PipedOutputStream decryptedOutputStream,
            ExecutorService execService
        ) throws IOException {

        PipedInputStream inputStream = new PipedInputStream(encryptedInputStream, StreamUtils.PIPE_BUFFER_SIZE);

        CompletableFuture<MessageHeader> headerFuture = CompletableFuture.supplyAsync(() -> {
            try {
                NoteBytesReader reader = new NoteBytesReader(inputStream);
                return MessageHeader.readHeader(reader);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, execService);

        
        return headerFuture.thenCompose(header -> {
            if (header instanceof SecureMessageV1) {
                return SecureMessageV1.decryptStreamToStream(
                    (SecureMessageV1) header,
                    identityPrivateKey,
                    inputStream,
                    decryptedOutputStream,
                    null,
                    execService
                );
            } else {
                // unsupported header → fail fast
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(
                        new IllegalArgumentException("No compatible header found: " + header));
                return failed;
            }
        }).whenComplete((result, error) -> {
            // cleanup
      
            StreamUtils.safeClose(encryptedInputStream);
            StreamUtils.safeClose(decryptedOutputStream);
            
        });
    }
}
