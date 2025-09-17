package io.netnotes.engine.crypto;


import io.netnotes.engine.messaging.MessageHeader;
import io.netnotes.engine.messaging.SecurityHeaderV1;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReader;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class CryptoStreamUtils {
    


    /*public static CompletableFuture<Void> writeEncryptedStreamToStream(
            MessageHeader header,
            NoteBytesReadOnly privateKey,
            PipedOutputStream dataOutputStream,
            PipedOutputStream outputEncryptedStream,
            ExecutorService execService,
            StreamProgressTracker tracker) throws IOException {
            
        PipedInputStream inputStream = new PipedInputStream(dataOutputStream, StreamUtils.PIPE_BUFFER_SIZE);

        if(header instanceof SecurityHeaderV1){
            return SecurityHeaderV1.writeEncryptedStreamToStream(
                (SecurityHeaderV1)header, 
                privateKey,
                inputStream, 
                outputEncryptedStream, 
                execService, 
                tracker);
        }else{
            return null;
        }
    }*/



    public static CompletableFuture<Void> readEncryptedStreamFromStream(
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
            if (header instanceof SecurityHeaderV1) {
                return SecurityHeaderV1.decryptStreamToStream(
                    (SecurityHeaderV1) header,
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