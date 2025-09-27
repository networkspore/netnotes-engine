package io.netnotes.engine.noteFiles;

import java.io.EOFException;
import java.io.File;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import javax.crypto.SecretKey;

import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteString;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.IntCounter;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;

public class FactoryPathProcess {
    
    public static NoteBytes createInitialFile(File idDataFile, File dataDir, NoteString[] targetPath, SecretKey secretKey){
        try{
            NoteBytes noteFilePath = getNewFilePath(dataDir);
            // Build the complete path structure
            FileStreamUtils.encryptPairToFile(idDataFile, secretKey, 
                buildPathStructure( targetPath, 0, noteFilePath)
            );
            return noteFilePath;
        }catch(Exception e){
            throw new RuntimeException("Failed to create initial data file", e);
        }
    }

    public static NoteBytes getNewFilePath(File dataDir){
        File newFile = NotePathFactory.createNewDataFile(dataDir);
        return new NoteBytesReadOnly(newFile.getAbsolutePath());
    }

    public static NoteBytesPair buildPathStructure(NoteString[] targetPath, int pathIndex, 
        NoteBytes resultFileAbsolutePath
    ) {
        if (pathIndex == targetPath.length) {
            return new NoteBytesPair(NotePathFactory.FILE_PATH, resultFileAbsolutePath);
        }else if (pathIndex > targetPath.length) {
            return null;
        }
        return new NoteBytesPair(targetPath[pathIndex], new NoteBytesObject(new NoteBytesPair[]{
            buildPathStructure(targetPath, pathIndex + 1, resultFileAbsolutePath)
        }));
        
    }

    private static NoteBytes createNewRootPath(NoteString[] targetPath, File dataDir, 
        NoteBytesWriter writer) throws Exception 
    {
        NoteBytes resultPath = getNewFilePath(dataDir);
        writer.write(buildPathStructure( targetPath, 0, resultPath));
        return resultPath;
    }

    
    public static CompletableFuture<NoteBytes> getOrCreateIdDataFile(File pathLedger, File dataDir, NoteString[] targetPath, 
        NoteStringArrayReadOnly path, SecretKey secretKey, ExecutorService execService
    ) {
        return CompletableFuture
            .supplyAsync(() -> {
            
                if(
                    pathLedger.exists() && 
                    pathLedger.isFile() && 
                    pathLedger.length() > CryptoService.AES_IV_SIZE
                ){
                    
                    PipedOutputStream decryptedOutput = new PipedOutputStream();
                    PipedOutputStream parsedOutput = new PipedOutputStream();
                
                    try {
                    
                        // Chain the operations: decrypt -> parse -> encrypt -> return file
                        CompletableFuture<NoteBytesObject> decryptFuture = 
                            FileStreamUtils.performDecryption(pathLedger, decryptedOutput, secretKey, execService);
                        
                        CompletableFuture<NoteBytes> parseFuture = 
                            parseStreamToOutputGetOrAddPath(targetPath, dataDir, secretKey, decryptedOutput, parsedOutput, execService);
                        
                        CompletableFuture<NoteBytesObject> saveFuture = 
                            FileStreamUtils.saveEncryptedFileSwap(pathLedger, secretKey, parsedOutput);
                        
                        // Wait for all operations to complete and return the result file
                        return CompletableFuture.allOf(decryptFuture, parseFuture, saveFuture)
                            .thenCompose(v -> parseFuture) // Return the parsed file result
                            .join(); // Block for this async operation
                        
                            
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process existing data file", e);
                    }finally{
                        StreamUtils.safeClose(decryptedOutput);
                        StreamUtils.safeClose(parsedOutput);
                    }
                
                }else{
                    return createInitialFile(pathLedger, dataDir, targetPath, secretKey);
                }
            }, execService);
    }

 

    public static CompletableFuture<NoteBytes> parseStreamToOutputGetOrAddPath(NoteString[] targetPath, File dataDir, SecretKey secretKey,
            PipedOutputStream decryptedInputStream,
            PipedOutputStream parsedOutputStream, ExecutorService execService) {
        return CompletableFuture
            .supplyAsync(() -> {


                try (
                    NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(decryptedInputStream, StreamUtils.PIPE_BUFFER_SIZE));
                    NoteBytesWriter writer = new NoteBytesWriter(parsedOutputStream)
                ) {
                    
                    NoteString targetPathKey = targetPath[0];
                
                    boolean foundRootKey = false;
                    IntCounter byteCounter = new IntCounter();
                    NoteBytes key = reader.nextNoteBytes();
                    if(key != null){
                        byteCounter.add(writer.write(key));
                    }
                    while ( key != null && !(foundRootKey = key.equals(targetPathKey))) {
                        //not found
                        NoteBytesMetaData valueMetaData = reader.nextMetaData();
                        if(valueMetaData != null){
                            byteCounter.add(writer.write(valueMetaData));
                            byteCounter.add(StreamUtils.readWriteNextBytes(valueMetaData.getLength(), reader, writer));
                        }else{
                            throw new IllegalStateException("Corrupted data format found at: " + byteCounter.get() );
                        }
                    }
                    
                    if (foundRootKey) {
                        return processFoundRootKey(targetPath, dataDir, secretKey, reader, writer);
                    } else {
                        return createNewRootPath(targetPath, dataDir, writer);
                    } 
                    
                } catch (Exception e) {
                    throw new RuntimeException(NoteMessaging.Error.INVALID, e);
                }
            }, execService);
    }

    private static NoteBytes processFoundRootKey(NoteString[] targetPath, File dataDir, SecretKey secretKey, 
            NoteBytesReader reader, NoteBytesWriter writer) throws Exception {
        
        // Read metadata for the root key's value
        NoteBytesMetaData rootValueMetaData = reader.nextMetaData();
       
        int rootMetadataLength = rootValueMetaData.getLength();
                
        AESBackedOutputStream divertedRoot = new AESBackedOutputStream(dataDir, secretKey, rootMetadataLength,(int)(StreamUtils.PIPE_BUFFER_SIZE * 0.9), false);

        IntCounter depthCounter = new IntCounter(targetPath.length == 1 ? 0 : 1);
        NoteBytes filePathResult = divertAndParseToTempFile(divertedRoot, secretKey, targetPath, depthCounter, rootValueMetaData.getLength(), reader);

        if (filePathResult != null) {
            writer.write(rootValueMetaData);

            // File exists - stream temp file back unchanged
            try(
                NoteBytesReader backedReader = new NoteBytesReader(
                    new AESBackedInputStream(divertedRoot, secretKey)
                )
            ){
                StreamUtils.readWriteBytes(backedReader, writer);
            }
            
            // Continue streaming remaining top-level content
            StreamUtils.readWriteBytes(reader, writer);
            return filePathResult;
        } else {
            NoteBytes newFilePath = getNewFilePath(dataDir);
            NoteBytesPair insertionPair = buildPathStructure( targetPath, depthCounter.get(), newFilePath);

            NoteBytesMetaData newRootValueMetadata = new NoteBytesMetaData(rootValueMetaData.getType(), rootValueMetaData.getLength() + insertionPair.byteLength());
            writer.write(newRootValueMetadata);

            try (
                NoteBytesReader backedReader = new NoteBytesReader(new AESBackedInputStream(divertedRoot, secretKey))
            ){  
                IntCounter bytesProcessed = new IntCounter();
                streamBackWithSizeAdjustments(insertionPair, backedReader, bytesProcessed, writer, targetPath,1, depthCounter.get(), insertionPair.byteLength());
                StreamUtils.readWriteBytes(backedReader, writer);
            }
            // Continue streaming remaining top-level content  
            StreamUtils.readWriteBytes(reader, writer);
            return newFilePath;
        
        }
    }



    


    private static NoteBytes divertAndParseToTempFile(AESBackedOutputStream abaos, SecretKey secretKey, 
        NoteString[] targetPath, IntCounter depthCounter, int contentLength, NoteBytesReader reader
    ) throws Exception {
      
    
        try(NoteBytesWriter writer = new NoteBytesWriter(abaos)) {
            // Stream the content to temp file while parsing
            IntCounter bytesCounter = new IntCounter();
            IntCounter regressionLength = new IntCounter(contentLength);
            return parseContentWhileWritingToTemp(targetPath, depthCounter, bytesCounter, regressionLength, 
                contentLength, reader, writer);
        }
        
        
    }

    private static NoteBytes parseContentWhileWritingToTemp(NoteString[] targetPath, IntCounter depthCounter, 
        IntCounter byteCounter, IntCounter regressionLength, int contentLength, NoteBytesReader reader, 
        NoteBytesWriter tmpWriter) throws Exception 
    {
        int currentDepth = depthCounter.get();
        int maxLevel = targetPath.length;

        if(currentDepth == 0){
            depthCounter.add(1);
            return searchForFilePathKey(byteCounter,contentLength, contentLength, reader, tmpWriter);
            
        }else if(
            currentDepth < maxLevel && 
            byteCounter.get() < contentLength && 
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
                if(key.equals(NotePathFactory.FILE_PATH)){
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
                return null;
            }
        }
        return null;
        
    }
   


    private static boolean streamBackWithSizeAdjustments(NoteBytesPair insertionPair, NoteBytesReader tmpReader, 
        IntCounter bytesProcessed, NoteBytesWriter writer, NoteString[] targetPath, int currentDepth, 
        int insertionDepth, int insertionSizeIncrease
    ) throws Exception {

        if(currentDepth == insertionDepth && insertionDepth == targetPath.length){
            writer.write(insertionPair); 
            return true;
        }else{
            NoteBytes key = tmpReader.nextNoteBytes();
            if (key == null){
                throw new IllegalStateException("Unexpected end of file found");
            }
            NoteBytes currentPathKey = targetPath[currentDepth];

            bytesProcessed.add(writer.write(key));
            
            // Read value metadata explicitly
            NoteBytesMetaData valueMetaData = tmpReader.nextMetaData();
            if (valueMetaData == null){
                throw new IllegalStateException("Corrupted end of file found");
            }
            
            if(key.equals(currentPathKey)){
                int newSize = valueMetaData.getLength() + insertionSizeIncrease;
                NoteBytesMetaData adjustedMetaData = new NoteBytesMetaData(valueMetaData.getType(), newSize);
                bytesProcessed.add(writer.write(adjustedMetaData));
                
                if (currentDepth < insertionDepth) {
                    return streamBackWithSizeAdjustments(insertionPair, tmpReader,bytesProcessed, writer, 
                    targetPath,currentDepth + 1, insertionDepth, insertionSizeIncrease);
                } else{
                    writer.write(insertionPair);
                    return true;
                } 
            }else{
                bytesProcessed.add(writer.write(valueMetaData));
                bytesProcessed.add(StreamUtils.readWriteNextBytes(valueMetaData.getLength(), tmpReader, writer));
                return streamBackWithSizeAdjustments(insertionPair, tmpReader, bytesProcessed, writer, targetPath,
                    currentDepth, insertionDepth, insertionSizeIncrease);
            }
        }
      

    }



   
}
