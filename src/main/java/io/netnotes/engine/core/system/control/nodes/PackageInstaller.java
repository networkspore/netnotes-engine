package io.netnotes.engine.core.system.control.nodes;


import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;

/**
 * PackageInstaller - Downloads and installs packages
 * 
 * Install process:
 * 1. Download package files
 * 2. Parse and verify manifest.json
 * 3. Store files in encrypted NoteFiles
 * 4. Register in InstallationRegistry
 * 
 * Does NOT load into runtime - that's AppData's job
 */
public class PackageInstaller {
    private final AppData appData;
    
    public PackageInstaller(AppData appData) {
        this.appData = appData;
    }
    
    public CompletableFuture<InstalledPackage> installPackage(PackageInfo pkg) {
        System.out.println("[PackageInstaller] Installing " + pkg.getName());
        
        // Build install path: node-packages/{name}/{version}/
        NoteStringArrayReadOnly installPath = new NoteStringArrayReadOnly(
            "node-packages",
            pkg.getName(),
            pkg.getVersion()
        );
        
        return appData.getAppDataInterface(new NoteBytesReadOnly("node-packages"))
            .getNoteFile(installPath.append("package.jar"))
            .thenCompose(noteFile -> downloadToNoteFile(pkg.getDownloadUrl(), noteFile))
            .thenApply(v -> {
                // Create InstalledPackage metadata
                InstalledPackage installed = new InstalledPackage(
                    pkg.getPackageId(),
                    pkg.getName(),
                    pkg.getVersion(),
                    pkg.getCategory(),
                    pkg.getDescription(),
                    pkg.getRepository(),
                    installPath,
                    pkg.getManifest(),
                    System.currentTimeMillis()
                );
                
                System.out.println("[PackageInstaller] Successfully installed " + 
                    pkg.getName());
                
                return installed;
            });
    }
    
    private CompletableFuture<NoteBytesObject> downloadToNoteFile(
            String downloadUrl,
            NoteFile noteFile) {
        
        PipedOutputStream outputStream = new PipedOutputStream();
        
        CompletableFuture<Void> downloadFuture = UrlStreamHelpers.transferUrlStream(
            downloadUrl, outputStream, null, appData.getExecService());
        
        CompletableFuture<NoteBytesObject> writeFuture = 
            noteFile.writeOnly(outputStream);
        
        return CompletableFuture.allOf(downloadFuture, writeFuture)
            .thenCompose(v -> writeFuture);
    }
}