package io.netnotes.engine.crypto;

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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;

import org.apache.commons.io.FileUtils;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.messaging.TaskMessages;
import io.netnotes.engine.noteBytes.collections.NoteBytesMapEphemeral;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.utils.Utils;
import io.netnotes.engine.utils.FileSwapUtils;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

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
    public static class DiskSpaceValidation {
        private boolean m_isValid;
        private final int m_numberOfFiles;
        private final long m_totalFileSizes;
        private final long m_requiredSpace;
        private final long m_availableSpace;
        private final long m_bufferSpace;
        
        public DiskSpaceValidation(
            boolean isSpaceAvaialble,
            int numberOfFiles,
            long totalFileSizes,
            long requiredSpace,
            long availableSpace,
            long bufferSpace
        ) {
            m_isValid = isSpaceAvaialble;
            m_numberOfFiles = numberOfFiles;
            m_totalFileSizes = totalFileSizes;
            m_requiredSpace = requiredSpace;
            m_availableSpace = availableSpace;
            m_bufferSpace = bufferSpace;
        }
   
        public boolean isValid(){
            return m_isValid;
        }
        
        public int getNumberOfFiles() {
            return m_numberOfFiles;
        }
        public long getTotalFileSizes() {
            return m_totalFileSizes;
        }
        public long getRequiredSpace() {
            return m_requiredSpace;
        }
        public long getAvailableSpace() {
            return m_availableSpace;
        }
        public long getBufferSpace() {
            return m_bufferSpace;
        }
 
    }



    public static List<File> collectAllFilesFromDataStructure(File idDataFile, NoteBytes filePathKey, SecretKey secretKey, ExecutorService execService) throws Exception {
        List<File> allFiles = new ArrayList<>();
        
        // Decrypt and parse to collect file paths
        PipedOutputStream decryptedOutput = new PipedOutputStream();
        
        CompletableFuture<NoteBytesObject> decryptFuture = performDecryption(idDataFile, decryptedOutput, secretKey, execService);
        
        try (PipedInputStream decryptedInput = new PipedInputStream(decryptedOutput);
            NoteBytesReader reader = new NoteBytesReader(decryptedInput)) {
            
            NoteBytesMetaData rootMetaData = reader.nextMetaData();
            if (rootMetaData == null || rootMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                return allFiles; // Empty or invalid structure
            }
            
            try(NoteBytesEphemeral rootData = new NoteBytesEphemeral(reader.readByteAmount(rootMetaData.getLength()))){
            
                try (NoteBytesMapEphemeral rootMap = new NoteBytesMapEphemeral(rootData)) {
                    collectFilesFromMap(rootMap, allFiles, filePathKey);
                }
            }
            // Wait for decryption to complete
            decryptFuture.join();
        }
        
        return allFiles;
    }


    // Recursively collect files without updating
    public static void collectFilesFromMap(NoteBytesMapEphemeral currentMap, List<File> allFiles, NoteBytes filePathKey) throws IOException {
        Set<NoteBytes> keys = currentMap.keySet();
        
        for (NoteBytes key : keys) {
            try(
                NoteBytesEphemeral value = currentMap.get(key);
            ){
                if (value != null) {
                    try (NoteBytesMapEphemeral valueMap = new NoteBytesMapEphemeral(value)) {
                        try(
                        NoteBytesEphemeral filePath = valueMap.get(filePathKey);
                        ){
                            if (filePath != null) {
                                File file = new File(filePath.getAsString());
                                if (file.exists() && file.isFile()) {
                                    allFiles.add(file);
                                }
                            } else {
                                // Recurse into nested structure
                                collectFilesFromMap(valueMap, allFiles, filePathKey);
                            }
                        }
                    } catch (Exception e) {
                        // Skip invalid entries
                        Utils.writeLogMsg("collectFilesFromMap.skipValue", e);
                    }
                }
            }
        }
    }

    public static int getIndexSmallerThan(long[] largestBatchSizes,long newFileSize){
        
        for(int i = 0; i<largestBatchSizes.length ; i++){
            long size = largestBatchSizes[i];
            if(size < newFileSize){
                return i;
            }
        }
        return -1;
    }

    public static void updateLargestBatchSizes(long[] largestBatchSizes, long newFileSize){
        long currentSize = newFileSize;
        int i = getIndexSmallerThan(largestBatchSizes, currentSize);
        while(i != -1){
            long oldSize = largestBatchSizes[i];

            largestBatchSizes[i] = currentSize;
            currentSize = oldSize;
            i = getIndexSmallerThan(largestBatchSizes, currentSize);
        }
        
    }

    public static long getLargestBatchSize(long[] largestBatchSizes){
        if(largestBatchSizes == null){
            return -1;
        }else{
            long size = 0;
            for(long fileSize : largestBatchSizes){
                size += fileSize;
            }
            return size;
        }
    }

    // Disk space validation
    public static DiskSpaceValidation validateDiskSpaceForUpdate(BulkUpdateConfig config, List<File> allFiles) throws Exception {
        long totalFileSizes = 0;
        long largestFileSize = 0;
        long[] largestBatchSizes = config.getStrategy() == UpdateStrategy.BATCHED ? new long[config.getBatchSize()] : null;

        for (File file : allFiles) {
            long fileLength = file.length();
            largestFileSize = largestFileSize < fileLength ? fileLength : largestFileSize;
            if(config.getStrategy() == UpdateStrategy.BATCHED){
                updateLargestBatchSizes(largestBatchSizes, fileLength);
            }
        }
        

        File referenceDir = allFiles.get(0).getParentFile();
        long availableSpace = referenceDir.getFreeSpace();
        long requiredSpace = allFiles.isEmpty() ? 0 : calculateRequiredSpace(totalFileSizes, largestFileSize,largestBatchSizes, config);
        long bufferSpace = (long) (availableSpace * config.getDiskSpaceBuffer());
        
    
        return new DiskSpaceValidation(allFiles.isEmpty()  ? true : bufferSpace >= requiredSpace , allFiles.size(), totalFileSizes, requiredSpace, availableSpace, bufferSpace);
    }

    public static long calculateRequiredSpace(long totalFileSizes, long largestFileSize, long[] largestBatchSizes, BulkUpdateConfig config) {
        switch (config.getStrategy()) {
            case UpdateStrategy.SEQUENTIAL:
                return largestFileSize;
            case UpdateStrategy.BATCHED:
                return getLargestBatchSize(largestBatchSizes);
            case UpdateStrategy.PARALLEL:
                return totalFileSizes;
            default:
                return totalFileSizes;
        }
    }


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



    public static Future<?> writeEncryptedDataStream(File file, File tmpFile, SecretKey secretKey, PipedOutputStream outputStream, ExecutorService execService, EventHandler<WorkerStateEvent> onComplete, EventHandler<WorkerStateEvent> onFailed) {
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
           
                byte[] iV = RandomService.getIV();
                Cipher cipher = CryptoService.getAESEncryptCipher(iV, secretKey);

                try(
                    PipedInputStream inputStream = new PipedInputStream(outputStream);
                    OutputStream fileOutputStream = Files.newOutputStream(tmpFile.toPath());
                ){
            
                   // Write IV first (unencrypted)
                    fileOutputStream.write(iV);
                    
                    // Create cipher stream for encrypted data
                    try (CipherOutputStream cipherStream = new CipherOutputStream(fileOutputStream, cipher)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int length;
                        
                        while ((length = inputStream.read(buffer)) != -1) {
                            cipherStream.write(buffer, 0, length);
                        }
                    }
                }
                FileUtils.moveFile(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);

                return true;
            }
        };
        task.setOnSucceeded(onComplete);
        task.setOnFailed(onError->{
             Task<Object> deleteTask = new Task<Object>() {
                @Override
                public Object call() throws IOException {
                    
                    return Files.deleteIfExists(tmpFile.toPath());
                }
            };

            deleteTask.setOnSucceeded((deleteComplete)->{
                Utils.returnException(onError,execService,onFailed);
            });
            deleteTask.setOnFailed(deleteFailed->{
                Utils.returnException(execService, onFailed, onError, deleteFailed);
            });

            execService.submit(deleteTask);
        });

        return execService.submit(task);
    }
     
    public static Future<?> writeEncryptedDataStream(File file, SecretKey secretKey, PipedOutputStream outputStream, ExecutorService execService, EventHandler<WorkerStateEvent> onComplete, EventHandler<WorkerStateEvent> onFailed) {
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
           
                byte[] iV = RandomService.getIV();
                Cipher cipher = CryptoService.getAESEncryptCipher(iV, secretKey);

                try(
                    PipedInputStream inputStream = new PipedInputStream(outputStream);
                    OutputStream fileOutputStream = Files.newOutputStream(file.toPath());
                ){
            
                   // Write IV first (unencrypted)
                    fileOutputStream.write(iV);
                    
                    // Create cipher stream for encrypted data
                    try (CipherOutputStream cipherStream = new CipherOutputStream(fileOutputStream, cipher)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int length;
                        
                        while ((length = inputStream.read(buffer)) != -1) {
                            cipherStream.write(buffer, 0, length);
                             if (length < BUFFER_SIZE) {
                                cipherStream.flush();
                            }
                        }
                    }
                }

                return true;
            }
        };
        task.setOnSucceeded(onComplete);
        task.setOnFailed(onError->{
             Task<Object> deleteTask = new Task<Object>() {
                @Override
                public Object call() throws IOException {
                    
                    return Files.deleteIfExists(file.toPath());
                }
            };

            deleteTask.setOnSucceeded((deleteComplete)->{
                Utils.returnException(onError,execService,onFailed);
            });
            deleteTask.setOnFailed(deleteFailed->{
                Utils.returnException(execService, onFailed, onError, deleteFailed);
            });

            execService.submit(deleteTask);
        });
        return execService.submit(task);
    }





    public static Future<?> readEncryptedDataStream(File file, SecretKey secretKey, ExecutorService execService, PipedOutputStream decryptedOutput) {
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
                try (
                    InputStream fileInputStream = Files.newInputStream(file.toPath());
                    NoteBytesReader reader = new NoteBytesReader(fileInputStream);
                    PipedOutputStream output = decryptedOutput
                ) {
                     // AES-GCM IV length
                    
                    if (file.length() > CryptoService.AES_NONCE_SIZE) {
                       
                        byte[] iV = reader.readByteAmount(CryptoService.AES_NONCE_SIZE);
                        
                        Cipher decryptCipher = CryptoService.getAESDecryptCipher(iV, secretKey);
                        
                        // Use CipherInputStream for automatic decryption
                        try (CipherInputStream cipherInputStream = new CipherInputStream(fileInputStream, decryptCipher)) {
                            byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
                            int length;
                            
                            while ((length = cipherInputStream.read(buffer)) != -1) {
                                output.write(buffer, 0, length);
                            }
                            output.flush();
                        }
                    } else {
                        // File is too small to contain valid encrypted data
                        throw new IllegalArgumentException("File is too small to contain encrypted data with IV");
                    }
                    
                    return true;
                }
            }
            
        };
        
        task.setOnFailed(failed -> {
            try {
                decryptedOutput.close();
            } catch (IOException e) {
                Utils.writeLogMsg("Utils.readEncryptedDataStream.close", e);
            }
        });
        
        return execService.submit(task);
    }

    


    protected Future<?> writeEncryptedFile( File file, File tmpFile, SecretKey secretKey, Semaphore semaphore, ExecutorService execService, PipedOutputStream pipedOutputStream, EventHandler<WorkerStateEvent> onWritten, EventHandler<WorkerStateEvent> onError){

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception{
                long fileLength = 0;
                try(
                    PipedInputStream inputStream = new PipedInputStream(pipedOutputStream);
                    OutputStream fileOutputStream = Files.newOutputStream(file.toPath());
                ){
                    
                    byte[] outIV = RandomService.getIV();
                    Cipher encryptCipher = CryptoService.getAESEncryptCipher(outIV, secretKey);

                    fileOutputStream.write(outIV);
                    
                    try (CipherOutputStream cipherStream = new CipherOutputStream(fileOutputStream, encryptCipher)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int length;
                        
                        while ((length = inputStream.read(buffer)) != -1) {
                            cipherStream.write(buffer, 0, length);
                            if (length < BUFFER_SIZE) {
                                cipherStream.flush();
                            }
                            fileLength += length;
                        }
                    }
                }
               
                Files.deleteIfExists(file.toPath());
                FileUtils.moveFile(tmpFile, file);
                pipedOutputStream.close();
                semaphore.release();
                return TaskMessages.createSuccessResult("File length:" + fileLength);
            }
        };


      

        task.setOnFailed(failed->{
            try{
                pipedOutputStream.close();
            }catch(IOException e){
                Utils.writeLogMsg("appData.writeEncryptedFile.pipedOutput.close", e);
            }
            execService.execute(()->{
                if(tmpFile.isFile()){
                    try{
                        Files.deleteIfExists(tmpFile.toPath());
                    }catch(IOException e1){       

                    }
                }
                semaphore.release();
                        
                Utils.returnException(failed, execService, onError);
            });  
        });
        return execService.submit(task);

    }
   
    public static void saveEncryptedData(SecretKey appKey, byte[] data, File dataFile) throws Exception {

        byte[] iV = RandomService.getIV();

        Cipher cipher = CryptoService.getAESEncryptCipher(iV, appKey);
        

        if (dataFile.isFile()) {
            Files.delete(dataFile.toPath());
        }

        try(
            ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
            OutputStream fileStream = Files.newOutputStream(dataFile.toPath());
        ){
            fileStream.write(iV);    
            int bufferSize = data.length < BUFFER_SIZE ? data.length : BUFFER_SIZE;

            byte[] byteArray = new byte[bufferSize];
            byte[] output;

            int length = 0;
            while((length = byteStream.read(byteArray)) != -1){

                output = cipher.update(byteArray, 0, length);
                if(output != null){
                    fileStream.write(output);
                }
            }

            output = cipher.doFinal();
            if(output != null){
                fileStream.write(output);
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
                
                byte[] iV = reader.readByteAmount(CryptoService.AES_NONCE_SIZE);

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
                if(fileSize < CryptoService.AES_NONCE_SIZE){
                    return new byte[0];
                }

                byte[] iV = reader.readByteAmount(CryptoService.AES_NONCE_SIZE);

               
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
           
                if (file.exists() && file.isFile() && file.length() > CryptoService.AES_NONCE_SIZE - 1) {
                    byte[] iV = reader.readByteAmount(CryptoService.AES_NONCE_SIZE);
                    
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
            

    /*
    ////////////////////////////////////////////////////
    */   

    public static CompletableFuture<BulkUpdateResult> processDataFileForBulkUpdateWithProgress(
            File idDataFile, SecretKey oldAppKey, SecretKey newAppKey, BulkUpdateConfig config,
            Consumer<ProgressUpdate> progressCallback,NoteBytes filePathKey, ExecutorService execService) {
        
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    progressCallback.accept(new ProgressUpdate(0, 0, "Starting decryption and analysis..."));
                    
                    PipedOutputStream decryptedOutput = new PipedOutputStream();
                    PipedOutputStream reEncryptedOutput = new PipedOutputStream();
                    
                    CompletableFuture<NoteBytesObject> decryptFuture = 
                        performDecryption(idDataFile, decryptedOutput, oldAppKey, execService);
                    
                    CompletableFuture<List<File>> parseAndUpdateFuture = 
                        parseDataStructureAndUpdateFilesWithStrategy(decryptedOutput, reEncryptedOutput, 
                                                                oldAppKey, newAppKey, config, progressCallback, filePathKey, execService);
                    
                    CompletableFuture<NoteBytesObject> saveFuture = 
                        saveEncryptedFileSwap(idDataFile, newAppKey, reEncryptedOutput);
                    
                    return CompletableFuture.allOf(decryptFuture, parseAndUpdateFuture, saveFuture)
                        .thenCompose(v -> parseAndUpdateFuture.thenApply(updatedFiles -> 
                            createBulkUpdateResult(updatedFiles)))
                        .join();
                        
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process data file for bulk update", e);
                }
            }, execService);
    }

    public static CompletableFuture<List<File>> parseDataStructureAndUpdateFilesWithStrategy(
            PipedOutputStream decryptedOutput, PipedOutputStream reEncryptedOutput,
            SecretKey oldAppKey, SecretKey newAppKey, BulkUpdateConfig config,
            Consumer<ProgressUpdate> progressCallback, NoteBytes filePathKey, ExecutorService execService) {
        
        return CompletableFuture
            .supplyAsync(() -> {
                List<File> allFiles = new ArrayList<>();
                
                try (PipedInputStream decryptedInput = new PipedInputStream(decryptedOutput);
                    NoteBytesReader reader = new NoteBytesReader(decryptedInput);
                    NoteBytesWriter writer = new NoteBytesWriter(reEncryptedOutput)) {
                    
                    progressCallback.accept(new ProgressUpdate(0, 0, "Parsing data structure..."));
                    
                    NoteBytesMetaData rootMetaData = reader.nextMetaData();
                    if (rootMetaData == null) {
                        return Collections.emptyList();
                    }
                    
                    if (rootMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                        throw new IOException("Root must be an object");
                    }
                    
                    try(NoteBytesEphemeral rootData = new NoteBytesEphemeral(reader.readByteAmount(rootMetaData.getLength()));){
                    
                        try (NoteBytesMapEphemeral rootMap = new NoteBytesMapEphemeral(rootData)) {
                            
                            // First pass: collect all files for disk space validation
                            collectFilesFromMap(rootMap, allFiles, filePathKey);
                            
                            progressCallback.accept(new ProgressUpdate(0, allFiles.size(), 
                                String.format("Found %d files. Validating disk space with %s strategy...", 
                                            allFiles.size(), config.getStrategy())));
                            
                            // Validate disk space requirements
                            DiskSpaceValidation validation = validateDiskSpaceForUpdate(config, allFiles);
                            if (!validation.isValid()) {
                                throw new RuntimeException("Insufficient disk space");
                            }
                            
                            progressCallback.accept(new ProgressUpdate(0, allFiles.size(), 
                                "Disk space validated. Starting file updates using " + config.getStrategy() + " strategy..."));
                            
                            // Second pass: update files according to strategy
                            List<File> updatedFiles = updateFilesWithStrategyAndProgress(rootMap, allFiles, 
                                                                                    oldAppKey, newAppKey, config, progressCallback, execService);
                            
                            // Write the root metadata and updated structure
                            writer.write(rootMetaData);
                            try (NoteBytesEphemeral serializedRoot = rootMap.getNoteBytesEphemeral()) {
                                writer.write(serializedRoot.get());
                            }
                            
                            return updatedFiles;
                        }
                    }
                    
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse and update data structure", e);
                }
            }, execService);
    }

    public static List<File> updateFilesWithStrategyAndProgress(NoteBytesMapEphemeral rootMap, List<File> allFiles,
                                                        SecretKey oldAppKey, SecretKey newAppKey, 
                                                        BulkUpdateConfig config, Consumer<ProgressUpdate> progressCallback, ExecutorService execService) {
        
        switch (config.getStrategy()) {
            case SEQUENTIAL:
                return updateFilesSequentialWithProgress(allFiles, oldAppKey, newAppKey, progressCallback);
                
            case BATCHED:
                return updateFilesBatchedWithProgress(allFiles, oldAppKey, newAppKey, config.getBatchSize(), progressCallback, execService);
                
            case PARALLEL:
                return updateFilesParallelWithProgress(allFiles, oldAppKey, newAppKey, progressCallback, execService);
                
            default:
                return updateFilesSequentialWithProgress(allFiles, oldAppKey, newAppKey, progressCallback);
        }
    }

    public static List<File> updateFilesSequentialWithProgress(List<File> allFiles, SecretKey oldAppKey, SecretKey newAppKey,
                                                    Consumer<ProgressUpdate> progressCallback) {
        List<File> updatedFiles = new ArrayList<>();
        
        for (int i = 0; i < allFiles.size(); i++) {
            File file = allFiles.get(i);
            
            try {
                progressCallback.accept(new ProgressUpdate(i, allFiles.size(), 
                    "Updating file: " + file.getName()));
                
                File tmpFile = new File(file.getAbsolutePath() + ".rekey.tmp");
                boolean success = updateFileEncryption(oldAppKey, newAppKey, file, tmpFile);
                
                if (success) {
                    updatedFiles.add(file);
                    progressCallback.accept(new ProgressUpdate(i + 1, allFiles.size(), 
                        "Successfully updated: " + file.getName()));
                } else {
                    Utils.writeLogMsg("updateFilesSequentialWithProgress.failed", "File: " + file.getAbsolutePath());
                    progressCallback.accept(new ProgressUpdate(i + 1, allFiles.size(), 
                        "Failed to update: " + file.getName()));
                }
            } catch (Exception e) {
                Utils.writeLogMsg("updateFilesSequentialWithProgress.exception", 
                    "File: " + file.getAbsolutePath() + ", Error: " + e.getMessage());
                progressCallback.accept(new ProgressUpdate(i + 1, allFiles.size(), 
                    "Error updating: " + file.getName()));
            }
        }
        
        return updatedFiles;
    }

    public static List<File> updateFilesBatchedWithProgress(List<File> allFiles, SecretKey oldAppKey, SecretKey newAppKey,
                                                    int batchSize, Consumer<ProgressUpdate> progressCallback, ExecutorService execService) {
        List<File> updatedFiles = new ArrayList<>();
        AtomicInteger totalCompleted = new AtomicInteger(0);
        
        // Process files in batches
        for (int batchStart = 0; batchStart < allFiles.size(); batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, allFiles.size());
            List<File> batch = allFiles.subList(batchStart, batchEnd);
            
            progressCallback.accept(new ProgressUpdate(totalCompleted.get(), allFiles.size(), 
                String.format("Processing batch %d-%d of %d files...", 
                            batchStart + 1, batchEnd, allFiles.size())));
            
            // Process current batch in parallel
            List<CompletableFuture<Boolean>> batchFutures = batch.stream()
                .map(file -> updateSingleFileEncryption(file, oldAppKey, newAppKey, execService)
                    .thenApply(result -> {
                        int completed = totalCompleted.incrementAndGet();
                        progressCallback.accept(new ProgressUpdate(completed, allFiles.size(), 
                            (result ? "Updated: " : "Failed: ") + file.getName()));
                        return result;
                    }))
                .collect(Collectors.toList());
            
            // Wait for current batch to complete before starting next batch
            CompletableFuture<Void> batchCompletion = CompletableFuture.allOf(
                batchFutures.toArray(new CompletableFuture[0]));
            
            try {
                batchCompletion.join();
                
                // Collect successful updates from this batch
                for (int j = 0; j < batch.size(); j++) {
                    if (batchFutures.get(j).join()) {
                        updatedFiles.add(batch.get(j));
                    }
                }
                
                progressCallback.accept(new ProgressUpdate(totalCompleted.get(), allFiles.size(), 
                    String.format("Completed batch %d-%d. %d files updated so far.", 
                                batchStart + 1, batchEnd, updatedFiles.size())));
                                
            } catch (Exception e) {
                Utils.writeLogMsg("updateFilesBatchedWithProgress.batchFailed", 
                    "Batch starting at index " + batchStart + ", Error: " + e.getMessage());
                progressCallback.accept(new ProgressUpdate(totalCompleted.get(), allFiles.size(), 
                    "Batch failed: " + e.getMessage()));
            }
        }
        
        return updatedFiles;
    }


    public static CompletableFuture<Boolean> updateSingleFileEncryption(File file, SecretKey oldAppKey, SecretKey newAppKey, ExecutorService execService) {
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    // Create temporary file for the re-encrypted version
                    File tmpFile = new File(file.getAbsolutePath() + ".rekey.tmp");
                    
                    // Call the static method to update file encryption
                    boolean success = updateFileEncryption(oldAppKey, newAppKey, file, tmpFile);
                    
                    if (!success) {
                        Utils.writeLogMsg("updateSingleFileEncryption.failed", "File: " + file.getAbsolutePath());
                    }
                    
                    return success;
                    
                } catch (Exception e) {
                    Utils.writeLogMsg("updateSingleFileEncryption.exception", 
                        "File: " + file.getAbsolutePath() + ", Error: " + e.getMessage());
                    return false;
                }
            }, execService);
    }

    public static List<File> updateFilesParallelWithProgress(List<File> allFiles, SecretKey oldAppKey, SecretKey newAppKey,
                                                    Consumer<ProgressUpdate> progressCallback, ExecutorService execService) {
        AtomicInteger completedCount = new AtomicInteger(0);
        
        progressCallback.accept(new ProgressUpdate(0, allFiles.size(), 
            "Starting parallel update of all " + allFiles.size() + " files..."));
        
        List<CompletableFuture<Boolean>> updateFutures = allFiles.stream()
            .map(file -> updateSingleFileEncryption(file, oldAppKey, newAppKey, execService)
                .thenApply(result -> {
                    int completed = completedCount.incrementAndGet();
                    progressCallback.accept(new ProgressUpdate(completed, allFiles.size(), 
                        (result ? "Updated: " : "Failed: ") + file.getName()));
                    return result;
                }))
            .collect(Collectors.toList());
        
        CompletableFuture<Void> allUpdatesFuture = CompletableFuture.allOf(
            updateFutures.toArray(new CompletableFuture[0]));
        
        try {
            allUpdatesFuture.join();
            
            // Collect successful updates
            List<File> updatedFiles = new ArrayList<>();
            for (int i = 0; i < allFiles.size(); i++) {
                if (updateFutures.get(i).join()) {
                    updatedFiles.add(allFiles.get(i));
                }
            }
            
            progressCallback.accept(new ProgressUpdate(allFiles.size(), allFiles.size(), 
                String.format("Parallel update completed. %d of %d files updated successfully.", 
                            updatedFiles.size(), allFiles.size())));
            
            return updatedFiles;
            
        } catch (Exception e) {
            Utils.writeLogMsg("updateFilesParallelWithProgress.failed", e.getMessage());
            progressCallback.accept(new ProgressUpdate(completedCount.get(), allFiles.size(), 
                "Parallel update failed: " + e.getMessage()));
            return Collections.emptyList();
        }
    }
    

    public static BulkUpdateResult createBulkUpdateResult(List<File> updatedFiles) {
        // The updated approach returns only successfully updated files
        // So we need to calculate failures differently
        
        List<String> failures = new ArrayList<>();
        int successCount = updatedFiles.size();
        
        // Validate that the updated files still exist and are accessible
        Iterator<File> iterator = updatedFiles.iterator();
        while (iterator.hasNext()) {
            File file = iterator.next();
            if (!file.exists() || !file.canRead()) {
                failures.add(file.getAbsolutePath());
                iterator.remove(); // Remove from success list
            }
        }
        
        // Recalculate success count after validation
        successCount = updatedFiles.size();
        int failCount = failures.size();
        
        return new BulkUpdateResult(successCount, failCount, failures);
    }
    // Progress tracking class
    public static class ProgressUpdate {
        private final int completed;
        private final int total;
        private final String message;
        
        public ProgressUpdate(int completed, int total, String message) {
            this.completed = completed;
            this.total = total;
            this.message = message;
        }
        
        public int getCompleted() { return completed; }
        public int getTotal() { return total; }
        public String getMessage() { return message; }
        
        public double getPercentage() {
            return total > 0 ? (double) completed / total * 100.0 : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("Progress: %d/%d (%.1f%%) - %s", completed, total, getPercentage(), message);
        }
    }



}