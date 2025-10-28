package io.netnotes.engine.plugins;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.utils.streams.StreamUtils;

/**
 * Loads/Unloads OSGi bundles from NoteFiles.
 */
public class OSGiBundleLoader {
    
    private final BundleContext m_bundleContext;
    private final ExecutorService m_execService;
    
    public OSGiBundleLoader(BundleContext bundleContext, ExecutorService execService) {
        m_bundleContext = bundleContext;
        m_execService = execService;
    }
    
    /**
     * Load a plugin bundle from a NoteFile.
     * 
     * @param noteFile NoteFile containing the JAR
     * @return CompletableFuture with the installed Bundle
     */
    public CompletableFuture<Bundle> loadBundleFromNoteFile(NoteFile noteFile) {
      
        Bundle existingBundle = getInstalledBundle(noteFile);
        if(existingBundle == null){
            PipedOutputStream readOutput = new PipedOutputStream();
            noteFile.readOnly(readOutput);
            
            return CompletableFuture.supplyAsync(()->{
                try(PipedInputStream inputStream = new PipedInputStream(readOutput, StreamUtils.PIPE_BUFFER_SIZE)){
                    String bundleLocation = getBundleLocation(noteFile);

                    Bundle bundle = m_bundleContext.installBundle(
                        bundleLocation, 
                        inputStream
                    );
                    bundle.start();
                    System.out.println("Successfully loaded bundle: " + bundleLocation);
                    return bundle;
                }catch(Exception e){
                    throw new CompletionException("Failed loading bundle", e);
                }
            }, m_execService);
        }else{
            return CompletableFuture.completedFuture(existingBundle);
        }
     
    }
    
    public String getBundleLocation(NoteFile noteFile){
        return "plugin://" + noteFile.getUrlPathString();
    }

    /**
     * Unload a bundle.
     */
    public CompletableFuture<Void> unloadBundle(NoteFile noteFile) {
        return CompletableFuture.runAsync(() -> {
            try {
                Bundle bundle = getInstalledBundle(noteFile);
                if(bundle == null){
                    return;
                }
                if (bundle.getState() == Bundle.ACTIVE) {
                    bundle.stop();
                }
                bundle.uninstall();
                System.out.println("Unloaded bundle: " + getBundleLocation(noteFile));
            } catch (BundleException e) {
                throw new CompletionException("Failed to unload bundle", e);
            }
        }, m_execService);
    }
    
    public Bundle getInstalledBundle(NoteFile noteFile){
        String bundleLocation = getBundleLocation(noteFile);
        return m_bundleContext.getBundle(bundleLocation);
    }

    /**
     * Get the state of a bundle as a string.
     */
    public static String getBundleState(Bundle bundle) {
        return switch (bundle.getState()) {
            case Bundle.UNINSTALLED -> "Uninstalled";
            case Bundle.INSTALLED -> "Installed";
            case Bundle.RESOLVED -> "Resolved";
            case Bundle.STARTING -> "Starting";
            case Bundle.STOPPING -> "Stopping";
            case Bundle.ACTIVE -> "Active";
            default -> "UNKNOWN";
        };
    }
}