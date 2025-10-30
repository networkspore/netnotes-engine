package io.netnotes.engine.utils.streams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;

public class StreamUtils {

    public static final int BUFFER_SIZE = 128 * 1024;
    public static final int PIPE_BUFFER_SIZE = 1024 * 1024;

    public static class StreamProgressTracker {
        private final AtomicLong bytesProcessed = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile long totalBytes = -1; // -1 means unknown
        private  Consumer<Double> m_onProgress = null;

        public long getBytesProcessed() {
            return bytesProcessed.get();
        }

        public void addBytesProcessed(long bytes) {
            bytesProcessed.addAndGet(bytes);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            cancelled.set(true);
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public void setTotalBytes(long total) {
            if(m_onProgress != null){
                m_onProgress.accept(getProgress());
            }
            this.totalBytes = total;
        }

        public double getProgress() {
            long bytesProcessed = this.bytesProcessed.get();
            return totalBytes > 0 ? (bytesProcessed > 0  ? bytesProcessed / totalBytes : 0) : -1;
        }

        public void setOnProgress(Consumer<Double> onProgress){
            m_onProgress = onProgress;
        }
    }

    public static void streamCopy(InputStream input, OutputStream output,
            StreamProgressTracker progressTracker) throws IOException {
        byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
        int length = 0;

        while ((length = input.read(buffer)) != -1) {
            if (progressTracker != null && progressTracker.isCancelled()) {
                throw new IOException("Operation cancelled");
            }

            output.write(buffer, 0, length);

            if (progressTracker != null) {
                progressTracker.addBytesProcessed(length);
            }
        }
    }

    public static void duplicateEntireStream(PipedOutputStream pipedOutput, PipedOutputStream output1,
            PipedOutputStream output2,
            StreamProgressTracker progressTracker) throws IOException {
        byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
        int length = 0;

        try (PipedInputStream input = new PipedInputStream(pipedOutput, PIPE_BUFFER_SIZE)) {
            while ((length = input.read(buffer)) != -1) {
                if (progressTracker != null && progressTracker.isCancelled()) {
                    throw new IOException("Operation cancelled");
                }
                output1.write(buffer, 0, length);
                output2.write(buffer, 0, length);
                if (progressTracker != null) {
                    progressTracker.addBytesProcessed(length);
                }
            }
        }
    }

    public static boolean safeClose(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
                return true;
            } catch (Exception e) {
                // Log but don't throw - we're cleaning up
                System.err.println("Warning: Error closing resource: " + e.getMessage());
                return false;
            }
        }
        return false;
    }


  

    public static byte[] readEntireOutputStream(PipedOutputStream outStream) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                PipedInputStream input = new PipedInputStream(outStream, PIPE_BUFFER_SIZE);) {

            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }

            return buffer.toByteArray();
        }
    }

    public static byte[] readByteAmount(int size, InputStream inputStream) throws IOException{
        try(UnsynchronizedByteArrayOutputStream byteOutput = new UnsynchronizedByteArrayOutputStream(size)){
            int bufferSize = size < StreamUtils.BUFFER_SIZE ? size : StreamUtils.BUFFER_SIZE;
            byte[] buffer = new byte[bufferSize];
            int length = 0;
            int remaining = size;
            while(remaining > 0 && ((length = inputStream.read(buffer, 0, remaining < bufferSize ? remaining : bufferSize)) != -1)){
                byteOutput.write(buffer, 0, length);
                remaining -= length;
            }
            if(remaining > 0){
                throw new IOException("Reached pre-mature end of stream expected: " + size);
            }
            return byteOutput.toByteArray();
        }
    }



    public static int readWriteNextBytes(int writeLength, NoteBytesReader reader, NoteBytesWriter writer) throws IOException {
        int remaining = writeLength;
        int bufferSize = StreamUtils.BUFFER_SIZE > writeLength ? writeLength : StreamUtils.BUFFER_SIZE;
        byte[] buffer = new byte[bufferSize];
        int length = 0;
        
        while (remaining > 0 && ((length = reader.read(buffer, 0, remaining < bufferSize ? remaining : bufferSize)) != -1)) {
            writer.write(buffer, 0, length);
            remaining -= length;
        }
        
        if (remaining > 0) {
            throw new IOException("Reached premature end of stream. Expected: " + writeLength + ", remaining: " + remaining);
        }

        return writeLength;
    }

    public static void readWriteBytes(NoteBytesReader reader, NoteBytesWriter writer) throws IOException {
        int length = 0;
        byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
        while ((length = reader.read(buffer)) != -1) {
            writer.write(buffer, 0, length);
        }
    }

    public static byte[] readInputStreamAsBytes(InputStream inputStream) throws IOException{
     
        try(UnsynchronizedByteArrayOutputStream outputStream = new UnsynchronizedByteArrayOutputStream()){
            byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
            int length = 0;

            while((length = inputStream.read(buffer)) != -1){
                outputStream.write(buffer, 0, length);
            }

            return outputStream.toByteArray();
        }
    }
    

    public static String readAsString(InputStreamReader reader) throws IOException{
        StringBuilder stringBuilder = new StringBuilder();
        char[] chars = new char[BUFFER_SIZE];
        int length;
        while ((length = reader.read(chars)) != -1) {
            stringBuilder.append(chars, 0, length);
        }
        return stringBuilder.toString();
        
    }

    public static String readAsString(InputStream inputStream, StreamProgressTracker progressTracker) throws IOException{
        StringBuilder stringBuilder = new StringBuilder();
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length =  inputStream.read(buffer)) != -1) {
            if (progressTracker != null && progressTracker.isCancelled()) {
                throw new IOException("Operation cancelled");
            }
         
            stringBuilder.append(new String(buffer, 0, length, StandardCharsets.UTF_8));
         
            if (progressTracker != null) {
                progressTracker.addBytesProcessed(length);
            }
        }
        return stringBuilder.toString();
    }


    public static JsonObject readJson(InputStreamReader reader) throws JsonIOException, JsonSyntaxException, IOException{
        return JsonParser.parseReader(reader).getAsJsonObject();
    }

    public static JsonArray readJsonArray(InputStreamReader reader) throws JsonIOException, JsonSyntaxException, IOException{
        return JsonParser.parseReader(reader).getAsJsonArray();
    }
}
