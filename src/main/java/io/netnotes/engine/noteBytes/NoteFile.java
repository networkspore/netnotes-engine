package io.netnotes.engine.noteBytes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.gson.JsonObject;

import io.netnotes.engine.Utils;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public class NoteFile extends NoteListString {

    private NoteFileInterface m_networksData = null;
    private File m_file;


    public NoteFile(NoteListString listString, File file, NoteFileInterface networksData){
        super(listString.toString());
        m_networksData = networksData;
        m_file = file;

    }

    public void acquire() throws InterruptedException{
        getSemaphore().acquire();
    }

    public void release(){
        getSemaphore().release();
    }

    public ExecutorService getExecService(){
        return m_networksData.getExecService();
    }

    public boolean isNetworksData(){
        return m_networksData != null;
    }

    public File getFile(){
        return m_file;
    }

    public File getTmpFile(){
        return new File(getFile().getParentFile().getAbsolutePath() + "/" + getFile().getName() + ".tmp");
    }

    protected NoteFileInterface getNoteFileInterface(){
        return m_networksData;
    }


    public boolean isFile(){
        File file = getFile();
        return file != null && file.isFile() && file.length() > 12;
    }

    public static URL getResourceURL(String resourceLocation){
        return resourceLocation != null ? Utils.class.getResource(resourceLocation) : null;
    }

   
    public void readWriteBytes(PipedOutputStream inParseStream, PipedOutputStream outParseStream, EventHandler<WorkerStateEvent> onFailed) throws IOException{

        PipedOutputStream decryptedOutputStream = new PipedOutputStream();
        getNoteFileInterface().readEncryptedFile(this, decryptedOutputStream,()->{
            Utils.writeStreamToStream(decryptedOutputStream, inParseStream, getExecService(), onFailed);

            PipedOutputStream writerOutputStream = new PipedOutputStream();
            Utils.writeStreamToStream(inParseStream, writerOutputStream, getExecService(), onFailed);

            getNoteFileInterface().writeEncryptedFile(this, writerOutputStream, onFailed);  
        }, onFailed);

   
    }

    

    public Future<?> getFileBytes(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {
                PipedOutputStream outParseStream = new PipedOutputStream();
                PipedOutputStream inParseStream = new PipedOutputStream();
                readWriteBytes(inParseStream, outParseStream, onFailed);

                try(
                    PipedInputStream inputStream = new PipedInputStream(inParseStream);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ){
                    byte[] buffer = new byte[Utils.DEFAULT_BUFFER_SIZE];
                    int length = 0;

                    while((length = inputStream.read(buffer)) != -1){
                        outParseStream.write(buffer, 0, length);
                        outParseStream.flush();
                        byteArrayOutputStream.write(buffer, 0, length);
                    }

                    return byteArrayOutputStream.toByteArray();
                }
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return getExecService().submit(task);
    }

    public Future<?> getFileNoteBytes(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
       Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {
                PipedOutputStream outParseStream = new PipedOutputStream();
                PipedOutputStream inParseStream = new PipedOutputStream();
                readWriteBytes(inParseStream, outParseStream, onFailed);

                try(
                    PipedInputStream inputStream = new PipedInputStream(inParseStream);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ){
                    byte[] buffer = new byte[Utils.DEFAULT_BUFFER_SIZE];
                    int length = 0;

                    while((length = inputStream.read(buffer)) != -1){
                        outParseStream.write(buffer, 0, length);
                        outParseStream.flush();
                        byteArrayOutputStream.write(buffer, 0, length);
                    }

                    byte[] bytes = byteArrayOutputStream.toByteArray();

                    return new NoteBytes(bytes);
                }
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return getExecService().submit(task);
    }

    public Future<?> getFileJson(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {
                PipedOutputStream outParseStream = new PipedOutputStream();
                PipedOutputStream inParseStream = new PipedOutputStream();
                readWriteBytes(inParseStream, outParseStream, onFailed);

                try(
                    PipedInputStream inputStream = new PipedInputStream(inParseStream);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ){
                    byte[] buffer = new byte[Utils.DEFAULT_BUFFER_SIZE];
                    int length = 0;

                    while((length = inputStream.read(buffer)) != -1){
                        outParseStream.write(buffer, 0, length);
                        outParseStream.flush();
                        byteArrayOutputStream.write(buffer, 0, length);
                    }

                    byte[] bytes = byteArrayOutputStream.toByteArray();

                    return new NoteBytes(bytes).getAsJsonObject();
                }
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return getExecService().submit(task);
    }

    public Future<?> saveEncryptedFile(PipedOutputStream pipedOutputStream, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        return getNoteFileInterface().saveEncryptedFile(this, pipedOutputStream, onSucceeded, onFailed);
    }

    public Future<?> saveFileBytes(byte[] bytes, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        return getNoteFileInterface().saveEncryptedFile(this, bytes, onSucceeded, onFailed);
    }

    public Future<?> saveFileNoteBytes(NoteBytes noteBytes, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        return saveFileBytes(noteBytes.get(), onSucceeded, onFailed);
    }

    public Future<?> saveFileJson(JsonObject json, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        return saveFileNoteBytes(new NoteJsonObject(json), onSucceeded, onFailed);
    }


    public interface NoteFileInterface{
        ExecutorService getExecService();
        Future<?> readEncryptedFile(NoteFile noteFile, PipedOutputStream pipedOutput, Runnable onAquired, EventHandler<WorkerStateEvent> onFailed);
        Future<?> writeEncryptedFile(NoteFile noteFile, PipedOutputStream pipedOutputStream, EventHandler<WorkerStateEvent> onFailed);
        Future<?> saveEncryptedFile(NoteFile noteFile, PipedOutputStream pipedOutputStream, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed);
        Future<?> saveEncryptedFile(NoteFile noteFile, byte[] bytes, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed);
    }
    
}
