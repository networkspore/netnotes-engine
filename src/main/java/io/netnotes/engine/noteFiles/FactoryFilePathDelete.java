package io.netnotes.engine.noteFiles;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import javax.crypto.SecretKey;

import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;

public class FactoryFilePathDelete {
    
    private static final int  metaDataSize  = NoteBytesMetaData.STANDARD_META_DATA_SIZE;
    
    public static CompletableFuture<NoteBytesObject> deleteNoteFilePath(NotePath notePath, 
        SecretKey secretKey, ExecutorService execService
    ) {
   
        return CompletableFuture
            .supplyAsync(() -> {
                PipedOutputStream decryptedOutput = new PipedOutputStream();
                PipedOutputStream parsedOutput = new PipedOutputStream();
            
                try {
                
                    // Chain the operations: decrypt -> parse -> encrypt -> return file
                    CompletableFuture<NoteBytesObject> decryptFuture = 
                        FileStreamUtils.performDecryption(notePath.getPathLedger(), decryptedOutput, secretKey, execService);
                    
                    CompletableFuture<Void> parseFuture = 
                        parseStreamForRoot( notePath, secretKey, decryptedOutput, parsedOutput, execService);
                    
                    CompletableFuture<NoteBytesObject> saveFuture = 
                        FileStreamUtils.saveEncryptedFileSwap(notePath.getPathLedger(), secretKey, parsedOutput);
                    
                    // Wait for all operations to complete and return the result file
                    return CompletableFuture.allOf(decryptFuture, parseFuture, saveFuture)
                        .thenCompose(v -> saveFuture) // Return the file ledger encryption result
                        .join(); // Block
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process existing data file", e);
                }finally{
                    StreamUtils.safeClose(decryptedOutput);
                    StreamUtils.safeClose(parsedOutput);
                }
            }, execService);
    }
    

    public static CompletableFuture<Void> parseStreamForRoot(NotePath notePath, SecretKey secretKey,
            PipedOutputStream decryptedInputStream,
            PipedOutputStream parsedOutputStream, ExecutorService execService) {
        return CompletableFuture
            .runAsync(() -> {
          
                try (
                    NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(decryptedInputStream, StreamUtils.PIPE_BUFFER_SIZE));
                    NoteBytesWriter writer = new NoteBytesWriter(parsedOutputStream)
                ) {      
                    NoteBytes rootPathKey = notePath.getTargetPath(0);
                    NoteBytes key = reader.nextNoteBytes();

                    while(key != null){
                        NoteBytesMetaData metaData = reader.nextMetaData();
                        if(metaData == null){
                            throw new IllegalArgumentException("parseStreamForRoot: Unexpected end of file after key: " + key );
                        }
                        if(key.equals(rootPathKey)){
                            //increment level and add metaData to path
                            notePath.addMetaData(metaData);
                             if(notePath.getSize() == 1 && notePath.isRecursive()){
                                //entire root path and all children deleted
                                recursiveDeleteFull(notePath, metaData.getLength(), reader);
                                StreamUtils.readWriteBytes(reader, writer);
                                return;
                            }else{
                                processFoundRootKey(notePath, secretKey, reader, writer);
                            }
                        }else{
                            writer.write(key);
                            writer.write(metaData);
                            StreamUtils.readWriteNextBytes(metaData.getLength(), reader, writer);
                        }

                    }
                    
                } catch (Exception e) {
                    throw new RuntimeException(NoteMessaging.Error.INVALID, e);
                }
            }, execService);
    }

    

    private static void processFoundRootKey(NotePath notePath,  SecretKey secretKey, 
        NoteBytesReader reader, NoteBytesWriter writer
    ) throws Exception {
        
        AESBackedOutputStream aesbos = new AESBackedOutputStream(notePath.getDataDir(), secretKey, notePath.getPathSize(0), 
            (int)(StreamUtils.PIPE_BUFFER_SIZE * 0.9));

        try(
            NoteBytesWriter tmpWriter = new NoteBytesWriter(aesbos);
        ){
            processNotePathToStream(notePath, notePath.getPathSize(0), reader, tmpWriter);
        }

        if(notePath.getByteCounter().get() != 0){

            try(NoteBytesReader tmpReader = new NoteBytesReader(
                new AESBackedInputStream(aesbos, secretKey)
            )){
                notePath.getCurrentLevel().set(0);
                notePath.getByteCounter().set(0);
              
                readBackPathFromTmp(notePath, notePath.getPathSize(0) - notePath.getDeletedFilePathLength().get(), tmpReader, writer);
                
            }
        }
        StreamUtils.readWriteBytes(reader, writer);
        return;
    }

    private static void readBackPathFromTmp( NotePath notePath, int bucketSize, NoteBytesReader tmpReader, NoteBytesWriter writer) throws IOException{
        
        while(notePath.getByteCounter().get() < bucketSize){
            
            NoteBytes key = tmpReader.nextNoteBytes();
            NoteBytes currentPathKey = notePath.getCurrentPathKey();

            if(key == null){
                throw new IllegalArgumentException("readBackPathFromTmp: Unexpected end of file:" + notePath.getByteCounter().get() + " expected:" + bucketSize);
            }

            if( notePath.getCurrentLevel().get() < notePath.getSize() && key.equals(currentPathKey)){
                
                notePath.getByteCounter().add(key.byteLength() + metaDataSize);

                NoteBytesMetaData metaData = tmpReader.nextMetaData();
                notePath.getByteCounter().add(metaDataSize);

                int currentPathBucketSize = metaData.getLength();
                
                //Check if path has been collapsed by having no further file paths below it
                if(isProceeedLevel(notePath)){
                    writer.write(key);
                    
                    int pathSize = currentPathBucketSize - notePath.getDeletedFilePathLength().get();
            
                    writer.write(new NoteBytesMetaData(metaData.getType(), pathSize));
                    notePath.getCurrentLevel().increment();

                    if(notePath.getCurrentLevel().get() < notePath.getSize()){
                        readBackPathFromTmp(notePath, notePath.getByteCounter().get() + currentPathBucketSize, tmpReader, writer);
                    }else if(notePath.isRecursive()){
                        tmpReader.skipData(currentPathBucketSize);
                        notePath.getByteCounter().add(currentPathBucketSize);
                       
                    }else{
                        readBackPathFromTmp(notePath, notePath.getByteCounter().get() + currentPathBucketSize, tmpReader, writer);
                    }
                }else{
                    notePath.getByteCounter().add(currentPathBucketSize);
                    tmpReader.skipData(currentPathBucketSize);

            
                }
            
            }else {
                //increment byteCounter and write key / value
                notePath.skipKeyValue(key, tmpReader, writer);
            }

            
        }
    }


    //Helper functions

    private static void recursiveDeleteFull(NotePath notePath, int bucketEnd, NoteBytesReader reader) throws IOException{

        while(notePath.getByteCounter().get() < bucketEnd){
            NoteBytes key = reader.nextNoteBytes();
      
            if(key != null){
                int keyByteSize = (key.byteLength() + metaDataSize);

                notePath.getDeletedFilePathLength().add(keyByteSize);
                notePath.getByteCounter().add(keyByteSize);

                byte keyType = key.getByteDecoding().getType();

                if(key.equals(NotePath.FILE_PATH)){

                    NoteBytes filePathValue = reader.nextNoteBytes();
          
                    if(notePath.getTargetFilePath() == null){
                        notePath.setTargetFilePath(filePathValue);
                    }
                    int filePathValueSize = (filePathValue.byteLength() + metaDataSize);

                    notePath.getByteCounter().add( filePathValueSize);
                    notePath.getDeletedFilePathLength().add(filePathValueSize);
                    deleteFilePathValue(filePathValue);
                }else if(keyType == NoteBytesMetaData.STRING_TYPE){
                    NoteBytesMetaData metaData = reader.nextMetaData();
                
                    notePath.getByteCounter().add(metaDataSize);
                    notePath.getDeletedFilePathLength().add( metaDataSize);

                    recursiveDeleteFull(notePath, notePath.getByteCounter().get() + metaData.getLength(), reader);
                }else{
                    throw new IllegalArgumentException("Unexpected data type in file:" + keyType);
                }
            }else{
                 throw new IllegalArgumentException("Unexpected end of file:" + notePath.getByteCounter().get() + " expected:" + bucketEnd);
            }
        }
    }

    private static void deleteFilePathValue(NoteBytes filePathValue){
        File file = new File(filePathValue.getAsString());
        try{
            Files.deleteIfExists(file.toPath());
        }catch(IOException e){
            System.err.println("File delete failed: " + file.getAbsolutePath());
        }
    }


   
    private static void processNotePathToStream( NotePath notePath, int bucketSize, NoteBytesReader reader, NoteBytesWriter writer) throws IOException{
        
        while(notePath.getByteCounter().get() < bucketSize){
            NoteBytes nextNoteBytes = reader.nextNoteBytes();
            
            if(nextNoteBytes == null){
                if(notePath.getByteCounter().get() < bucketSize){
                    throw new IllegalArgumentException("Unexpected end of file:" + notePath.getByteCounter().get() + " expected:" + bucketSize);
                }else{
                    return;
                }
            }

            NoteBytesReadOnly currentPath = notePath.getCurrentPathKey();
        
            if(notePath.getByteCounter().get() < bucketSize){
                if(nextNoteBytes.equals(NotePath.FILE_PATH)){
                    //isTargetFilePath non-recursive delete
                    if( notePath.getCurrentLevel().get() == notePath.getSize()){
                        NoteBytes filePathValue = reader.nextNoteBytes();

                        notePath.setTargetFilePath(filePathValue);
                        deleteFilePathValue(filePathValue);
                        NoteBytesObject pathObject = notePath.createCurrentFilePathObject();

                        int filePathSize = pathObject.byteLength();
                        notePath.getByteCounter().add(filePathSize);
                        notePath.getDeletedFilePathLength().add(filePathSize);

                        int remaining = bucketSize - notePath.getByteCounter().get();
                        notePath.getByteCounter().add(remaining);
                        StreamUtils.readWriteNextBytes(remaining, reader, writer);
                        return;
                    }else{
                        notePath.skipKeyValue(nextNoteBytes, reader, writer);
                    }
                }else if( currentPath != null && nextNoteBytes.equals(currentPath)){
                    NoteBytesMetaData metaData = reader.nextMetaData();
                   

                    //addMetaData and increment notePath.currentLevel
                    notePath.addMetaData(metaData);

                    //process next level or recursive delete
                    if(notePath.getCurrentLevel().get() == notePath.getSize() && notePath.isRecursive()){
                        
                        //document deleted key + metadata + value metadata + all children length 
                        notePath.getDeletedFilePathLength().add((metaDataSize * 2) + nextNoteBytes.byteLength());
                        int remainingPathSize = notePath.getByteCounter().get() + metaData.getLength();

                        recursiveDeleteFull(notePath, remainingPathSize, reader);

                        int remaining = bucketSize - notePath.getByteCounter().get();
                        
                        StreamUtils.readWriteNextBytes(remaining, reader, writer);
                        notePath.getByteCounter().add(remaining);
                    }else{
                        notePath.getByteCounter().add(writer.write(nextNoteBytes));
                        notePath.getByteCounter().add(writer.write(metaData));

                        processNotePathToStream(notePath, notePath.getByteCounter().get() + metaData.getLength(), reader, writer);
                    }
                    
                }else{
                    notePath.skipKeyValue(nextNoteBytes, reader, writer);
                }
                
            }
        }
        
    }

    private static int calculateTargetPathSizeAtLevel(NotePath notePath, int level){
        int size = 0;
        int i = level;

        while(i + 1 < notePath.getSize()){
            size += notePath.getTargetPath(level + 1).byteLength() + (metaDataSize *2);
            i++;
        }

        return size + notePath.getDeletedFilePathLength().get();
    }

    private static boolean isProceeedLevel(NotePath notePath){
        int level = notePath.getCurrentLevel().get();
        
        return level < notePath.getSize() && notePath.getPathSize()[level] > calculateTargetPathSizeAtLevel(notePath, level);
    }
   
}
