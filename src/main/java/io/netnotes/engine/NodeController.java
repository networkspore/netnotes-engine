package io.netnotes.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.naming.NameNotFoundException;

import io.netnotes.engine.messaging.BasicMessageHandlerV1;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.messaging.TaskMessages;
import io.netnotes.engine.messaging.NoteMessaging.General;
import io.netnotes.engine.noteBytes.ByteDecoding;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesConcurrentMapEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesMapEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesPair;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesWriter;
import io.netnotes.engine.noteBytes.NoteSerializable;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;

public class NodeController {

    private final AppData appData;
    private final NoteBytesReadOnly controllerId;
    private final Map<NoteBytesReadOnly, Node> nodeRegistry = new ConcurrentHashMap<>();
    
    public NodeController(NoteBytesReadOnly controllerId, AppData appData) {
        this.appData = appData;
        this.controllerId = controllerId;
    }
    
    // Core interface that nodes get - just stream routing
    public NodeControllerInterface getNodeControllerInterface(NoteBytesReadOnly nodeId) {
        return new StreamingNodeControllerInterface(nodeId);
    }
    

    // Route message to specific node
    private CompletableFuture<Void> routeToNode(NoteBytes fromId, NoteBytes toId, 
                                                          PipedOutputStream messageStream, 
                                                          PipedOutputStream replyStream) {
        Node targetNode = nodeRegistry.get(toId);
        if (targetNode == null) {
            return CompletableFuture.failedFuture(new NameNotFoundException("Node not found: " + toId));
        }
        try {
            return targetNode.receiveRawMessage(messageStream, replyStream);
   
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private ExecutorService getExecService(){
        return appData.getExecService();
    }

    /**
     * Stream-based broadcast method that handles dual streams per recipient
     * @param fromId The sender's ID
     * @param toIds Array of recipient IDs  
     * @param messageBytes The message content as byte array
     * @return CompletableFuture containing a map of recipient ID -> reply/error
     */
    private CompletableFuture<NoteBytesConcurrentMapEphemeral> broadcastMessage(
            NoteBytesReadOnly fromId, 
            NoteBytesArrayReadOnly toIds, 
            byte[] messageBytes) {
        
        NoteBytesReadOnly[] recipients = toIds.getAsReadOnlyArray();
        NoteBytesConcurrentMapEphemeral resultMap = new NoteBytesConcurrentMapEphemeral();
        
        List<CompletableFuture<Void>> recipientFutures = new ArrayList<>();
        
        for (NoteBytesReadOnly recipientId : recipients) {
            CompletableFuture<Void> recipientFuture = processRecipient(recipientId, messageBytes, resultMap);
            recipientFutures.add(recipientFuture);
        }
        
        // Wait for all recipients to complete
        return CompletableFuture.allOf(recipientFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> resultMap);
    }

    /**
     * Process a single recipient with dual streams
     */
    private CompletableFuture<Void> processRecipient(
            NoteBytesReadOnly recipientId, 
            byte[] messageBytes, 
            NoteBytesConcurrentMapEphemeral resultMap) {
        
        Node targetNode = nodeRegistry.get(recipientId);
        
        if (targetNode == null) {
            // Handle missing node immediately
            NoteBytesEphemeral errorReply = createErrorReply("Node not found: " + recipientId);
            resultMap.put(recipientId.copy(), errorReply);
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            // Create dual streams for this recipient
            PipedOutputStream messageOut = new PipedOutputStream();
            PipedOutputStream replyOut = new PipedOutputStream(); 
            PipedInputStream replyIn = new PipedInputStream(replyOut);
            
            // Start the message sending operation (this returns void now)
            CompletableFuture<Void> sendFuture = CompletableFuture.runAsync(() -> {
                try {
                    targetNode.receiveRawMessage(messageOut, replyOut);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to send to node: " + recipientId, e);
                }
            });
            
            // Start writing message bytes to the output stream
            CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
                try (PipedOutputStream out = messageOut) {
                    out.write(messageBytes);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write message for: " + recipientId, e);
                }
            }, getExecService());
            
            // Start collecting the reply from the input stream
            CompletableFuture<Void> replyFuture = CompletableFuture.runAsync(() -> {
                try {
                    NoteBytesEphemeral reply = collectReplyFromStream(replyIn);
                    resultMap.put(recipientId.copy(), reply);
                } catch (Exception e) {
                    NoteBytesEphemeral errorReply = createErrorReply("Failed to read reply from: " + recipientId + " - " + e.getMessage());
                    resultMap.put(recipientId.copy(), errorReply);
                }
            }, getExecService());
            
            // Combine all three operations for this recipient
            return CompletableFuture.allOf(sendFuture, writeFuture, replyFuture);
            
        } catch (Exception e) {
            // Handle any setup errors
            NoteBytesEphemeral errorReply = createErrorReply("Setup error for: " + recipientId + " - " + e.getMessage());
            resultMap.put(recipientId.copy(), errorReply);
            return CompletableFuture.completedFuture(null);
        }
    }
  
    /**
     * Collect reply data from the reply input stream
     */
    private NoteBytesEphemeral collectReplyFromStream(PipedInputStream replyIn) throws IOException {
        try (ByteArrayOutputStream replyBuffer = new ByteArrayOutputStream();
            PipedInputStream input = replyIn) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = input.read(buffer)) != -1) {
                replyBuffer.write(buffer, 0, bytesRead);
            }
            
            return new NoteBytesEphemeral(replyBuffer.toByteArray());
        }
    }

    private CompletableFuture<NoteBytesConcurrentMapEphemeral> broadcastMessageAlternative(
        NoteBytesReadOnly fromId,
        NoteBytesArrayReadOnly toIds,
        byte[] messageBytes
    ) {
        
        NoteBytesReadOnly[] recipients = toIds.getAsReadOnlyArray();
        NoteBytesConcurrentMapEphemeral resultMap = new NoteBytesConcurrentMapEphemeral();
        AtomicInteger completionCounter = new AtomicInteger(0);
        int totalRecipients = recipients.length;
        
        CompletableFuture<NoteBytesConcurrentMapEphemeral> finalResult = new CompletableFuture<>();
        
        for (NoteBytesReadOnly recipientId : recipients) {
            PipedOutputStream individualOut = new PipedOutputStream();
            PipedOutputStream individualReply = new PipedOutputStream();
            
            Node targetNode = nodeRegistry.get(recipientId);
            
            if (targetNode != null) {
                // Start the routing to this single node
                CompletableFuture.runAsync(() -> {
                    try {
                        targetNode.receiveRawMessage(individualOut, individualReply);
                    } catch (Exception e) {
                        // Error will be caught in reply collection
                    }
                });
                
                // Write message bytes to this recipient's stream  
                CompletableFuture.runAsync(() -> {
                    try (PipedOutputStream out = individualOut) {
                        out.write(messageBytes);
                    } catch (IOException e) {
                        // Handle write error
                    }
                });
                
                // Collect individual reply
                CompletableFuture.runAsync(() -> {
                    collectIndividualReplyAsync(recipientId, individualReply, resultMap, 
                        () -> handleReplyCompletion(completionCounter, totalRecipients, resultMap, finalResult));
                });
                
            } else {
                // Handle not found case
                CompletableFuture.runAsync(() -> {
                    writeNotFoundReply(recipientId, resultMap,
                        () -> handleReplyCompletion(completionCounter, totalRecipients, resultMap, finalResult));
                });
            }
        }
        
        return finalResult;
    }


    /**
     * Collect individual reply asynchronously
     */
    private void collectIndividualReplyAsync(
            NoteBytesReadOnly recipientId,
            PipedOutputStream replyOut,
            NoteBytesConcurrentMapEphemeral resultMap,
            Runnable onComplete) {
        
        try (PipedInputStream replyIn = new PipedInputStream(replyOut)) {
            NoteBytesEphemeral reply = collectReplyFromStream(replyIn);
            resultMap.put(recipientId.copy(), reply);
        } catch (Exception e) {
            NoteBytesEphemeral errorReply = createErrorReply("Failed to collect reply from: " + recipientId);
            resultMap.put(recipientId.copy(), errorReply);
        } finally {
            onComplete.run();
        }
    }


    /**
     * Handle not found case
     */
    private void writeNotFoundReply(
            NoteBytesReadOnly recipientId,
            NoteBytesConcurrentMapEphemeral resultMap,
            Runnable onComplete) {
        
        try {
            NoteBytesEphemeral errorReply = createErrorReply("Node not found: " + recipientId);
            resultMap.put(recipientId.copy(), errorReply);
        } finally {
            onComplete.run();
        }
    }

    /**
     * Handle completion of individual replies
     */
    private void handleReplyCompletion(
            AtomicInteger counter,
            int total,
            NoteBytesConcurrentMapEphemeral resultMap,
            CompletableFuture<NoteBytesConcurrentMapEphemeral> finalResult) {
        
        if (counter.incrementAndGet() == total) {
            finalResult.complete(resultMap);
        }
    }

    /**
     * Helper methods
     */

    private NoteBytesEphemeral createErrorReply(String errorMessage) {
        return new NoteBytesEphemeral(TaskMessages.getResultMessage(General.ERROR, errorMessage));
    }




    private class StreamingNodeControllerInterface implements NodeControllerInterface {
        private final NoteBytesReadOnly nodeId;
        private static final long MAX_MESSAGE_SIZE = 50 * 1024 * 1024; // 50MB default
        private static final long MAX_TOTAL_BROADCAST_SIZE = 500 * 1024 * 1024; // 500MB total (50MB * 10 recipients)
        private static final int MAX_RECIPIENTS = 1000; // Prevent broadcast bombs


        public StreamingNodeControllerInterface(NoteBytesReadOnly nodeId) {
            this.nodeId = nodeId;
        }
        
        @Override
        public ExecutorService getExecService() {
            return appData.getExecService();
        }
        
        @Override
        public CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path) {
            return appData.getNoteFileRegistry().getNoteFile(path);
        }
        
        @Override
        public NoteBytesReadOnly getControllerId() {
            return controllerId;
        }
        
   
        // Send message to specific node
        @Override
        public CompletableFuture<Void> sendMessage(NoteBytesReadOnly toId, PipedOutputStream messageStream, PipedOutputStream replyStream) {
            return routeToNode(this.nodeId, toId, messageStream, replyStream);
        }
        
      
      

        @Override
        public CompletableFuture<Void> sendMessage(NoteBytesArrayReadOnly toIds, 
        PipedOutputStream messageStream, 
        PipedOutputStream replyStream) {
            
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
                }, getExecService())
                .thenCompose(messageBytes -> {
                    // At this point we know the message is within limits
                    return broadcastMessage(this.nodeId, toIds, messageBytes);
                })
                .thenCompose(resultMap -> {
                    // Serialize and write response to reply stream
                    return CompletableFuture.runAsync(() -> {
                        try(NoteBytesConcurrentMapEphemeral map = resultMap; 
                           NoteBytesEphemeral mapEphemeral = map.getNoteBytesEphemeral();
                        ) {
                            // Serialize the result map
                             NoteBytesObject object = BasicMessageHandlerV1.createHeader(
                                    controllerId, 
                                    NoteMessaging.General.BROADCAST_RESULT, 
                                    mapEphemeral
                                   );
                            StreamUtils.writeMessageToStreamAndClose(replyStream, object);
                            
                        } catch (Exception e) {
                            // If there's an error, try to write error response
                            try {
                                  NoteBytesObject object = BasicMessageHandlerV1.createHeader(
                                    controllerId, 
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
                    }, getExecService());
                });
        }


        
        @Override
        public CompletableFuture<Void> unregisterNode() {
            return CompletableFuture.runAsync(() -> {
                nodeRegistry.remove(this.nodeId);
            }, appData.getExecService());
        }
        
        private CompletableFuture<NoteBytes> resolveNodeId(NoteStringArrayReadOnly path) {
            // Implementation depends on your path resolution strategy
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Path resolution not implemented"));
        }
    }
}