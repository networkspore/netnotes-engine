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

import io.netnotes.engine.messaging.TypedMessageMap;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.NoteMessaging.General;
import io.netnotes.engine.messaging.task.TaskMessages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesConcurrentMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.utils.streams.StreamUtils;

public class NodeBroadcaster {
    public static final long MAX_MESSAGE_SIZE = 50 * 1024 * 1024; // 50MB default
    public static final long MAX_TOTAL_BROADCAST_SIZE = 500 * 1024 * 1024; // 500MB total (50MB * 10 recipients)
    public static final int MAX_RECIPIENTS = 1000; // Prevent broadcast bombs

    private final Map<NoteBytesReadOnly, INode> nodeRegistry;
    private final Executor exec;
    private final NoteBytesReadOnly broadcasterId;

    public NodeBroadcaster(Map<NoteBytesReadOnly, INode> nodeRegistry,
                       Executor exec,
                       NoteBytesReadOnly broadcasterId) {

        this.nodeRegistry = nodeRegistry;
        this.exec = exec;
        this.broadcasterId = broadcasterId;
    }

    public CompletableFuture<Void> sendMessage(NoteBytesReadOnly nodeId, 
        NoteBytesReadOnly[] toIds, 
        PipedOutputStream messageStream, 
        PipedOutputStream replyStream
    ) {
            
        return CompletableFuture
            .supplyAsync(() -> {
                // Early validation - check recipient count first (cheapest check)
                int recipientCount = toIds.length;
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
                    try{
                        //Serialize the results
                        NoteBytes noteBytes = resultMap.getNoteBytesObject();
                    
                        //Create boadcast result header
                        NoteBytesObject object = TypedMessageMap.createHeader(
                            this.broadcasterId, 
                            NoteMessaging.General.BROADCAST_RESULT, 
                            noteBytes
                        );

                        try(
                            NoteBytesWriter writer = new NoteBytesWriter(replyStream);
                        ){
                            writer.write(object);
                        }catch(IOException e){
                            throw new RuntimeException("Writing replies to reply stream failed", e);
                        }
                        
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process broadcast response", e);
                    }
                }, exec);
            });
    }

    private CompletableFuture<NoteBytesConcurrentMap> broadcastMessage(NoteBytesReadOnly fromId, 
        NoteBytesReadOnly[] recipients, byte[] messageBytes
    ) {

   
        NoteBytesConcurrentMap resultMap = new NoteBytesConcurrentMap();

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
            NoteBytesConcurrentMap resultMap) {

        INode targetNode = nodeRegistry.get(recipientId);

        if (targetNode == null) {
            resultMap.put(recipientId.copy(), TaskMessages.getTaskMessage(NoteMessaging.General.PROCESSING, General.ERROR, "Node not found"));
            return CompletableFuture.completedFuture(null);
        }

        try {
            PipedOutputStream messageOut = new PipedOutputStream();
            PipedOutputStream replyOut   = new PipedOutputStream();
         

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
                    byte[] bytes = StreamUtils.readOutputStream(replyOut);
                    resultMap.put(recipientId.copy(), new NoteBytes(bytes));
                } catch (Exception e) {
                    resultMap.put(recipientId.copy(),TaskMessages.createErrorMessage(NoteMessaging.General.PROCESSING, "Failed to read reply", e));
                }
            }, exec);

            return CompletableFuture.allOf(sendFuture, writeFuture, replyFuture);

        } catch (Exception e) {
            resultMap.put(recipientId.copy(),TaskMessages.createErrorMessage(NoteMessaging.General.PROCESSING, "Setup error for", e));
            return CompletableFuture.completedFuture(null);
        }
    }


}
