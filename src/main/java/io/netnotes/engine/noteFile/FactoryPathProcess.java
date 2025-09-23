package io.netnotes.engine.noteFile;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import javax.crypto.SecretKey;


import io.netnotes.engine.crypto.AESBackedInputStream;
import io.netnotes.engine.crypto.AESBackedOutputStream;
import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.IntCounter;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;

public class FactoryPathProcess {
    
    public static CompletableFuture<File> processExistingDataFile(File idDataFile, NoteStringArrayReadOnly path, SecretKey secretKey, ExecutorService execService) {
        return CompletableFuture
            .supplyAsync(() -> {
               
                
                File dataDir = idDataFile.getParentFile();
                if (
                    path == null || 
                    path.byteLength() == 0 || 
                    dataDir == null
                ) {
                    throw new IllegalArgumentException("Required parameters cannot be null");
                }

                if(!dataDir.isDirectory()){
                    try{
                        Files.createDirectories(dataDir.toPath());
                    }catch(IOException e){
                        throw new RuntimeException("Cannot access data directory", e);
                    }
                }

                NoteBytes[] targetPath = path.getAsArray();

                if(
                    idDataFile.exists() && 
                    idDataFile.isFile() && 
                    idDataFile.length() > CryptoService.AES_IV_SIZE
                ){
                  
                        PipedOutputStream decryptedOutput = new PipedOutputStream();
                        PipedOutputStream parsedOutput = new PipedOutputStream();
                  
                        try {
                        
                            // Set up piped streams for concurrent processing
                        
                            
                            // Chain the operations: decrypt -> parse -> encrypt -> return file
                            CompletableFuture<NoteBytesObject> decryptFuture = 
                                FileStreamUtils.performDecryption(idDataFile, decryptedOutput, secretKey, execService);
                            
                            CompletableFuture<File> parseFuture = 
                                parseIdDataFileUpdate(targetPath, dataDir, secretKey, decryptedOutput, parsedOutput, execService);
                            
                            CompletableFuture<NoteBytesObject> saveFuture = 
                                FileStreamUtils.saveEncryptedFileSwap(idDataFile, secretKey, parsedOutput);
                            
                            // Wait for all operations to complete and return the result file
                            return CompletableFuture.allOf(decryptFuture, parseFuture, saveFuture)
                                .thenCompose(v -> parseFuture) // Return the parsed file result
                                .join(); // Block for this async operation
                            
                                
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to process existing data file", e);
                        }finally{
                            try {
                                if (decryptedOutput != null) decryptedOutput.close();
                                if (parsedOutput != null) parsedOutput.close();
                            } catch (IOException closeEx) {
                                System.err.println("Failed to close streams: " + closeEx.getMessage());
                            }
                        }
                   
                }else{
                    return createInitialFile(idDataFile, dataDir, targetPath, secretKey);
                }
            }, execService);
    }

    public static File createInitialFile(File idDataFile,File dataDir, NoteBytes[] targetPath, SecretKey secretKey){
        try{
            File newFile = DataFactory.createNewDataFile(dataDir);
            // Build the complete path structure
        
            NoteBytesPair rootPair = buildPathStructure( targetPath, 0, newFile);
            FileStreamUtils.encryptPairToFile(newFile, secretKey, rootPair);

            return newFile;
        }catch(Exception e){
            throw new RuntimeException("Failed to create initial data file", e);
        }
       
       
    }

    public static NoteBytesPair buildPathStructure(NoteBytes[] targetPath, int pathIndex, File resultFile) throws Exception {
         
        if (pathIndex == targetPath.length) {
            return new NoteBytesPair(DataFactory.FILE_PATH, new NoteBytes(resultFile.getAbsolutePath()));
        }else if (pathIndex >= targetPath.length) {
            return null;
        }

        return new NoteBytesPair(targetPath[pathIndex], new NoteBytesObject(new NoteBytesPair[]{
            buildPathStructure(targetPath, pathIndex + 1, resultFile)
        }));
        
    }

    public static CompletableFuture<File> parseIdDataFileUpdate(NoteBytes[] targetPath, File dataDir, SecretKey secretKey,
            PipedOutputStream decryptedInputStream,
            PipedOutputStream parsedOutputStream, ExecutorService execService) {
        return CompletableFuture
            .supplyAsync(() -> {

                // Validate inputs
                

                try (
                    NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(decryptedInputStream, StreamUtils.PIPE_BUFFER_SIZE));
                    NoteBytesWriter writer = new NoteBytesWriter(parsedOutputStream)
                ) {
                    
                    NoteBytes targetPathKey = targetPath[0];
                    
                    boolean found = false;
                    IntCounter counter = new IntCounter();
                    NoteBytes key = reader.nextNoteBytes();
                    if(key != null){
                        counter.add(writer.write(key));
                    }
                    while ( key != null && !(found = key.equals(targetPathKey))) {
                        //not found
                        NoteBytesMetaData valueMetaData = reader.nextMetaData();
                        if(valueMetaData != null){
                            counter.add(writer.write(valueMetaData));
                            counter.add(StreamUtils.readWriteNextBytes(valueMetaData.getLength(), reader, writer));
                        }else{
                            int location = counter.get();
                            throw new EOFException("Unexpected end of file found at: " + location );
                        }
                    }
                    
                    if (found) {
                        // Found root key
                        return processFoundRootKey(targetPath, dataDir, secretKey, reader, writer);
                    } else {
                        // Root key doesn't exist - create entire path structure
                        return createCompletePathStructure(targetPath, dataDir, reader, writer);
                    } 
                    
                } catch (Exception e) {
                    throw new RuntimeException("Failed to complete parsing idDataFile", e);
                }
            }, execService);
    }

    private static File processFoundRootKey(NoteBytes[] targetPath, File dataDir, SecretKey secretKey, 
            NoteBytesReader reader, NoteBytesWriter writer) throws Exception {
        
        // Read metadata for the root key's value
        NoteBytesMetaData rootMetaData = reader.nextMetaData();
        
        //don't write the rootMetaData here
       
        int rootMetadataLength = rootMetaData.getLength();
                
        // Create temp file and divert root key content


        AESBackedOutputStream abos = new AESBackedOutputStream(dataDir, secretKey, rootMetadataLength,StreamUtils.PIPE_BUFFER_SIZE, false);

        IntCounter depthCounter = new IntCounter(targetPath.length == 1 ? 0 : 1);
        NoteBytes filePathResult = divertAndParseToTempFile(abos, secretKey, targetPath, depthCounter, rootMetaData.getLength(), reader);

        if (filePathResult != null) {
            writer.write(rootMetaData);
            // File exists - stream temp file back unchanged
           
            
            try(
                NoteBytesReader tmpReader = new NoteBytesReader(
                    new AESBackedInputStream(abos, secretKey)
                )
            ){
                StreamUtils.readWriteBytes(tmpReader, writer);
            }
            
        
            // Continue streaming remaining top-level content
            StreamUtils.readWriteBytes(reader, writer);
            return new File(filePathResult.getAsString());
        } else {
            File newFile = DataFactory.createNewDataFile(dataDir);
            NoteBytesMetaData newRootMetadata = new NoteBytesMetaData(rootMetaData.getType(), rootMetaData.getLength() + calculateInsertionSize(0, targetPath, newFile));
            writer.write(newRootMetadata);

            if(targetPath.length == 1){
                insertStructureFromDepth(writer, targetPath, 1, newFile);
               
                try(
                    NoteBytesReader tmpReader = new NoteBytesReader(new AESBackedInputStream(abos, secretKey));
                    ){
                    StreamUtils.readWriteBytes(tmpReader, writer);
                }
            
             
                StreamUtils.readWriteBytes(reader, writer);
                return newFile;
            }else{
                streamBackWithInsertedStructure(abos, targetPath, depthCounter.get(), newFile, writer, secretKey, rootMetadataLength);
                // Continue streaming remaining top-level content  
                StreamUtils.readWriteBytes(reader, writer);
                return newFile;
            }

        
        }
    }



    private static int calculateInsertionSize(int currentDepth, NoteBytes[] targetPath, File newFile) throws Exception {
        // Base case: at the final key level, calculate the file pairs size
        if (currentDepth == targetPath.length - 1) {
            // FILE_PATH key: 1 byte + 5 bytes metadata = 6 bytes
            int filePathKeySize = DataFactory.FILE_PATH.byteLength() + NoteBytesMetaData.STANDARD_META_DATA_SIZE;
            
            // FILE_PATH value: path string + 5 bytes metadata
            String filePath = newFile.getAbsolutePath();
            int filePathValueSize = filePath.getBytes().length + NoteBytesMetaData.STANDARD_META_DATA_SIZE;

            return filePathKeySize + filePathValueSize;
        }
        
        // Beyond path - no calculation needed
        if (currentDepth >= targetPath.length) {
            return 0;
        }
        
        // Intermediate level: calculate size needed from this depth to the end
        NoteBytes currentKey = targetPath[currentDepth];
        
        // Key size: path key bytes + metadata
        int keySize = currentKey.byteLength() + NoteBytesMetaData.STANDARD_META_DATA_SIZE;
        
        // Value size: nested object metadata + recursive content from next level
        int nestedContentSize = calculateInsertionSize(currentDepth + 1, targetPath, newFile);
        int valueSize = NoteBytesMetaData.STANDARD_META_DATA_SIZE + nestedContentSize;
        
        return keySize + valueSize;
    }

    private static File createCompletePathStructure(NoteBytes[] targetPath, File dataDir,
            NoteBytesReader reader, NoteBytesWriter writer) throws Exception {
        
        // Create new file for this path
        File newFile = DataFactory.createNewDataFile(dataDir);
        
        writer.write(buildPathStructure( targetPath, 0, newFile));
        
        // Continue streaming any remaining content from original file
        StreamUtils.readWriteBytes(reader, writer);
        
        return newFile;
    }

 
    private static NoteBytes divertAndParseToTempFile(AESBackedOutputStream abaos, SecretKey secretKey, 
        NoteBytes[] targetPath, IntCounter depthCounter, int contentLength, NoteBytesReader reader
    ) throws Exception {
      
    
        try(NoteBytesWriter writer = new NoteBytesWriter(abaos)) {
            // Stream the content to temp file while parsing
            IntCounter bytesCounter = new IntCounter();
            IntCounter regressionLength = new IntCounter(contentLength);
            return parseContentWhileWritingToTemp(targetPath, depthCounter, bytesCounter, regressionLength, 
                contentLength, reader, writer);
        }
        
        
    }

    private static NoteBytes parseContentWhileWritingToTemp(NoteBytes[] targetPath, IntCounter depthCounter, 
        IntCounter byteCounter, IntCounter regressionLength, int contentLength, NoteBytesReader reader, 
        NoteBytesWriter tmpWriter) throws Exception 
    {
        int currentDepth = depthCounter.get();
        int maxLevel = targetPath.length;

        if(currentDepth == 0){
    
            return searchForFilePathKey(byteCounter,contentLength, contentLength, reader, tmpWriter);
            
        }else if(currentDepth < maxLevel && byteCounter.get() < contentLength && 
            byteCounter.get() < regressionLength.get()
        ){
            NoteBytes targetKey = targetPath[currentDepth];

            NoteBytes key = reader.nextNoteBytes();
            int keySize = tmpWriter.write(key);
            byteCounter.add(keySize);

            if(key != null){
                if(targetKey.equals(key)){
                    depthCounter.add(1);
                    NoteBytesMetaData metaData = reader.nextMetaData();
                    
                    if(metaData != null ){
                        byteCounter.add( tmpWriter.write(metaData));
                        if(currentDepth == maxLevel -1){
                            return searchForFilePathKey(byteCounter, metaData.getLength(), contentLength, reader, tmpWriter);
                        }else{
                            regressionLength.set(byteCounter.get() + metaData.getLength());
                            return parseContentWhileWritingToTemp(targetPath, depthCounter, byteCounter,regressionLength, contentLength, reader, tmpWriter);
                        }
                    }else{
                        throw new EOFException("Unexpected end of file found at: " + targetPath[0] + " + " + byteCounter.get() + " bytes" );
                    }
                    
                }else{
                    NoteBytes value = reader.nextNoteBytes();
                    byteCounter.add(tmpWriter.write(value));
                    return parseContentWhileWritingToTemp(targetPath, depthCounter, byteCounter,regressionLength, contentLength, reader, tmpWriter);
                }
            }else{
                return null;
            }
          
        }else{
            return null;
        }
    }
        


    private static NoteBytes searchForFilePathKey(IntCounter byteCounter, int objectSize, int contentLength, NoteBytesReader reader, NoteBytesWriter tmpWriter) throws Exception {
                
        int count = 0;
        while(count < objectSize && byteCounter.get() < contentLength){
            NoteBytes key = reader.nextNoteBytes();
    
            if(key != null){
                int keySize = tmpWriter.write(key);
                count += keySize;
                byteCounter.add(keySize);
                if(key.equals(DataFactory.FILE_PATH)){
                    NoteBytes filePath = reader.nextNoteBytes();
                    byteCounter.add(tmpWriter.write(filePath));
                    return filePath;
                }else{
                    NoteBytesMetaData valueMetaData = reader.nextMetaData();
                    int metaDataSize = tmpWriter.write(valueMetaData);
                    byteCounter.add(metaDataSize);
                    
                    StreamUtils.readWriteNextBytes(valueMetaData.getLength(), reader, tmpWriter);
                    byteCounter.add(valueMetaData.getLength());
                    count+= (metaDataSize + valueMetaData.getLength());
                }
            }else{
                byteCounter.set(contentLength);
                return null;
            }
        }
        return null;
        
    }
   

    private static void streamBackWithInsertedStructure(AESBackedOutputStream abos, NoteBytes[] targetPath,
        int insertionDepth, File newFile, NoteBytesWriter writer, SecretKey secretKey, int rootMetadataLength) throws Exception {
    
        try (NoteBytesReader tmpReader = new NoteBytesReader(new AESBackedInputStream(abos, secretKey))){
            streamBackWithSizeAdjustments(newFile, tmpReader, writer, targetPath, insertionDepth, 
                        1, rootMetadataLength);
        }
    }

    private static void streamBackWithSizeAdjustments(File newFile, NoteBytesReader tmpReader, NoteBytesWriter writer,
        NoteBytes[] targetPath, int insertionDepth, int currentDepth, int bucketLength) throws Exception 
    {
        
        IntCounter bytesProcessed = new IntCounter();
        IntCounter insertedAtThisLevel = new IntCounter();
        
        while (bytesProcessed.get() < bucketLength) {
            // Read key (metadata handled internally)
            NoteBytes key = tmpReader.nextNoteBytes();
            if (key == null) break;
            
            bytesProcessed.add(writer.write(key));
            
            // Read value metadata explicitly
            NoteBytesMetaData valueMetaData = tmpReader.nextMetaData();
            bytesProcessed.add(NoteBytesMetaData.STANDARD_META_DATA_SIZE);
            
            // Determine if this key is on our target path
            boolean isOnTargetPath = (currentDepth < targetPath.length) && 
                                    key.equals(targetPath[currentDepth]);
            
            if (isOnTargetPath && currentDepth < insertionDepth && 
                valueMetaData.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                // This bucket needs to grow - adjust its size
                int insertionSize = calculateInsertionSize(currentDepth + 1, targetPath, newFile);
                int newSize = valueMetaData.getLength() + insertionSize;
                NoteBytesMetaData adjustedMetaData = new NoteBytesMetaData(valueMetaData.getType(), newSize);
                writer.write(adjustedMetaData);
                
                // Recurse deeper with value's length as new boundary
                streamBackWithSizeAdjustments(newFile, tmpReader, writer, targetPath, insertionDepth,
                        currentDepth + 1, valueMetaData.getLength());
                bytesProcessed.add(newSize); // Add the adjusted size
                        
            } else if (isOnTargetPath && currentDepth == insertionDepth) {
                // This is where we insert - write existing content first
                writer.write(valueMetaData);
                StreamUtils.readWriteNextBytes(valueMetaData.getLength(), tmpReader, writer);
                bytesProcessed.add(valueMetaData.getLength());
                
                // Insert new structure from this depth
                insertStructureFromDepth(writer, targetPath, insertionDepth, newFile);
                insertedAtThisLevel.set(1);
                StreamUtils.readWriteBytes(tmpReader, writer);
                return;
            } else {
                // Not on target path - stream unchanged
                writer.write(valueMetaData);
                StreamUtils.readWriteNextBytes(valueMetaData.getLength(), tmpReader, writer);
                bytesProcessed.add(valueMetaData.getLength());
            }
        }
        
        // If we reached end of bucket without finding insertion point, insert here
        if (currentDepth == insertionDepth && insertedAtThisLevel.get() == 0) {
            insertStructureFromDepth(writer, targetPath, insertionDepth, newFile);
        }
    }



    private static int insertStructureFromDepth(NoteBytesWriter writer, NoteBytes[] targetPath, int insertionDepth, File newFile) throws Exception {
        
        NoteBytesPair pair = buildPathStructure( targetPath, insertionDepth, newFile);
        if(pair != null){
            return writer.write(pair);
        }else{
            return 0;
        }
    }

   
}
