package io.netnotes.engine.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import io.netnotes.engine.core.nodes.INode;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.messaging.task.TaskMessages;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;
import io.netnotes.engine.utils.VirtualExecutors;


public class AppData {

    private final NoteFileService m_noteFileRegistry;
    private final Map<NoteBytesReadOnly, INode> m_nodeRegistry = new ConcurrentHashMap<>();

    private final SettingsData m_settingsData;

    public AppData(SettingsData settingsData){
        m_noteFileRegistry = new NoteFileService(settingsData);
        m_settingsData = settingsData;
    }


    public ExecutorService getExecService(){
        return m_noteFileRegistry.getExecService();
    }

    public ScheduledExecutorService getSchedualedExecService(){
        return m_noteFileRegistry.getScheduledExecutor();
    }

    public Map<NoteBytesReadOnly, INode> nodeRegistry(){
        return m_nodeRegistry;
    }

    public NoteFileService getNoteFileService(){
        return m_noteFileRegistry;
    }


    public AppDataInterface getAppDataInterface(NoteBytesReadOnly id){
        return new AppDataInterface(){
            final NoteBytesReadOnly startingPath = new NoteBytesReadOnly(id.get(), NoteBytesMetaData.STRING_TYPE);
            @Override
            public void shutdown() {
                AppData.this.shutdown(null);
            }

            @Override
            public CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path) {
                if(path == null){
                    return CompletableFuture.failedFuture(new NullPointerException("Path is null"));
                }
                return CompletableFuture.supplyAsync(()->{
                    NoteBytesReadOnly[] originalPath = path.getAsArray();

                    boolean isStartingPath = originalPath.length > 0 ? originalPath[0].equals(startingPath) : false;

                    if(isStartingPath){
                        NoteBytesReadOnly[] copiedPath = new NoteBytesReadOnly[originalPath.length];
                        for(int i = 0; i < originalPath.length; i++){
                            copiedPath[i] = new NoteBytesReadOnly(originalPath[i].get(), NoteBytesMetaData.STRING_TYPE);
                        }
                        return new NoteStringArrayReadOnly(copiedPath);
                    
                    }else{
                        NoteBytesReadOnly[] copiedPath = new NoteBytesReadOnly[originalPath.length + 1];

                        copiedPath[0] = startingPath;
                        for(int i = 0; i < originalPath.length; i++){
                            copiedPath[i + 1] = new NoteBytesReadOnly(originalPath[i].get(), NoteBytesMetaData.STRING_TYPE);
                        }
                        return new NoteStringArrayReadOnly(copiedPath);
                    
                    }
                }, VirtualExecutors.getVirtualExecutor())
                    .thenCompose(scopedPath->AppData.this.m_noteFileRegistry.getNoteFile(scopedPath));

            }

        

        };
    }


    public CompletableFuture<Void> shutdown(AsyncNoteBytesWriter progressWriter){
        if(progressWriter != null){
            ProgressMessage.writeAsync("AppData", 0, -1, "Closing any open files", progressWriter);
        }
        m_settingsData.shutdown();
        return getNoteFileService().prepareAllFoShutdown().exceptionally((ex)->{
                if(ex != null){
                    Throwable cause = ex.getCause();
                    String msg = "Error shutting down note file service: " + cause == null ? ex.getMessage() : ex.getMessage() + ": " + cause.toString();
                    System.err.println(msg);
                    ex.printStackTrace();
                    if(progressWriter != null){
                        TaskMessages.writeErrorAsync("AppData",msg, ex, progressWriter);
                    }
                }
                return null;
            }).thenApply((v)->null);
    }
}