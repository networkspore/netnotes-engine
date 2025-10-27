package io.netnotes.engine.plugins;

import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.noteFiles.notePath.NotePath;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;
import io.netnotes.engine.utils.streams.StreamUtils.StreamProgressTracker;

/**
 * Handles downloading plugin JAR files from GitHub releases and saving them to NoteFiles.
 */
public class OSGiPluginDownloader {
    
    private final AppDataInterface m_appData;
    private final ExecutorService m_execService;
    
    
    public OSGiPluginDownloader(AppDataInterface appData, ExecutorService execService) {
        m_appData = appData;
        m_execService = execService;
    }
    
    /**
     * Download and install a plugin from a GitHub release.
     * 
     * @param release The AppRelease containing download URL
     * @return CompletableFuture with the installed plugin's NoteFile path
     */
    public CompletableFuture<OSGiPluginMetaData> downloadAndInstall(OSGiPluginRelease release, boolean enabled, StreamProgressTracker progressTracker) {

        return downloadToNoteFile(release.getDownloadUrl(), release.createAssetPath(), progressTracker)
            .thenApply(noteFile ->  new OSGiPluginMetaData(release, enabled));
    }
    
    /**
     * Download a JAR file from URL directly to a NoteFile.
     */
    private CompletableFuture<NoteFile> downloadToNoteFile(String downloadUrl, NoteStringArrayReadOnly path,
        StreamProgressTracker progressTracker
    ) {
       
        return m_appData.getNoteFile(path)
            .thenCompose(noteFile ->{ 
                PipedOutputStream outputStream = new PipedOutputStream();
                CompletableFuture<Void> copyFuture = 
                    UrlStreamHelpers.copyUrlStream(downloadUrl, outputStream, progressTracker, m_execService);
                CompletableFuture<NoteBytesObject> writeFuture = noteFile.writeOnly(outputStream);

                return CompletableFuture.allOf(copyFuture, writeFuture).thenApply(v -> noteFile);
            });
     
    }

    

    /**
     * Delete a plugin's JAR file.
     */
    public CompletableFuture<NotePath> deletePluginJar(NoteStringArrayReadOnly jarPath, AsyncNoteBytesWriter progressWriter) {
        return m_appData.deleteNoteFilePath(jarPath, false, progressWriter);
    }
 
}