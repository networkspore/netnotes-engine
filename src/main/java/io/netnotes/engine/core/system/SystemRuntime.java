package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import javax.crypto.SecretKey;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.NoteFileServiceInterface;
import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.nodes.NodeController;
import io.netnotes.engine.core.system.control.nodes.RepositoryManager;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;


public class SystemRuntime {

    private final NoteFileService noteFileService;
    private final ProcessRegistryInterface registryInterface;
    
    // Node registry: instanceId → INode (runtime cache)
   // private final Map<NoteBytesReadOnly, INode> m_nodeRegistry = new ConcurrentHashMap<>();
    
    // System Services (FlowProcesses)
    private RepositoryManager repositoryManager;
    private NodeController nodeController;

    /**
     * @param settingsData
     * @param registryInterface
     * @param systemAccess
     */
    public SystemRuntime(
        ContextPath sessionPath,
        SettingsData settingsData,
        ProcessRegistryInterface registryInterface,
        RuntimeAccess systemAccess
    ) {
     
        if (settingsData == null) {
            throw new IllegalArgumentException("SettingsData cannot be null");
        }
        if (registryInterface == null) {
            throw new IllegalArgumentException("ProcessRegistryInterface cannot be null");
        }
        
        this.noteFileService = new NoteFileService(settingsData);
        this.registryInterface = registryInterface;
        if(systemAccess != null){
            grantSystemAccess(systemAccess);
        }
        
    }

     /**
     * Grant system access by filling with closures
     * 
     * Each closure captures private fields (settingsData, noteFileService)
     * SystemSessionProcess can call these WITHOUT AppData exposing getters
     */
    /**
     * Grant system access by filling with closures
     * 
     * Each closure captures private fields (settingsData, noteFileService)
     * SystemSessionProcess can call these WITHOUT AppData exposing getters
     */
    private void grantSystemAccess(RuntimeAccess access) {
        
        // Password change - coordinates SettingsData + NoteFileService
        access.setPasswordChanger((currentPassword, newPassword, batchSize, progressWriter) -> {
            // Closure captures this.settingsData and this.noteFileService
            return noteFileService.updateFilePathLedgerEncryption(
                progressWriter,
                currentPassword,
                newPassword,
                batchSize
            );
        });
        
        // Password verification
        access.setPasswordVerifier(password -> {
            return noteFileService.getSettingsData().verifyPassword(password);
        });
        
        access.setOldPasswordVerifier(oldPassword -> {
            return noteFileService.getSettingsData().verifyOldPassword(oldPassword);
        });
        
        // Recovery operations
        access.setInvestigator(() -> {
            return noteFileService.investigateFileEncryptionState();
        });
        
        access.setRecoveryPerformer((analysis, progressWriter, batchSize) -> {
            SecretKey currentKey = noteFileService.getSettingsData().getSecretKey();
            SecretKey oldKey = noteFileService.getSettingsData().getOldKey();
            
            return noteFileService.reEncryptFilesAdaptive(
                analysis.getFilesNeedingUpdate(),
                oldKey,           // Decrypt with old
                currentKey,       // Encrypt with current
                "RECOVERY",
                batchSize,
                progressWriter,
                analysis          // Track completion
            );
        });
        
        access.setRollbackPerformer((analysis, progressWriter, batchSize) -> {
            // First rollback settings (swap keys)
            return CompletableFuture.runAsync(() -> {
                try {
                    noteFileService.getSettingsData().rollbackToOldPassword();
                } catch (Exception e) {
                    throw new RuntimeException("Settings rollback failed", e);
                }
            })
            .thenCompose(v -> {
                // Now keys are swapped: current=old, old=current
                SecretKey nowCurrent = noteFileService.getSettingsData().getSecretKey(); // Was old
                SecretKey nowOld = noteFileService.getSettingsData().getOldKey();        // Was current
                
                // Re-encrypt files that were updated back to "old" (now current) key
                return noteFileService.reEncryptFilesAdaptive(
                    analysis.getAllFilesNeedingUpdate(), // All files that were changed
                    nowOld,        // Decrypt with new key (was current)
                    nowCurrent,    // Encrypt with old key (now current)
                    "ROLLBACK",
                    batchSize,
                    progressWriter,
                    analysis
                );
            });
        });
        
        access.setComprehensiveRecoveryPerformer((analysis, progressWriter, batchSize) -> {
            SecretKey currentKey = noteFileService.getSettingsData().getSecretKey();
            SecretKey oldKey = noteFileService.getSettingsData().getOldKey();
            
            // Step 1: Re-encrypt files needing update
            return noteFileService.reEncryptFilesAdaptive(
                analysis.getFilesNeedingUpdate(),
                oldKey,
                currentKey,
                "COMPREHENSIVE_REENCRYPT",
                batchSize,
                progressWriter,
                analysis
            )
            .thenCompose(reencryptSuccess -> {
                // Step 2: Finish swaps
                return noteFileService.performFinishSwaps(
                    analysis.getFilesNeedingSwap()
                );
            })
            .thenCompose(swapSuccess -> {
                // Step 3: Cleanup tmp files
                return noteFileService.cleanupFiles(
                    analysis.getFilesNeedingCleanup()
                );
            });
        });
        
        access.setSwapPerformer((analysis, progressWriter) -> {
            return noteFileService.performFinishSwaps(
                analysis.getFilesNeedingSwap()
            );
        });
        
        access.setCleanupPerformer(analysis -> {
            return noteFileService.cleanupFiles(
                analysis.getFilesNeedingCleanup()
            );
        });
        
        // Settings operations
        access.setSettingsRollback(() -> {
            return CompletableFuture.runAsync(() -> {
                try {
                    noteFileService.getSettingsData().rollbackToOldPassword();
                } catch (Exception e) {
                    throw new RuntimeException("Settings rollback failed", e);
                }
            });
        });
        
        access.setOldKeyChecker(() -> {
            return noteFileService.getSettingsData().hasOldKey();
        });
        
        access.setOldKeyClearer(() -> {
            noteFileService.getSettingsData().clearOldKey();
        });
        
        // File service operations
        access.setDiskSpaceValidator(() -> {
            return noteFileService.validateDiskSpaceForReEncryption();
        });
        
        access.setCorruptedFilesDeleter(files -> {
            return noteFileService.deleteCorruptedFiles(files);
        });
        
        access.setFileCounter(() -> {
            return noteFileService.getFileCount();
        });

        // ═══════════════════════════════════════════════════════════════════════
        // NODE MANAGEMENT OPERATIONS
        // ═══════════════════════════════════════════════════════════════════════

        // Package browsing - delegates to RepositoryManager
        access.setPackageBrowser(() -> {
            return repositoryManager.updateAllRepositories();
        });

        // Package installation - delegates to NodeController
        access.setPackageInstaller(request -> {
            return nodeController.installPackage(request);
        });

        // Get installed packages - delegates to NodeController's registry
        access.setInstalledPackagesProvider(() -> {
            return CompletableFuture.completedFuture(
                nodeController.getInstallationRegistry().getInstalledPackages()
            );
        });

        // Uninstall package - delegates to NodeController
        access.setPackageUninstaller((packageId, progress) -> {
            return nodeController.uninstallPackage(packageId, progress);
        });

        // Load node - delegates to NodeController
        access.setNodeLoader(request -> {
            return nodeController.loadNode(request);
        });

        // Unload node - delegates to NodeController
        access.setNodeUnloader(instanceId -> {
            return nodeController.unloadInstance(instanceId);
        });

        // Get running instances - delegates to NodeController
        access.setRunningInstancesProvider(() -> {
            return CompletableFuture.completedFuture(
                nodeController.getAllInstances()
            );
        });

        // Get installation registry - provides direct access for complex operations
        access.setInstallationRegistryProvider(() -> {
            return nodeController.getInstallationRegistry();
        });

        // ═══════════════════════════════════════════════════════════════════════
        // ADDITIONAL NODE OPERATIONS (for enhanced screens)
        // ═══════════════════════════════════════════════════════════════════════

        // Get instances by package
        access.setInstancesByPackageProvider(packageId -> {
            return CompletableFuture.completedFuture(
                nodeController.getInstancesByPackage(packageId)
            );
        });

        // Get instances by process
        access.setInstancesByProcessProvider(processId -> {
            return CompletableFuture.completedFuture(
                nodeController.getInstancesByProcess(processId)
            );
        });

        // Enhanced uninstall with data deletion option
        access.setPackageUninstallerWithData((packageId, deleteData, password, progress) -> {
            // First verify password (redundant check for safety)
            return noteFileService.getSettingsData().verifyPassword(password)
                .thenCompose(valid -> {
                    if (!valid) {
                        return CompletableFuture.failedFuture(
                            new SecurityException("Invalid password"));
                    }
                    
                    // Uninstall package
                    return nodeController.uninstallPackage(packageId, progress)
                        .thenCompose(v -> {
                            // If deleteData is true, also delete data directory
                            if (deleteData) {
                                return nodeController.deletePackageData(packageId, progress);
                            }
                            return CompletableFuture.completedFuture(null);
                        });
                });
        });

        // Package configuration update
        access.setPackageConfigUpdater((packageId, newProcessConfig, password) -> {
            // Verify password first
            return noteFileService.getSettingsData().verifyPassword(password)
                .thenCompose(valid -> {
                    if (!valid) {
                        return CompletableFuture.failedFuture(
                            new SecurityException("Invalid password"));
                    }
                    
                    // Delegate to NodeController
                    return nodeController.updatePackageConfiguration(packageId, newProcessConfig);
                });
        });
    }

    /**
     * Initialize system services
     * 
     * Spawns child processes:
     * - InstallationRegistry
     * - RepositoryManager
     * - NodeController
     */
    public CompletableFuture<Void> initialize() {
        System.out.println("[SystemRuntime] Initializing services...");
        
        return initializeRepositoryManager()
            .thenCompose(v -> initializeNodeController())
            .thenRun(() -> {
                System.out.println("[SystemRuntime] All services initialized");
            });
    }

    

    private CompletableFuture<Void> initializeRepositoryManager() {
       
        // Create child interface scoped to repositories subtree
        ContextPath repoPath = ContextPath.of(CoreConstants.REPOSITORIES);

        ContextPath repoFilePath = repoPath.append("repository-list");
        return noteFileService.getNoteFile(repoFilePath.getSegments())
            .thenCompose(repoFile -> {
               this.repositoryManager = new RepositoryManager(CoreConstants.REPOSITORIES, repoFile);
                ContextPath path = registryInterface.registerProcess(
                    repositoryManager, 
                    CoreConstants.REPOSITORIES_PATH,
                    CoreConstants.SYSTEM_PATH,
                    registryInterface
                );
                System.out.println("[SystemRuntime] Registered RepositoryManager at: " + path);
                return registryInterface.startProcess(path)
                    .thenCompose(v -> repositoryManager.initialize());
            });
    }

    private CompletableFuture<Void> initializeNodeController() {



        NoteFileServiceInterface noteFileServiceInterface = new NoteFileServiceInterface(){

            @Override
            public CompletableFuture<NoteFile> getNoteFile(ContextPath path) {
                return SystemRuntime.this.noteFileService.getNoteFile(path);
            }

            @Override
            public ContextPath getDataRootPath() {
                return CoreConstants.NODE_DATA_PATH;
            }

            @Override
            public CompletableFuture<Void> deleteNoteFile(ContextPath path, boolean recurrsive, AsyncNoteBytesWriter progress){
                return SystemRuntime.this.noteFileService.deleteNoteFilePath(path, false, progress);
            }

        };

       

        // Create controller
        this.nodeController = new NodeController(
            CoreConstants.NODE_CONTROLLER,
            noteFileServiceInterface
        );
        ContextPath path = registryInterface.registerProcess(
                    nodeController, 
                    CoreConstants.NODE_CONTROLLER_PATH,
                    CoreConstants.SYSTEM_PATH,
                    registryInterface
                );

        System.out.println("[SystemRuntime] Registered NodeController at: " + path);


        return registryInterface.startProcess(path);
           
    }

    /**
     * Shutdown - stop all services
     */
    public CompletableFuture<Void> shutdown() {
        System.out.println("[SystemRuntime] Shutting down services");
        
        return nodeController.shutdown()
            .thenCompose(v -> repositoryManager.shutdown())
            .thenCompose(v -> noteFileService.shutdown(null))
            .thenRun(() -> {
                System.out.println("[SystemRuntime] Shutdown complete");
            });
    }

  
}
