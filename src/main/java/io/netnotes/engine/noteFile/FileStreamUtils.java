package io.netnotes.engine.noteFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;

import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.messaging.TaskMessages;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;

public class FileStreamUtils {

    private static final int BUFFER_SIZE = 64 * 1024;

    public static String getNewUUIDFilePath(File dataDir){
        return dataDir.getAbsolutePath() + "/" + NoteUUID.createSafeUUID128();
    }

    public static class BulkUpdateResult {
        private final int successCount;
        private final int failureCount;
        private final List<String> failedFiles;
        
        public BulkUpdateResult(int successCount, int failureCount, List<String> failedFiles) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.failedFiles = new ArrayList<>(failedFiles);
        }
        
        public int getSuccessCount() {
            return successCount;
        }
        
        public int getFailureCount() {
            return failureCount;
        }
        
        public List<String> getFailedFiles() {
            return Collections.unmodifiableList(failedFiles);
        }
        
        public int getTotalFiles() {
            return successCount + failureCount;
        }
        
        public boolean isAllSuccessful() {
            return failureCount == 0;
        }
        
        @Override
        public String toString() {
            return String.format("BulkUpdateResult{total=%d, success=%d, failed=%d}", 
                            getTotalFiles(), successCount, failureCount);
        }
    }

    public enum UpdateStrategy {
        SEQUENTIAL("Process files one at a time - safest for disk space"),
        BATCHED("Process files in small batches - balanced approach"),
        PARALLEL("Process all files simultaneously - fastest but uses most disk space");
        
        private final String description;
        
        UpdateStrategy(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        @Override
        public String toString() {
            return name() + ": " + description;
        }
    }


    public static class BulkUpdateConfig {
        private final UpdateStrategy strategy;
        private final int batchSize;
        private final double diskSpaceBuffer; // Percentage of free space to keep as buffer
        
        public BulkUpdateConfig(UpdateStrategy strategy, int batchSize, double diskSpaceBuffer) {
            this.strategy = strategy;
            this.batchSize = Math.max(1, batchSize);
            this.diskSpaceBuffer = Math.max(0.1, Math.min(0.9, diskSpaceBuffer)); // 10-90%
        }
        
        public static BulkUpdateConfig conservative() {
            return new BulkUpdateConfig(UpdateStrategy.SEQUENTIAL, 1, 0.5); // Keep 50% free space
        }
        
        public static BulkUpdateConfig balanced() {
            return new BulkUpdateConfig(UpdateStrategy.BATCHED, 5, 0.3); // Keep 30% free space
        }
        
        public static BulkUpdateConfig aggressive() {
            return new BulkUpdateConfig(UpdateStrategy.PARALLEL, Integer.MAX_VALUE, 0.1); // Keep 10% free space
        }
        
        public UpdateStrategy getStrategy() { return strategy; }
        public int getBatchSize() { return batchSize; }
        public double getDiskSpaceBuffer() { return diskSpaceBuffer; }
        
        @Override
        public String toString() {
            return String.format("BulkUpdateConfig{strategy=%s, batchSize=%d, diskSpaceBuffer=%.1f%%}", 
                            strategy, batchSize, diskSpaceBuffer * 100);
        }
    }

    


    // Disk space validation result
    



    public static void writeFileBytes(File file, byte[] bytes) throws IOException{
        try(
            OutputStream fileOutputStream = Files.newOutputStream(file.toPath());
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        ){
            byte[] buffer = new byte[BUFFER_SIZE];
            int length = 0;
            
            while((length = inputStream.read(buffer)) != 1){
                fileOutputStream.write(buffer, 0, length);
            }
        }
    }
    

    public static void saveEncryptedFile(File file, byte[] bytes, SecretKey secretKey) throws Exception{

        try(
            OutputStream fileOutputStream = Files.newOutputStream(file.toPath());
        ){
            
            byte[] outIV = RandomService.getIV();
            Cipher encryptCipher = CryptoService.getAESEncryptCipher(outIV, secretKey);


            fileOutputStream.write(outIV);

            try(
                CipherOutputStream outputStream = new CipherOutputStream(fileOutputStream, encryptCipher);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            ){
                byte[] buffer = new byte[BUFFER_SIZE];
                int length = 0;
                while((length = inputStream.read(buffer)) != 1){
                    outputStream.write(buffer, 0, length);
                }
            }
        }
    
    }




    public static boolean updateFileEncryption(SecretKey oldAppKey, SecretKey newAppKey, File file, File tmpFile) throws Exception {
        if(file != null && file.isFile()){
            
            Path filePath = file.toPath();
            Path tmpPath = tmpFile.toPath();

            try(
                InputStream fileInputStream = Files.newInputStream(filePath);
                OutputStream fileOutputStream = Files.newOutputStream(tmpPath);
                NoteBytesReader initialReader = new NoteBytesReader(fileInputStream);
            ){
                
                byte[] oldIV = initialReader.readByteAmount(12);
                byte[] outIV = RandomService.getIV();

                Cipher decryptCipher = CryptoService.getAESDecryptCipher(oldIV, newAppKey);
                Cipher encryptCipher = CryptoService.getAESEncryptCipher(outIV, newAppKey);

                fileOutputStream.write(outIV);

                try(
                    CipherInputStream inputStream = new CipherInputStream(fileInputStream, decryptCipher);
                    CipherOutputStream outputStream = new CipherOutputStream(fileOutputStream, encryptCipher);
                ){
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int length = 0;
                    while((length = inputStream.read(buffer)) != 1){
                        outputStream.write(buffer, 0, length);
                    }
                }
            }
        

            Files.move(tmpPath, filePath, StandardCopyOption.REPLACE_EXISTING);

            return true;
        }
        return false;
    }



    public static boolean decryptFileToFile(SecretKey appKey, File encryptedFile, File decryptedFile) throws Exception{
        if(encryptedFile != null && encryptedFile.isFile() && encryptedFile.length() > 12){
            
            try(
                InputStream inputStream = Files.newInputStream(encryptedFile.toPath());
                NoteBytesReader reader = new NoteBytesReader(inputStream);
                OutputStream outStream = Files.newOutputStream(decryptedFile.toPath());
            ){
                
                byte[] iV = reader.readByteAmount(CryptoService.AES_IV_SIZE);

                Cipher cipher = CryptoService.getAESDecryptCipher(iV, appKey);

                long fileSize = encryptedFile.length();
                int bufferSize = fileSize < (8 * 1024) ? (int) fileSize :(8 * 1024);

                byte[] buffer = new byte[bufferSize];
                byte[] decryptedBuffer;
                int length = 0;
                long decrypted = 0;

                while ((length = inputStream.read(buffer)) != -1) {
                    decryptedBuffer = cipher.update(buffer, 0, length);
                    if(decryptedBuffer != null){
                        outStream.write(decryptedBuffer);
                    }
                    decrypted += length;
                }

                decryptedBuffer = cipher.doFinal();

                if(decryptedBuffer != null){
                    outStream.write(decryptedBuffer);
                }

                if(decrypted == fileSize){
                    return true;
                }
            }

      
        }
        return false;
    }

  
    
    public static byte[] decryptFileToBytes(SecretKey appKey, File file) throws Exception{
        if(file != null && file.isFile()){
            
            try(
                InputStream inputStream = Files.newInputStream(file.toPath());
                NoteBytesReader reader = new NoteBytesReader(inputStream);
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ){

                if(!file.exists() || !file.isFile()){
                    return null;
                }
                long fileSize = file.length();
                if(fileSize < CryptoService.AES_IV_SIZE){
                    return new byte[0];
                }

                byte[] iV = reader.readByteAmount(CryptoService.AES_IV_SIZE);

               
                Cipher cipher = CryptoService.getAESDecryptCipher(iV, appKey);

                
                int bufferSize = fileSize < (long) StreamUtils.BUFFER_SIZE ? (int) fileSize : StreamUtils.BUFFER_SIZE;

                byte[] buffer = new byte[bufferSize];
             
                int length = 0;

                try(CipherInputStream inputCipher = new CipherInputStream(inputStream, cipher)){

                    while ((length = inputCipher.read(buffer)) != -1) {
                        outStream.write(buffer, 0, length);
                    }

                    return outStream.toByteArray();
               }
            }
        }
        return null;

    }

    public static CompletableFuture<NoteBytesObject> saveEncryptedFile( File file, SecretKey secretKey, PipedOutputStream pipedOutputStream){
        return CompletableFuture.supplyAsync(() -> {
            try(
                PipedInputStream inputStream = new PipedInputStream(pipedOutputStream);
                OutputStream fileOutputStream = Files.newOutputStream(file.toPath());
            ){
                
                byte[] outIV = RandomService.getIV();
                Cipher encryptCipher = CryptoService.getAESEncryptCipher(outIV, secretKey);

                fileOutputStream.write(outIV);
                
                try(CipherOutputStream outputStream = new CipherOutputStream(fileOutputStream, encryptCipher)){
                    long bytesWritten = 0;
                    int length = 0;  
                    byte[] readBuffer = new byte[StreamUtils.BUFFER_SIZE];
                    while((length = inputStream.read(readBuffer)) != -1){
                        outputStream.write(readBuffer, 0, length);
                        bytesWritten += length;
                    }

                    return TaskMessages.createSuccessResult("File length:" + bytesWritten);
                }
            }catch (Exception e) {
                throw new RuntimeException(e);
            }

        });
    
    }

    public static CompletableFuture<NoteBytesObject> saveEncryptedFileSwap(
        File file,
        SecretKey secretKey,
        PipedOutputStream pipedOutputStream
    ) {
        
        return CompletableFuture.supplyAsync(() -> {
            final File tmpFile = new File(getNewUUIDFilePath(file.getParentFile()) + ".tmp");
            final Path tmpPath = tmpFile.toPath();

            long bytesWritten = 0;
            try (PipedInputStream inputStream = new PipedInputStream(pipedOutputStream)){
                try(
                    OutputStream fileOutputStream = Files.newOutputStream(tmpPath);
                ){
                    byte[] outIV = RandomService.getIV();
                    Cipher encryptCipher = CryptoService.getAESEncryptCipher(outIV, secretKey);
                    fileOutputStream.write(outIV);
                    
                    try (CipherOutputStream outputStream = new CipherOutputStream(fileOutputStream, encryptCipher)) {
                        int length;
                        byte[] readBuffer = new byte[StreamUtils.BUFFER_SIZE];
                        while ((length = inputStream.read(readBuffer)) != -1) {
                            outputStream.write(readBuffer, 0, length);
                            bytesWritten += length;
                        }
                    }
                } 
            } catch (Exception e) {
                try {
                    Files.deleteIfExists(tmpPath);
                } catch (IOException cleanupEx) {
                    e.addSuppressed(cleanupEx);
                }
                throw new RuntimeException("Failed to write encrypted data", e);
            }
            return FileSwapUtils.performAtomicFileSwap(file, tmpFile, bytesWritten);
        });
    }

    public static CompletableFuture<NoteBytesObject> performDecryption(
        File file, 
        PipedOutputStream pipedOutput,
        SecretKey secretKey,
        ExecutorService execService
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try (
                InputStream fileInputStream = Files.newInputStream(file.toPath());
                NoteBytesReader reader = new NoteBytesReader(fileInputStream);
            ) {
           
                if (file.exists() && file.isFile() && file.length() > CryptoService.AES_IV_SIZE - 1) {
                    byte[] iV = reader.readByteAmount(CryptoService.AES_IV_SIZE);
                    
                    Cipher decryptCipher = CryptoService.getAESDecryptCipher(iV, secretKey);
                    
                    byte[] readBuffer = new byte[StreamUtils.BUFFER_SIZE];
                    int length;
                    int dataLength = 0;
                    try(CipherInputStream inputStream = new CipherInputStream(fileInputStream, decryptCipher)){

                        while ((length = inputStream.read(readBuffer)) != -1) {
                            pipedOutput.write(readBuffer, 0, length);
                        }
                    
                        return TaskMessages.createSuccessResult("Data length:" + dataLength);
                    }
                }
                
                return TaskMessages.createSuccessResult("File length: 0");
                
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    pipedOutput.close();
                } catch (IOException e) {
                    // Log close error
                }
            }
        }, execService);
    }
            

    public static void encryptPairToFile(File file, SecretKey secretKey, NoteBytesPair pair) throws Exception{
        try( 
            OutputStream fileOutputStream = Files.newOutputStream(file.toPath());
        ){
            byte[] outIV = RandomService.getIV();
            Cipher encryptCipher = CryptoService.getAESEncryptCipher(outIV, secretKey);

            fileOutputStream.write(outIV);
            
            try(NoteBytesWriter writer = new NoteBytesWriter(new CipherOutputStream(fileOutputStream, encryptCipher))){
                writer.write(pair);
            }
        }
    }


    public static void readFileToWriter(File tmpFile, NoteBytesWriter writer, SecretKey key) throws Exception {
        try (
           InputStream fileIn = Files.newInputStream(tmpFile.toPath());
        ) {
            byte[] iV = StreamUtils.readByteAmount(CryptoService.AES_IV_SIZE, fileIn);
            
            Cipher decryptCipher = CryptoService.getAESDecryptCipher(iV, key);
            try(CipherInputStream tmpIn = new CipherInputStream(fileIn, decryptCipher) ){
                byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = tmpIn.read(buffer)) != -1) {
                    writer.write(buffer, 0, bytesRead);
                }
            }
        }
        
    }



}