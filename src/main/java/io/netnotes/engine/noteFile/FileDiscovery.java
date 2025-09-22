package io.netnotes.engine.noteFile;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.noteFile.FileStreamUtils.BulkUpdateConfig;
import io.netnotes.engine.noteFile.FileStreamUtils.BulkUpdateResult;
import io.netnotes.engine.utils.Utils;

public class FileDiscovery {


    public static List<File> collectAllFilesFromDataStructure(File idDataFile, SecretKey secretKey, ExecutorService execService) throws Exception {
        List<File> allFiles = new ArrayList<>();
        
        // Decrypt and parse to collect file paths
        PipedOutputStream decryptedOutput = new PipedOutputStream();
        
        CompletableFuture<NoteBytesObject> decryptFuture = FileStreamUtils.performDecryption(idDataFile, decryptedOutput, secretKey, execService);
        
        try (PipedInputStream decryptedInput = new PipedInputStream(decryptedOutput);
            NoteBytesReader reader = new NoteBytesReader(decryptedInput)) {
            
            NoteBytesMetaData rootMetaData = reader.nextMetaData();
            if (rootMetaData == null || rootMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                return allFiles; // Empty or invalid structure
            }
            
            NoteBytesMap rootMap = new NoteBytesMap(reader, rootMetaData.getLength());
            collectFilesFromMap(rootMap, allFiles);
                
            
            // Wait for decryption to complete
            decryptFuture.join();
        }
        
        return allFiles;
    }


    // Recursively collect files without updating
    public static void collectFilesFromMap(NoteBytesMap currentMap, List<File> allFiles) throws IOException {
        Set<NoteBytes> keys = currentMap.keySet();
        
        for (NoteBytes key : keys) {
        
            NoteBytes value = currentMap.get(key);
        
            if (value != null && value.getByteDecoding().getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                try  {

                    NoteBytesMap valueMap = new NoteBytesMap(value.get());
                   
                    NoteBytes filePath = valueMap.get(DataFactory.FILE_PATH);
                
                    if (filePath != null) {
                        File file = new File(filePath.getAsString());
                        if (file.exists() && file.isFile()) {
                            allFiles.add(file);
                        }
                    } else {
                        // Recurse into nested structure
                        collectFilesFromMap(valueMap, allFiles);
                    }
                    
                } catch (Exception e) {
                    // Skip invalid entries
                    Utils.writeLogMsg("collectFilesFromMap.skipValue", e);
                }
            }
            
        }
    }

  
 

    public static CompletableFuture<List<File>> parseDataStructureAndUpdateFilesWithStrategy(
            PipedOutputStream decryptedOutput, PipedOutputStream reEncryptedOutput,
            SecretKey oldAppKey, SecretKey newAppKey, BulkUpdateConfig config,
            Consumer<ProgressUpdate> progressCallback, ExecutorService execService) {
        
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
                    
                  
                   
                    NoteBytesMap rootMap = new NoteBytesMap(reader, rootMetaData.getLength());
                    // First pass: collect all files for disk space validation
                    collectFilesFromMap(rootMap, allFiles);
                    
                    progressCallback.accept(new ProgressUpdate(0, allFiles.size(), 
                        String.format("Found %d files. Validating disk space with %s strategy...", 
                                    allFiles.size(), config.getStrategy())));
                    
                    // Validate disk space requirements
                    DiskSpaceValidation validation = DiskSpaceValidation.validateDiskSpace(config, allFiles);
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

                
                    writer.write(rootMap.getNoteBytesObject());
                    
                    
                    return updatedFiles;
                
            
                    
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse and update data structure", e);
                }
            }, execService);
    }

    public static List<File> updateFilesWithStrategyAndProgress(NoteBytesMap rootMap, List<File> allFiles,
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
                boolean success = FileStreamUtils.updateFileEncryption(oldAppKey, newAppKey, file, tmpFile);
                
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
                    boolean success = FileStreamUtils.updateFileEncryption(oldAppKey, newAppKey, file, tmpFile);
                    
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

    
}
