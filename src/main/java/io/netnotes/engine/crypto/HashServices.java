package io.netnotes.engine.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.ove.crypto.digest.Blake2b;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ProgressIndicator;

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



    public static Future<?> copyFileAndHash(File inputFile, File outputFile, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed, ProgressIndicator progressIndicator) {

        Task<HashData> task = new Task<HashData>() {
            @Override
            public HashData call() throws NoSuchAlgorithmException, MalformedURLException, IOException {
                long contentLength = -1;

                if (inputFile != null && inputFile.isFile() && outputFile != null && !inputFile.getAbsolutePath().equals(outputFile.getAbsolutePath())) {
                    contentLength = Files.size(inputFile.toPath());
                } else {
                    return null;
                }
                final Blake2b digest = Blake2b.Digest.newInstance(32);

                try(
                    FileInputStream inputStream = new FileInputStream(inputFile);
                    FileOutputStream outputStream = new FileOutputStream(outputFile);
                ){
                    byte[] buffer = new byte[contentLength < (long) StreamUtils.BUFFER_SIZE ? (int) contentLength : StreamUtils.BUFFER_SIZE];

                    int length;
                    long copied = 0;

                    while ((length = inputStream.read(buffer)) != -1) {

                        outputStream.write(buffer, 0, length);
                        digest.update(buffer, 0, length);

                        copied += (long) length;
                        if(progressIndicator != null){
                            updateProgress(copied, contentLength);
                        }
                    }



                }

                return new HashData(digest.digest());

            }

        };

        if (progressIndicator != null) {
            progressIndicator.progressProperty().bind(task.progressProperty());
        }

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }


    
}
