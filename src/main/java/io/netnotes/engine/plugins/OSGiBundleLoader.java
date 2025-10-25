package io.netnotes.engine.plugins;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteFiles.NoteFile;

/**
 * Loads OSGi bundles from NoteFiles.
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
     * @param pluginId Unique identifier for the plugin
     * @param noteFile NoteFile containing the JAR
     * @return CompletableFuture with the installed Bundle
     */
    public CompletableFuture<Bundle> loadBundleFromNoteFile(NoteBytes pluginId, NoteFile noteFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Read JAR bytes from NoteFile
                byte[] jarBytes = readJarFromNoteFile(noteFile);
                
                // Install bundle in OSGi framework
                String bundleLocation = "plugin://" + pluginId.getAsString();
                Bundle bundle = m_bundleContext.installBundle(
                    bundleLocation, 
                    new ByteArrayInputStream(jarBytes)
                );
                
                // Start the bundle
                bundle.start();
                
                System.out.println("Successfully loaded bundle: " + bundleLocation);
                return bundle;
                
            } catch (BundleException | IOException e) {
                throw new RuntimeException("Failed to load bundle from NoteFile", e);
            }
        }, m_execService);
    }
    
    /**
     * Read JAR bytes from a NoteFile.
     */
    private byte[] readJarFromNoteFile(NoteFile noteFile) throws IOException {
        PipedOutputStream readOutput = new PipedOutputStream();
        
        // Start read operation
        noteFile.readOnly(readOutput);
        
        try (PipedInputStream inputStream = new PipedInputStream(readOutput)) {
            return inputStream.readAllBytes();
        }
    }
    
    /**
     * Unload a bundle.
     */
    public CompletableFuture<Void> unloadBundle(Bundle bundle) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (bundle.getState() == Bundle.ACTIVE) {
                    bundle.stop();
                }
                bundle.uninstall();
                System.out.println("Unloaded bundle: " + bundle.getSymbolicName());
            } catch (BundleException e) {
                throw new RuntimeException("Failed to unload bundle", e);
            }
        }, m_execService);
    }
    
    /**
     * Get the state of a bundle as a string.
     */
    public static String getBundleState(Bundle bundle) {
        return switch (bundle.getState()) {
            case Bundle.UNINSTALLED -> "UNINSTALLED";
            case Bundle.INSTALLED -> "INSTALLED";
            case Bundle.RESOLVED -> "RESOLVED";
            case Bundle.STARTING -> "STARTING";
            case Bundle.STOPPING -> "STOPPING";
            case Bundle.ACTIVE -> "ACTIVE";
            default -> "UNKNOWN";
        };
    }
}