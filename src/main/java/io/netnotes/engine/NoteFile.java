package io.netnotes.engine;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public class NoteFile extends NoteListString {

    private NetworksInterface m_networksData = null;
    private File m_file;


    public NoteFile(NoteListString listString, File file, NetworksInterface networksData){
        super(listString.toString());
        m_networksData = networksData;
        m_file = file;
    }

    public void acquire() throws InterruptedException{
        getSemaphore().acquire();
    }

    public void release(){
        getSemaphore().release();
        releaseFile();
    }

    protected void releaseFile(){
        m_networksData.release();
    }

    public boolean isAquired(){
        return getSemaphore().availablePermits() < 1;
    }

    public boolean isNetworksData(){
        return m_networksData != null;
    }

    public File getFile(){
        return m_file;
    }


    protected NetworksInterface getNetworksInterface(){
        return m_networksData;
    }
  
   

    @Override
    public int hashCode(){
        return -1;
    }

    public boolean isFile(){
        return false;
    }

    public static URL getResourceURL(String resourceLocation){
        return resourceLocation != null ? Utils.class.getResource(resourceLocation) : null;
    }

   

   /* public Future<?> getNetworksDataStream( EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NetworksData networksData = m_networksData;

    }

    public Future<?> getResourceStream( EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        URL location = resourceLocation != null ? Utils.class.getResource(resourceLocation) : null;
        location.openStream()
    }*/



     public Future<?> readEncryptedFile(PipedOutputStream pipedOutput, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NetworksInterface networksData = m_networksData;

        if(networksData != null && byteLength() > 0){
            return networksData.readEncryptedFile( pipedOutput, onRead->{
                try {
                    pipedOutput.close();
                } catch (IOException e) {

                }
            }, onReadFailed->{
                try {
                    pipedOutput.close();
                } catch (IOException e) {

                }
       
                Utils.returnException( onReadFailed.getSource().getException(), m_networksData.getExecService(), onFailed);
              
            });
        }else{
            return Utils.returnException(NoteConstants.ERROR_INVALID, networksData.getExecService(), onFailed);
        }
    }

    public Future<?> writeEncryptedFile(PipedInputStream pipedWriterInput, EventHandler<WorkerStateEvent> onFailed){
        NetworksInterface networksData = m_networksData;
        if(networksData != null && byteLength() > 0){
            return networksData.writeEncryptedFile( pipedWriterInput, onFailed);
        }else{
            return Utils.returnException(NoteConstants.ERROR_INVALID, networksData.getExecService(), onFailed);
        }
    }

    public interface NetworksInterface{
        Future<?> getNoteFile(NoteListString path, EventHandler<WorkerStateEvent> onComplete);
        ExecutorService getExecService();
        Future<?> readEncryptedFile( PipedOutputStream pipedOutput, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed);
        Future<?> writeEncryptedFile(PipedInputStream pipedWriterInput, EventHandler<WorkerStateEvent> onFailed);
        void release();

    }
    
}
