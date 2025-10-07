package io.netnotes.engine.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.ove.crypto.digest.Blake2b;

public class HashServices {

     public static byte[] digestFile(File file) throws  IOException {

        return digestFileBlake2b(file,32);
        
    }
    

    public static byte[] digestFileBlake2b(File file, int digestLength) throws IOException {
        final Blake2b digest = Blake2b.Digest.newInstance(digestLength);
        try(
            FileInputStream fis = new FileInputStream(file);
        ){
            int bufferSize = file.length() < StreamUtils.BUFFER_SIZE ? (int) file.length() : StreamUtils.BUFFER_SIZE;

            byte[] byteArray = new byte[bufferSize];
            int bytesCount = 0;

            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            };

            byte[] hashBytes = digest.digest();

            return hashBytes;
        }
    }

    public static boolean verifyBCryptPassword(NoteBytes password, NoteBytes hash) {
        BCrypt.Result result = BCrypt.verifyer(BCrypt.Version.VERSION_2A, LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)).verify(password.get(), hash.getBytes());
        return result.verified;
    }

   
    public static NoteBytes getBcryptHash(NoteBytes password) {
        return new NoteBytes( BCrypt.with(BCrypt.Version.VERSION_2A, RandomService.getSecureRandom(), LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)).hash(15, password.getChars()));
    }


    public static CompletableFuture<byte[]> copyFileAndReturnHash(File inputFile, File outputFile, ExecutorService execService) {

        return CompletableFuture.supplyAsync(()->{
            long contentLength = -1;

            if (inputFile != null && inputFile.isFile() && outputFile != null && !inputFile.getAbsolutePath().equals(outputFile.getAbsolutePath())) {
                try{
                    contentLength = Files.size(inputFile.toPath());
                }catch(IOException e){
                    throw new CompletionException("Cannot read input file",e);
                }
            } else {
                throw new CompletionException("Cannot process input file", inputFile == null || outputFile == null ? 
                    new NullPointerException(inputFile == null ? "inputFile null" : "outputFile null") : new IllegalStateException("inputFile and outputFile are the same"));
            }
            final Blake2b digest = Blake2b.Digest.newInstance(32);

            try(
                FileInputStream inputStream = new FileInputStream(inputFile);
                FileOutputStream outputStream = new FileOutputStream(outputFile);
            ){
                byte[] buffer = new byte[contentLength < (long) StreamUtils.BUFFER_SIZE ? (int) contentLength : StreamUtils.BUFFER_SIZE];

                int length;
                //long copied = 0;

                while ((length = inputStream.read(buffer)) != -1) {

                    outputStream.write(buffer, 0, length);
                    digest.update(buffer, 0, length);

                  //  copied += (long) length;
                }



            } catch (FileNotFoundException e) {
                throw new CompletionException(e);
            } catch (IOException e) {
                throw new CompletionException(e);
            }

            return digest.digest();

        }, execService);
    }


    
}
