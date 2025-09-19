package io.netnotes.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import io.netnotes.engine.messaging.BasicMessageHandlerV1;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.messaging.TaskMessages;
import io.netnotes.engine.messaging.NoteMessaging.General;
import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesConcurrentMapEphemeral;

public class NodeBroadcaster {
    public static final long MAX_MESSAGE_SIZE = 50 * 1024 * 1024; // 50MB default
    public static final long MAX_TOTAL_BROADCAST_SIZE = 500 * 1024 * 1024; // 500MB total (50MB * 10 recipients)
    public static final int MAX_RECIPIENTS = 1000; // Prevent broadcast bombs

    private final Map<NoteBytesReadOnly, Node> nodeRegistry;
    private final Executor exec;
    private final NoteBytesReadOnly broadcasterId;

    public NodeBroadcaster(Map<NoteBytesReadOnly, Node> nodeRegistry,
                       Executor exec,
                       NoteBytesReadOnly broadcasterId) {

        this.nodeRegistry = nodeRegistry;
        this.exec = exec;
        this.broadcasterId = broadcasterId;
    }

    public CompletableFuture<Void> sendMessage(NoteBytesReadOnly nodeId, 
        NoteBytesArrayReadOnly toIds, 
        PipedOutputStream messageStream, 
        PipedOutputStream replyStream
    ) {
            
        return CompletableFuture
            .supplyAsync(() -> {
                // Early validation - check recipient count first (cheapest check)
                int recipientCount = toIds.getAsReadOnlyArray().length;
                if (recipientCount > MAX_RECIPIENTS) {
                    throw new RuntimeException("Too many recipients: " + recipientCount + " (max: " + MAX_RECIPIENTS + ")");
                }
                
                // Read message bytes from input stream with size limits
                try (PipedInputStream inputStream = new PipedInputStream(messageStream);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    
                    byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
                    int length = 0;
                    long totalBytesRead = 0;
                    
                    while ((length = inputStream.read(buffer)) != -1) {
                        totalBytesRead += length;
                        
                        // Check individual message size limit
                        if (totalBytesRead > MAX_MESSAGE_SIZE) {
                            throw new RuntimeException("Message too large: " + totalBytesRead + " bytes (max: " + MAX_MESSAGE_SIZE + ")");
                        }
                        
                        // Check total broadcast impact (message size × recipient count)
                        long totalBroadcastSize = totalBytesRead * recipientCount;
                        if (totalBroadcastSize > MAX_TOTAL_BROADCAST_SIZE) {
                            throw new RuntimeException("Total broadcast size too large: " + totalBroadcastSize + 
                                " bytes (" + totalBytesRead + " × " + recipientCount + " recipients, max: " + MAX_TOTAL_BROADCAST_SIZE + ")");
                        }
                        
                        outputStream.write(buffer, 0, length);
                    }
                    
                    return outputStream.toByteArray();
                    
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read message stream", e);
                }
            }, exec)
            .thenCompose(messageBytes -> {
                // At this point we know the message is within limits
                return broadcastMessage(nodeId, toIds, messageBytes);
            })
            .thenCompose(resultMap -> {
                // Serialize and write response to reply stream
                return CompletableFuture.runAsync(() -> {
                    try(NoteBytesConcurrentMapEphemeral map = resultMap; 
                        NoteBytesEphemeral mapEphemeral = map.getNoteBytesEphemeral();
                    ) {
                        // Serialize the result map
                            NoteBytesObject object = BasicMessageHandlerV1.createHeader(
                                this.broadcasterId, 
                                NoteMessaging.General.BROADCAST_RESULT, 
                                mapEphemeral
                                );
                        StreamUtils.writeMessageToStreamAndClose(replyStream, object);
                        
                    } catch (Exception e) {
                        // If there's an error, try to write error response
                        try {
                                NoteBytesObject object = BasicMessageHandlerV1.createHeader(
                                this.broadcasterId, 
                                NoteMessaging.General.ERROR, 
                                TaskMessages.createErrorMessage(
                                    "Stream reply", 
                                    "Failed to serialize broadcast results", 
                                    e));
                            StreamUtils.writeMessageToStreamAndClose(replyStream, object);
                        } catch (IOException ioEx) {
                            // Log or handle the fact that we couldn't even write the error
                            throw new RuntimeException("Failed to write error response", ioEx);
                        }
                        throw new RuntimeException("Failed to process broadcast response", e);
                    }
                }, exec);
            });
    }

    private CompletableFuture<NoteBytesConcurrentMapEphemeral> broadcastMessage(
            NoteBytesReadOnly fromId,
            NoteBytesArrayReadOnly toIds,
            byte[] messageBytes) {

        NoteBytesReadOnly[] recipients = toIds.getAsReadOnlyArray();
        NoteBytesConcurrentMapEphemeral resultMap = new NoteBytesConcurrentMapEphemeral();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (NoteBytesReadOnly recipientId : recipients) {
            futures.add(processRecipient(recipientId, messageBytes, resultMap));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> resultMap);
    }

    private CompletableFuture<Void> processRecipient(
            NoteBytesReadOnly recipientId,
            byte[] messageBytes,
            NoteBytesConcurrentMapEphemeral resultMap) {

        Node targetNode = nodeRegistry.get(recipientId);

        if (targetNode == null) {
            resultMap.put(recipientId.copy(), TaskMessages.getResultMessage(General.ERROR, "Node not found"));
            return CompletableFuture.completedFuture(null);
        }

        try {
            PipedOutputStream messageOut = new PipedOutputStream();
            PipedOutputStream replyOut   = new PipedOutputStream();
            PipedInputStream replyIn     = new PipedInputStream(replyOut);

            CompletableFuture<Void> sendFuture = CompletableFuture.runAsync(() -> {
                try {
                    targetNode.receiveRawMessage(messageOut, replyOut);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to send to node: " + recipientId, e);
                }
            });

            CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
                try (PipedOutputStream out = messageOut) {
                    out.write(messageBytes);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write message for: " + recipientId, e);
                }
            }, exec);

            CompletableFuture<Void> replyFuture = CompletableFuture.runAsync(() -> {
                try {
                    NoteBytesEphemeral reply = collectReplyFromStream(replyIn);
                    resultMap.put(recipientId.copy(), reply);
                } catch (Exception e) {
                    resultMap.put(recipientId.copy(),TaskMessages.createErrorMessage("processRecipient", "Failed to read reply", e));
                }
            }, exec);

            return CompletableFuture.allOf(sendFuture, writeFuture, replyFuture);

        } catch (Exception e) {
            resultMap.put(recipientId.copy(),TaskMessages.createErrorMessage("processRecipient", "Setup error for", e));
            return CompletableFuture.completedFuture(null);
        }
    }

    private NoteBytesEphemeral collectReplyFromStream(PipedInputStream replyIn) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             PipedInputStream input = replyIn) {

            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }

            return new NoteBytesEphemeral(buffer.toByteArray());
        }
    }
}
