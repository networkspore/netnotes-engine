package io.netnotes.engine.messaging.header;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.noteBytes.collections.NoteBytesPairEphemeral;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.noteBytes.processing.NoteBytesReader;
import io.netnotes.noteBytes.processing.NoteBytesWriter;
import io.netnotes.noteBytes.processing.RandomService;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.streams.StreamUtils.StreamProgressTracker;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesEphemeral;
import io.netnotes.noteBytes.NoteBytesObjectEphemeral;
import io.netnotes.noteBytes.NoteBytesReadOnly;

public class SecureMessageV1 extends MessageHeader {

    public static class SecurityLevel{
        public static final NoteBytesReadOnly SECURITY_SIGNED = new NoteBytesReadOnly(new byte[]{0x01});
        public static final NoteBytesReadOnly SECURITY_SEALED = new NoteBytesReadOnly(new byte[]{0x02});
    }

    public static final NoteBytesReadOnly HEADER_KEY = new NoteBytesReadOnly(new byte[]{ 0x53, 0x48, 0x44, 0x52, 0x01}); // "SHDR1"


    public static final NoteBytesReadOnly SECURITY_LEVEL_KEY = new NoteBytesReadOnly(new byte[]{0x13});
    public static final NoteBytesReadOnly SENDER_PUBLIC_KEY =  new NoteBytesReadOnly(new byte[]{0x14});
    public static final NoteBytesReadOnly SIGNATURE_KEY = new NoteBytesReadOnly(new byte[]{0x15});
    public static final NoteBytesReadOnly DATA_LENGTH = new NoteBytesReadOnly(new byte[]{0x16});
    public static final NoteBytesReadOnly EPHEMERAL_PUBLIC_KEY = new NoteBytesReadOnly(new byte[]{0x17});
    public static final NoteBytesReadOnly NONCE_KEY = new NoteBytesReadOnly(new byte[]{0x18});
    public static final NoteBytesReadOnly ALGORITHM_KEY = new NoteBytesReadOnly(new byte[]{0x19});
    public static final NoteBytesReadOnly SALT_KEY = new NoteBytesReadOnly(new byte[]{0x20});


    private NoteBytes m_senderPublicKey = null;
    private NoteBytes m_signature = null;
    private NoteBytes m_dataLength = null;
    private NoteBytes m_securityLevel = null;
    private NoteBytes m_ephemeralPublicKey = null;
    private NoteBytes m_nonce = null;
    private NoteBytes m_salt = null;
    private NoteBytes m_algorithm = null;

    public SecureMessageV1(NoteBytesReader reader) throws EOFException, IOException{
        super(HEADER_KEY);
        NoteBytesMetaData headerMetaData = reader.nextMetaData();

        if(headerMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            throw new IOException("Invalid header body type");
        }

        int size = headerMetaData.getLength();
        
        if(size < (NoteBytesMetaData.STANDARD_META_DATA_SIZE *4) +2 ){
            throw new IOException("Header contents too small: " + size);
        }

        int bytesRemaining = size;

        while(bytesRemaining > 0){
            NoteBytesReadOnly key = reader.nextNoteBytesReadOnly();
            NoteBytesReadOnly value = reader.nextNoteBytesReadOnly();
            if(key == null || value == null){
                throw new IOException("Unexpected end of stream");
            }
            updateData(key, value);

            bytesRemaining -= (key.byteLength() + value.byteLength() + (NoteBytesMetaData.STANDARD_META_DATA_SIZE *2));
        }

        if(bytesRemaining < 0){
             throw new IOException("Corrupt header detected");
        }
    }

    public static NoteBytes readSignature(NoteBytesReader reader) throws EOFException, IOException{

        NoteBytes signatureKey = reader.nextNoteBytes();

        if(signatureKey == null){
            throw new EOFException("Signature key missing before end of stream");
        }
        if(!signatureKey.equals(SIGNATURE_KEY)){
            throw new IllegalArgumentException("Signature key invalid for SecurityHeaderV1");
        }

        NoteBytes signature = reader.nextNoteBytes();
        if(signature == null){
            throw new EOFException("Signature value missing before end of stream");
        }

        return signature;
    }

    public NoteBytes getData(NoteBytesReadOnly key){
        if(key.equals(SENDER_ID_KEY)){
            return getSenderId();
        }else if(key.equals(TIME_STAMP_KEY)){
            return getTimeStamp() ;
        }else if(key.equals(SECURITY_LEVEL_KEY)){
            return m_securityLevel;
        }else if(key.equals(SENDER_PUBLIC_KEY)){
            return m_senderPublicKey;
        }else if(key.equals(SIGNATURE_KEY)){
            return m_signature ;
        }else if(key.equals(DATA_LENGTH)){
            return m_dataLength;
        }else if(key.equals(EPHEMERAL_PUBLIC_KEY)){
            return m_ephemeralPublicKey;
        }else if(key.equals(NONCE_KEY)){
            return m_nonce;
        }else if(key.equals(SALT_KEY)){
            return m_salt;
        }else if(key.equals(ALGORITHM_KEY)){
            return m_algorithm;
        }

        throw new IllegalArgumentException("Argument unavailable in this version");
    }


    public void updateData(NoteBytesReadOnly key, NoteBytesReadOnly value){
        if(key.equals(SENDER_ID_KEY)){
            setSenderId(value);
        }else if(key.equals(SECURITY_LEVEL_KEY)){
            m_securityLevel = value;
        }else if(key.equals(TIME_STAMP_KEY)){
            setTimeStamp(value);
        }else if(key.equals(SENDER_PUBLIC_KEY)){
            m_senderPublicKey = value;
        }else if(key.equals(SIGNATURE_KEY)){
            m_signature = value;
        }else if(key.equals(DATA_LENGTH)){
            m_dataLength = value;
        }else if(key.equals(EPHEMERAL_PUBLIC_KEY)){
            m_ephemeralPublicKey = value;
        }else if(key.equals(NONCE_KEY)){
            m_nonce = value;
        }else if(key.equals(SALT_KEY)){
            m_salt = value;
        }else if(key.equals(ALGORITHM_KEY)){
            m_algorithm = value;
        }
    }


    public NoteBytes getSenderPublicKey() {
        return m_senderPublicKey;
    }

    public void setSenderPublicKey(NoteBytes senderPublicKey) {
        this.m_senderPublicKey = senderPublicKey;
    }

    public NoteBytes getSignature() {
        return m_signature;
    }

    public void setSignature(NoteBytes signature) {
        this.m_signature = signature;
    }

    public NoteBytes getDataLength() {
        return m_dataLength;
    }

    public void setDataLength(NoteBytes dataLength) {
        this.m_dataLength = dataLength;
    }

    public NoteBytes getSecurityLevel() {
        return m_securityLevel;
    }

    public void setSecurityLevel(NoteBytes securityLevel) {
        this.m_securityLevel = securityLevel;
    }

    public NoteBytes getEphemeralPublicKey() {
        return m_ephemeralPublicKey;
    }

    public void setEphemeralPublicKey(NoteBytes ephemeralPublicKey) {
        this.m_ephemeralPublicKey = ephemeralPublicKey;
    }

    public NoteBytes getNonce() {
        return m_nonce;
    }

    public void setNonce(NoteBytes nonce) {
        this.m_nonce = nonce;
    }

    public NoteBytes getSalt() {
        return m_salt;
    }

    public void setSalt(NoteBytes salt) {
        this.m_salt = salt;
    }

    public NoteBytes getAlgorithm() {
        return m_algorithm;
    }

    public void setAlgorithm(NoteBytes algorithm) {
        this.m_algorithm = algorithm;
    }




    public static NoteBytesPairEphemeral getSecuritySignedHeader(NoteBytes senderId, Ed25519PublicKeyParameters senderPublicKey, int dataLength){
        NoteBytesObjectEphemeral header = new NoteBytesObjectEphemeral(new NoteBytesPairEphemeral[]{
            new NoteBytesPairEphemeral(SENDER_ID_KEY, senderId),
            new NoteBytesPairEphemeral(SENDER_PUBLIC_KEY, senderPublicKey.getEncoded()),
            new NoteBytesPairEphemeral(SECURITY_LEVEL_KEY, SecurityLevel.SECURITY_SIGNED),
            new NoteBytesPairEphemeral(TIME_STAMP_KEY, System.currentTimeMillis()),
            new NoteBytesPairEphemeral(DATA_LENGTH, dataLength)
        });
        return new NoteBytesPairEphemeral(HEADER_KEY, header);
    }

    public static NoteBytesPairEphemeral getSecuritySignedFooter(byte[] signature){
        return new NoteBytesPairEphemeral(SIGNATURE_KEY, signature);
    }
    
    public static NoteBytesPairEphemeral getSecuritySealedHeader(NoteBytes senderId, X25519PublicKeyParameters senderPublicKey, X25519PublicKeyParameters ephemeralPublic, NoteBytes nonce, NoteBytes algorithm){
        try(
            NoteBytesPairEphemeral senderIdKey = new NoteBytesPairEphemeral(SENDER_ID_KEY, senderId);
            NoteBytesPairEphemeral securityLevel =  new NoteBytesPairEphemeral(SECURITY_LEVEL_KEY, SecurityLevel.SECURITY_SEALED);
            NoteBytesPairEphemeral timeStamp = new NoteBytesPairEphemeral(TIME_STAMP_KEY, System.currentTimeMillis());
            NoteBytesPairEphemeral senderPk = new NoteBytesPairEphemeral(SENDER_PUBLIC_KEY, senderPublicKey.getEncoded());
            NoteBytesPairEphemeral ephPk =  new NoteBytesPairEphemeral(EPHEMERAL_PUBLIC_KEY, ephemeralPublic.getEncoded());
            NoteBytesPairEphemeral nonceKey = new NoteBytesPairEphemeral(NONCE_KEY, nonce);
            NoteBytesPairEphemeral algo = new NoteBytesPairEphemeral(ALGORITHM_KEY, algorithm);
        ){
            return new NoteBytesPairEphemeral(HEADER_KEY, 
                new NoteBytesEphemeral(new NoteBytesPairEphemeral[]{
                    senderIdKey,
                    securityLevel,
                    timeStamp,
                    senderPk,
                    ephPk,
                    nonceKey,
                    algo
                })
            );
        }
    }

    public static NoteBytesPairEphemeral getSecuritySealedHeader(NoteBytes senderId, X25519PublicKeyParameters senderPublicKey, X25519PublicKeyParameters ephemeralPublic, NoteBytes nonce, NoteBytes algorithm, NoteBytes salt){
  
        try(
            NoteBytesPairEphemeral senderIdPair = new NoteBytesPairEphemeral(SENDER_ID_KEY, senderId);
            NoteBytesPairEphemeral securityLevelPair =  new NoteBytesPairEphemeral(SECURITY_LEVEL_KEY, SecurityLevel.SECURITY_SEALED);
            NoteBytesPairEphemeral timeStampPair = new NoteBytesPairEphemeral(TIME_STAMP_KEY, System.currentTimeMillis());
            NoteBytesPairEphemeral senderPkPair = new NoteBytesPairEphemeral(SENDER_PUBLIC_KEY, senderPublicKey.getEncoded());
            NoteBytesPairEphemeral ephemeralPkPair =  new NoteBytesPairEphemeral(EPHEMERAL_PUBLIC_KEY, ephemeralPublic.getEncoded());
            NoteBytesPairEphemeral noncePair = new NoteBytesPairEphemeral(NONCE_KEY, nonce);
            NoteBytesPairEphemeral algorithmPair = new NoteBytesPairEphemeral(ALGORITHM_KEY, algorithm);
            NoteBytesPairEphemeral saltPair = new NoteBytesPairEphemeral(SALT_KEY, salt);
        ){
            return new NoteBytesPairEphemeral(HEADER_KEY, 
                new NoteBytesEphemeral(new NoteBytesPairEphemeral[]{
                    senderIdPair,
                    securityLevelPair,
                    timeStampPair,
                    senderPkPair,
                    ephemeralPkPair,
                    noncePair,
                    algorithmPair,
                    saltPair,
                })
            );
        }
    }



    public static CompletableFuture<Void> beginSignedStream(
        NoteBytes senderId,
        Ed25519PrivateKeyParameters senderPrivateKey,
        Ed25519PublicKeyParameters senderPublicKey,
        int dataLength,
        PipedOutputStream startStream,
        PipedOutputStream outputEncryptedStream,
        ExecutorService execService
    ){
        return CompletableFuture.runAsync(() -> {

            try(
                NoteBytesPairEphemeral header = getSecuritySignedHeader(senderId, senderPublicKey, dataLength);
                PipedInputStream inputStream = new PipedInputStream(startStream, StreamUtils.PIPE_BUFFER_SIZE);
                NoteBytesWriter writer = new NoteBytesWriter(outputEncryptedStream);
            ){
                
                writer.write(header);
                
                Ed25519Signer signer = new Ed25519Signer();
            
                signer.init(true, senderPrivateKey);
                
                byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    writer.write(buffer, 0, bytesRead);
                    signer.update(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                writer.flush();
                if(totalBytes != dataLength){
                    throw new IllegalStateException("Message data length does not match header");
                }

                //generateSignature and writer footer
                writer.write(getSecuritySignedFooter(signer.generateSignature()));
            }catch(Exception e){
                throw new RuntimeException("Signed stream failed", e);
            }
        });
    }

  
    public static CompletableFuture<Void> beginEncryptedStream(
        NoteBytes senderId,
        X25519PrivateKeyParameters senderPrivateKey,
        X25519PublicKeyParameters senderPublicKey,
        X25519PublicKeyParameters recipientKey,
        PipedOutputStream startStream,
        PipedOutputStream outputEncryptedStream,
        ExecutorService execService
    ){
        return CompletableFuture.runAsync(() -> {

            X25519PrivateKeyParameters ephemeralPrivate = new X25519PrivateKeyParameters(RandomService.getSecureRandom());
            X25519PublicKeyParameters ephemeralPublic = ephemeralPrivate.generatePublicKey();
           
            final int keySize = CryptoService.CHACHA20_KEY_SIZE;
            final NoteBytesReadOnly algorithm = CryptoService.CHA_CHA_20_POLY_1305_ALGORITHM;
            final int nonceSize = CryptoService.CHACHA20_NONCE_SIZE;

            try(
                NoteBytesEphemeral nonce = new NoteBytesEphemeral(RandomService.getRandomBytes(nonceSize));
                NoteBytesEphemeral sharedSecret = new NoteBytesEphemeral(CryptoService.getX245519SharedSecret(ephemeralPrivate, recipientKey, keySize));
                PipedInputStream inputStream = new PipedInputStream(startStream, StreamUtils.PIPE_BUFFER_SIZE);
                NoteBytesWriter writer = new NoteBytesWriter(outputEncryptedStream);
                NoteBytesEphemeral key = new NoteBytesEphemeral(
                     CryptoService.deriveHKDFKey(
                        sharedSecret.get(), 
                        algorithm, 
                        keySize
                ));
                NoteBytesPairEphemeral header = getSecuritySealedHeader(senderId, senderPublicKey, ephemeralPublic, nonce, algorithm);
            ){
                writer.write(header);
                writer.flush();
                try (CipherOutputStream cipherOut = new CipherOutputStream(outputEncryptedStream, CryptoService.getChaCha20Poly1305Cipher(key, nonce, Cipher.ENCRYPT_MODE))) {
                    StreamUtils.streamCopy(inputStream, cipherOut, null);
                }

            }catch(Exception e){
                throw new RuntimeException("Encrypted stream failed", e);
            }
        }, execService);
    }


                
    public static void processSignedDecryption(
        SecureMessageV1 header,
        InputStream input,
        OutputStream output,
        StreamProgressTracker progressTracker
    ) throws IOException, InterruptedException, SecurityException, IllegalStateException {

        // Extract signature metadata
        NoteBytes senderPublicKeyReadOnly = header.getSenderPublicKey();

        NoteBytes lengthReadOnly = header.getDataLength();
        
        if (senderPublicKeyReadOnly == null || lengthReadOnly == null) {
            throw new SecurityException("Missing signature metadata");
        }
        byte type = lengthReadOnly.getType();
        
        byte[] senderPublicKeyBytes = senderPublicKeyReadOnly.get();
    
        long dataLength = type == NoteBytesMetaData.INTEGER_TYPE ? (long) lengthReadOnly.getAsInt() : lengthReadOnly.getAsLong();
        
        if (progressTracker != null) {
            progressTracker.setTotalBytes(dataLength);
        }
        
        // Stream verification
        Ed25519PublicKeyParameters publicKey = new Ed25519PublicKeyParameters(senderPublicKeyBytes, 0);
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, publicKey);
        
        byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
        long totalRead = 0;

        while (totalRead < dataLength) {
            if (progressTracker != null && progressTracker.isCancelled()) {
                throw new InterruptedException("Operation cancelled");
            }
            
            int toRead = (int) Math.min(StreamUtils.BUFFER_SIZE, dataLength - totalRead);
            int bytesRead = input.read(buffer, 0, toRead);
            if (bytesRead == -1) {
                throw new IOException("Unexpected end of stream");
            }
            
            verifier.update(buffer, 0, bytesRead);
            output.write(buffer, 0, bytesRead);
            totalRead += bytesRead;
            
            if (progressTracker != null) {
                progressTracker.addBytesProcessed(bytesRead);
            }
        }

        NoteBytesReader reader = new NoteBytesReader(input);

        try(NoteBytesEphemeral signature = new NoteBytesEphemeral(readSignature(reader))){
            if(!verifier.verifySignature(signature.get())){
                throw new SecurityException("Signature verification failed");
            }
        }catch(EOFException e){
            throw new SecurityException("Message reached end of stream before signature could be read", e);
        }catch(IOException e){
            throw new SecurityException("Failed to read signature after message data", e);
        }
        output.flush();
    }
    
    public static void processSealedDecryption(
        SecureMessageV1 header,
        InputStream input,
        OutputStream output,
        X25519PrivateKeyParameters privateKey,
        StreamProgressTracker progressTracker
    )  {
        //Uknown total size
        progressTracker.setTotalBytes(-1);
        // Extract encryption metadata
        NoteBytes headerEphemeralKey = header.getEphemeralPublicKey();
        NoteBytes nonce = header.getNonce();

        if (headerEphemeralKey == null || nonce == null) {
            throw new SecurityException("Missing encryption metadata");
        }
  
        // Recreate ephemeral public key
        X25519PublicKeyParameters ephemeralPublicKey =
                new X25519PublicKeyParameters(headerEphemeralKey.getBytes(), 0);

        // Derive shared secret using X25519 key agreement
        X25519Agreement agreement = new X25519Agreement();
        agreement.init(privateKey);
        byte[] sharedSecret = new byte[32];
        agreement.calculateAgreement(ephemeralPublicKey, sharedSecret, 0);
    
        // Perform streaming decryption
        try(
            NoteBytesEphemeral receivedPk = new NoteBytesEphemeral(headerEphemeralKey);
            NoteBytesEphemeral ephemeralSecret = new NoteBytesEphemeral(sharedSecret);
            NoteBytesEphemeral ephemeralDecryptionKey = new NoteBytesEphemeral( CryptoService.deriveHKDFKey(
                sharedSecret,
                CryptoService.CHA_CHA_20_POLY_1305_ALGORITHM,
                CryptoService.CHACHA20_KEY_SIZE
            ));
            CipherInputStream cipherInput = new CipherInputStream(input, 
                CryptoService.getChaCha20Poly1305Cipher(ephemeralDecryptionKey, nonce, Cipher.DECRYPT_MODE)
            );
        ) {
            
            StreamUtils.streamCopy(cipherInput, output, progressTracker);
            
        } catch(Exception e){
            throw new RuntimeException("Decryption failed", e);
        }
    }
    

     /*
      public static CompletableFuture<Void> readMessageHeaderExample(
            X25519PrivateKeyParameters identityPrivateKey,
            PipedOutputStream encryptedInputStream,
            PipedOutputStream decryptedOutputStream,
            StreamProgressTracker progressTracker,
            ExecutorService execService) throws IOException {

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
                return decryptStreamToStream(
                        (SecureMessageV1) header,
                        identityPrivateKey,
                        inputStream,
                        decryptedOutputStream,
                        progressTracker,
                        execService);
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
    public static CompletableFuture<Void> decryptStreamToStream(SecureMessageV1 header, 
        X25519PrivateKeyParameters privateKey, InputStream encryptedInputStream, OutputStream decryptedOutputStream,
        StreamProgressTracker progressTracker, ExecutorService execService
    ) {
        
        return CompletableFuture.runAsync(() -> {

            try {
                if(header.getHeaderType().equals(SecureMessageV1.HEADER_KEY)){
                    SecureMessageV1 securityHeaderV1 = (SecureMessageV1) header;
                    NoteBytes securityLevel = securityHeaderV1.getSecurityLevel();

                    if (securityLevel.equals(SecurityLevel.SECURITY_SIGNED)) {
                        processSignedDecryption(header,encryptedInputStream,decryptedOutputStream,progressTracker);
                    }  else if (securityLevel.equals(SecurityLevel.SECURITY_SEALED)) {
                        processSealedDecryption(header, encryptedInputStream, decryptedOutputStream, privateKey, progressTracker);
                    } else {
                        throw new IllegalArgumentException("Unknown security level: " + securityLevel);
                    }
                }else{
                    throw new IllegalArgumentException("SecurityHeaderV1 expected");
                }
                
            } catch (Exception e) {
                if (progressTracker != null) {
                    progressTracker.cancel();
                }
                throw new RuntimeException("Encryption failed", e);
            } 
        });  
    
    }*/
    
}
