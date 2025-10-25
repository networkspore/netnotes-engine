package io.netnotes.engine.plugins;

import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
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
    
    // Base path for plugin storage
    private static final String PLUGINS_BASE_PATH = "plugins";
    private static final String JARS_SUBFOLDER = "jars";
    
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
    public CompletableFuture<PluginInstallResult> downloadAndInstall(OSGiPluginRelease release, StreamProgressTracker progressTracker) {
        String pluginId = release.getPluginInfo().getName();
        String version = release.getVersion();
        
        // Create path: plugins/jars/{pluginId}-{version}
        NoteStringArrayReadOnly jarPath = createPluginJarPath(pluginId, version);
        
        return downloadToNoteFile(release.getDownloadUrl(), jarPath, progressTracker)
            .thenApply(noteFile -> new PluginInstallResult(
                new NoteBytes(pluginId),
                version,
                jarPath,
                noteFile,
                release
            ));
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
     * Create a NoteFile path for a plugin JAR.
     */
    private NoteStringArrayReadOnly createPluginJarPath(String pluginId, String version) {
        String[] pathElements = {
            PLUGINS_BASE_PATH,
            JARS_SUBFOLDER,
            pluginId + "-" + version
        };
        return new NoteStringArrayReadOnly(pathElements);
    }

    /**
     * Delete a plugin's JAR file.
     */
    public CompletableFuture<NotePath> deletePluginJar(NoteStringArrayReadOnly jarPath, AsyncNoteBytesWriter progressWriter) {
        return m_appData.deleteNoteFilePath(jarPath, false, progressWriter);
    }
    
    /**
     * Result of a plugin installation.
     */
    public static class PluginInstallResult {
        private final NoteBytes pluginId;
        private final String version;
        private final NoteFile jarFile;
        private final OSGiPluginRelease release;
        
        public PluginInstallResult(
            NoteBytes pluginId,
            String version,
            NoteStringArrayReadOnly jarPath,
            NoteFile jarFile,
            OSGiPluginRelease release
        ) {
            this.pluginId = pluginId;
            this.version = version;
            this.jarFile = jarFile;
            this.release = release;
        }
        
        public NoteBytes getPluginId() { return pluginId; }
        public String getVersion() { return version; }
        public NoteStringArrayReadOnly getJarPath() { return jarFile.getPath(); }
        public NoteFile getJarNoteFile() { return jarFile; }
        public OSGiPluginRelease getRelease() { return release; }
    }
}