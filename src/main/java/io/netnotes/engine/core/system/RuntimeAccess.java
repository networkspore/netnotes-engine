package io.netnotes.engine.core.system;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.nodes.InstallationRegistry;
import io.netnotes.engine.core.system.control.nodes.InstallationRequest;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.InstanceId;
import io.netnotes.engine.core.system.control.nodes.NodeInstance;
import io.netnotes.engine.core.system.control.nodes.NodeLoadRequest;
import io.netnotes.engine.core.system.control.nodes.PackageId;
import io.netnotes.engine.core.system.control.nodes.PackageInfo;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.DiskSpaceValidation;
import io.netnotes.engine.noteFiles.notePath.NoteFileService.FileEncryptionAnalysis;


/**
 * AppDataSystemAccess - Capability container filled by SystemRuntime
 * 
 * Pattern:
 * 1. SystemSessionProcess creates: new RuntimeAccess()
 * 2. Passes to SystemRuntime: new SystemRuntime(settingsData, processService, systemAccess)
 * 3. SystemRuntime fills with closures over private fields
 * 4. SystemSessionProcess uses: systemAccess.changePassword(...)
 * 
 * Result: Capabilities WITHOUT SystemRuntime exposing getters
 */
public class RuntimeAccess {
    
    // Closures stored as functional interfaces
    private PasswordChanger passwordChanger;
    private PasswordVerifier passwordVerifier;
    private PasswordVerifier oldPasswordVerifier;
    private InvestigationProvider investigator;
    private RecoveryPerformer recoveryPerformer;
    private RollbackPerformer rollbackPerformer;
    private ComprehensiveRecoveryPerformer comprehensiveRecoveryPerformer;
    private SwapPerformer swapPerformer;
    private CleanupPerformer cleanupPerformer;
    private SettingsRollback settingsRollback;
    private OldKeyChecker oldKeyChecker;
    private OldKeyClearer oldKeyClearer;
    private DiskSpaceValidator diskSpaceValidator;
    private CorruptedFilesDeleter corruptedFilesDeleter;
    private FileCounter fileCounter;

    private PackageBrowser packageBrowser;
    private PackageInstaller packageInstaller;
    private InstalledPackagesProvider installedPackagesProvider;
    private PackageUninstaller packageUninstaller;
    private NodeLoader nodeLoader;
    private NodeUnloader nodeUnloader;
    private RunningInstancesProvider runningInstancesProvider;
    private InstallationRegistryProvider installationRegistryProvider;

    private InstancesByPackageProvider instancesByPackageProvider;
    private InstancesByProcessProvider instancesByProcessProvider;
    private PackageUninstallerWithData packageUninstallerWithData;
    private PackageConfigUpdater packageConfigUpdater;

    
    // === PACKAGE-PRIVATE SETTERS (called by SystemRuntime.grantSystemAccess) ===


    void setInstancesByPackageProvider(InstancesByPackageProvider provider) {
        this.instancesByPackageProvider = provider;
    }

    void setInstancesByProcessProvider(InstancesByProcessProvider provider) {
        this.instancesByProcessProvider = provider;
    }

    void setPackageUninstallerWithData(PackageUninstallerWithData uninstaller) {
        this.packageUninstallerWithData = uninstaller;
    }

    void setPackageConfigUpdater(PackageConfigUpdater updater) {
        this.packageConfigUpdater = updater;
    }
    
    void setPasswordChanger(PasswordChanger changer) {
        this.passwordChanger = changer;
    }
    
    void setPasswordVerifier(PasswordVerifier verifier) {
        this.passwordVerifier = verifier;
    }
    
    void setOldPasswordVerifier(PasswordVerifier oldVerifier) {
        this.oldPasswordVerifier = oldVerifier;
    }
    
    void setInvestigator(InvestigationProvider investigator) {
        this.investigator = investigator;
    }
    
    void setRecoveryPerformer(RecoveryPerformer recovery) {
        this.recoveryPerformer = recovery;
    }
    
    void setRollbackPerformer(RollbackPerformer rollback) {
        this.rollbackPerformer = rollback;
    }
    
    void setComprehensiveRecoveryPerformer(ComprehensiveRecoveryPerformer comprehensive) {
        this.comprehensiveRecoveryPerformer = comprehensive;
    }
    
    void setSwapPerformer(SwapPerformer swapper) {
        this.swapPerformer = swapper;
    }
    
    void setCleanupPerformer(CleanupPerformer cleanup) {
        this.cleanupPerformer = cleanup;
    }
    
    void setSettingsRollback(SettingsRollback rollback) {
        this.settingsRollback = rollback;
    }
    
    void setOldKeyChecker(OldKeyChecker checker) {
        this.oldKeyChecker = checker;
    }
    
    void setOldKeyClearer(OldKeyClearer clearer) {
        this.oldKeyClearer = clearer;
    }
    
    void setDiskSpaceValidator(DiskSpaceValidator validator) {
        this.diskSpaceValidator = validator;
    }
    
    void setCorruptedFilesDeleter(CorruptedFilesDeleter deleter) {
        this.corruptedFilesDeleter = deleter;
    }
    
    void setFileCounter(FileCounter counter) {
        this.fileCounter = counter;
    }


    void setPackageBrowser(PackageBrowser browser) {
        this.packageBrowser = browser;
    }

    void setPackageInstaller(PackageInstaller installer) {
        this.packageInstaller = installer;
    }

    void setInstalledPackagesProvider(InstalledPackagesProvider provider) {
        this.installedPackagesProvider = provider;
    }

    void setPackageUninstaller(PackageUninstaller uninstaller) {
        this.packageUninstaller = uninstaller;
    }

    void setNodeLoader(NodeLoader loader) {
        this.nodeLoader = loader;
    }

    void setNodeUnloader(NodeUnloader unloader) {
        this.nodeUnloader = unloader;
    }

    void setRunningInstancesProvider(RunningInstancesProvider provider) {
        this.runningInstancesProvider = provider;
    }

    void setInstallationRegistryProvider(InstallationRegistryProvider provider) {
        this.installationRegistryProvider = provider;
    }

        
    // === PUBLIC GETTERS (called by SystemSessionProcess) ===
    
    CompletableFuture<Boolean> changePassword(
        NoteBytesEphemeral currentPassword,
        NoteBytesEphemeral newPassword,
        int batchSize,
        AsyncNoteBytesWriter progressWriter
    ) {
        if (passwordChanger == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Password changer capability not granted"));
        }
        return passwordChanger.change(currentPassword, newPassword, batchSize, progressWriter);
    }
    
    CompletableFuture<Boolean> verifyPassword(NoteBytesEphemeral password) {
        if (passwordVerifier == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Password verifier capability not granted"));
        }
        return passwordVerifier.verify(password);
    }
    
    CompletableFuture<Boolean> verifyOldPassword(NoteBytesEphemeral oldPassword) {
        if (oldPasswordVerifier == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Old password verifier capability not granted"));
        }
        return oldPasswordVerifier.verify(oldPassword);
    }
    
    CompletableFuture<FileEncryptionAnalysis> investigateFileEncryption() {
        if (investigator == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Investigator capability not granted"));
        }
        return investigator.investigate();
    }
    
    CompletableFuture<Boolean> performRecovery(
        FileEncryptionAnalysis analysis,
        AsyncNoteBytesWriter progressWriter,
        int batchSize
    ) {
        if (recoveryPerformer == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Recovery performer capability not granted"));
        }
        return recoveryPerformer.perform(analysis, progressWriter, batchSize);
    }
    
    CompletableFuture<Boolean> performRollback(
        FileEncryptionAnalysis analysis,
        AsyncNoteBytesWriter progressWriter,
        int batchSize
    ) {
        if (rollbackPerformer == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Rollback performer capability not granted"));
        }
        return rollbackPerformer.perform(analysis, progressWriter, batchSize);
    }
    
    CompletableFuture<Boolean> performComprehensiveRecovery(
        FileEncryptionAnalysis analysis,
        AsyncNoteBytesWriter progressWriter,
        int batchSize
    ) {
        if (comprehensiveRecoveryPerformer == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Comprehensive recovery performer capability not granted"));
        }
        return comprehensiveRecoveryPerformer.perform(analysis, progressWriter, batchSize);
    }
    
    CompletableFuture<Boolean> performSwap(
        FileEncryptionAnalysis analysis,
        AsyncNoteBytesWriter progressWriter
    ) {
        if (swapPerformer == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Swap performer capability not granted"));
        }
        return swapPerformer.perform(analysis, progressWriter);
    }
    
    CompletableFuture<Boolean> performTempFileCleanup(FileEncryptionAnalysis analysis) {
        if (cleanupPerformer == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cleanup performer capability not granted"));
        }
        return cleanupPerformer.cleanup(analysis);
    }
    
    CompletableFuture<Void> rollbackSettingsData() {
        if (settingsRollback == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Settings rollback capability not granted"));
        }
        return settingsRollback.rollback();
    }
    
    boolean hasOldKeyForRecovery() {
        if (oldKeyChecker == null) {
            throw new IllegalStateException("Old key checker capability not granted");
        }
        return oldKeyChecker.hasOldKey();
    }
    
    void clearOldKey() {
        if (oldKeyClearer == null) {
            throw new IllegalStateException("Old key clearer capability not granted");
        }
        oldKeyClearer.clear();
    }
    
    CompletableFuture<DiskSpaceValidation> validateDiskSpaceForReEncryption() {
        if (diskSpaceValidator == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Disk space validator capability not granted"));
        }
        return diskSpaceValidator.validate();
    }
    
    CompletableFuture<Boolean> deleteCorruptedFiles(List<String> files) {
        if (corruptedFilesDeleter == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Corrupted files deleter capability not granted"));
        }
        return corruptedFilesDeleter.delete(files);
    }
    
    CompletableFuture<Integer> getFileCount() {
        if (fileCounter == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("File counter capability not granted"));
        }
        return fileCounter.count();
    }
    
    // === FUNCTIONAL INTERFACES ===
    
    @FunctionalInterface
    interface PasswordChanger {
        CompletableFuture<Boolean> change(
            NoteBytesEphemeral current,
            NoteBytesEphemeral newPassword,
            int batchSize,
            AsyncNoteBytesWriter progressWriter
        );
    }
    
    @FunctionalInterface
    interface PasswordVerifier {
        CompletableFuture<Boolean> verify(NoteBytesEphemeral password);
    }
    
    @FunctionalInterface
    interface InvestigationProvider {
        CompletableFuture<FileEncryptionAnalysis> investigate();
    }
    
    @FunctionalInterface
    interface RecoveryPerformer {
        CompletableFuture<Boolean> perform(
            FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter,
            int batchSize
        );
    }
    
    @FunctionalInterface
    interface RollbackPerformer {
        CompletableFuture<Boolean> perform(
            FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter,
            int batchSize
        );
    }
    
    @FunctionalInterface
    interface ComprehensiveRecoveryPerformer {
        CompletableFuture<Boolean> perform(
            FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter,
            int batchSize
        );
    }
    
    @FunctionalInterface
    interface SwapPerformer {
        CompletableFuture<Boolean> perform(
            FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter
        );
    }
    
    @FunctionalInterface
    interface CleanupPerformer {
        CompletableFuture<Boolean> cleanup(FileEncryptionAnalysis analysis);
    }
    
    @FunctionalInterface
    interface SettingsRollback {
        CompletableFuture<Void> rollback();
    }
    
    @FunctionalInterface
    interface OldKeyChecker {
        boolean hasOldKey();
    }
    
    @FunctionalInterface
    interface OldKeyClearer {
        void clear();
    }
    
    @FunctionalInterface
    interface DiskSpaceValidator {
        CompletableFuture<DiskSpaceValidation> validate();
    }
    
    @FunctionalInterface
    interface CorruptedFilesDeleter {
        CompletableFuture<Boolean> delete(List<String> files);
    }
    
    @FunctionalInterface
    interface FileCounter {
        CompletableFuture<Integer> count();
    }


    /**
     * Browse available packages from repositories
     */
    CompletableFuture<List<PackageInfo>> browseAvailablePackages() {
        if (packageBrowser == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Package browser capability not granted"));
        }
        return packageBrowser.browse();
    }

    /**
     * Install a package (password already verified by caller)
     * 
     * The password in the request was already verified by SystemSessionProcess,
     * so we can proceed with installation directly.
     */
    CompletableFuture<InstalledPackage> installPackage(
        InstallationRequest request
    ) {
        if (packageInstaller == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Package installer capability not granted"));
        }
        return packageInstaller.install(request);
    }

    /**
     * Get list of installed packages
     */
    CompletableFuture<List<InstalledPackage>> getInstalledPackages() {
        if (installedPackagesProvider == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Installed packages provider capability not granted"));
        }
        return installedPackagesProvider.provide();
    }

    /**
     * Uninstall a package
     */
    CompletableFuture<Void> uninstallPackage(PackageId packageId, AsyncNoteBytesWriter progress) {
        if (packageUninstaller == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Package uninstaller capability not granted"));
        }
        return packageUninstaller.uninstall(packageId, progress);
    }

    /**
     * Load a node instance
     */
    CompletableFuture<NodeInstance> loadNode(NodeLoadRequest request) {
        if (nodeLoader == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Node loader capability not granted"));
        }
        return nodeLoader.load(request);
    }

    /**
     * Unload a node instance
     */
    CompletableFuture<Void> unloadNode(InstanceId instanceId) {
        if (nodeUnloader == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Node unloader capability not granted"));
        }
        return nodeUnloader.unload(instanceId);
    }

    /**
     * Get all running node instances
     */
    CompletableFuture<List<NodeInstance>> getRunningInstances() {
        if (runningInstancesProvider == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Running instances provider capability not granted"));
        }
        return runningInstancesProvider.provide();
    }

    /**
     * Get installation registry (needed by InstallationFlowCoordinator)
     */
    InstallationRegistry getInstallationRegistry() {
        if (installationRegistryProvider == null) {
            throw new IllegalStateException(
                "Installation registry provider capability not granted");
        }
        return installationRegistryProvider.provide();
    }


    /**
     * Get instances of a specific package
     */
    public CompletableFuture<List<NodeInstance>> getInstancesByPackage(PackageId packageId) {
        if (instancesByPackageProvider == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Instances by package provider capability not granted"));
        }
        return instancesByPackageProvider.provide(packageId);
    }

    /**
     * Get instances in a specific process namespace
     */
    public CompletableFuture<List<NodeInstance>> getInstancesByProcess(String processId) {
        if (instancesByProcessProvider == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Instances by process provider capability not granted"));
        }
        return instancesByProcessProvider.provide(processId);
    }

    /**
     * Uninstall package with data deletion option (requires password)
     */
    public CompletableFuture<Void> uninstallPackage(
        PackageId packageId,
        boolean deleteData,
        NoteBytesEphemeral password,
        AsyncNoteBytesWriter progress
    ) {
        if (packageUninstallerWithData == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Package uninstaller with data capability not granted"));
        }
        return packageUninstallerWithData.uninstall(packageId, deleteData, password, progress);
    }

    /**
     * Update package configuration (requires password)
     */
    public CompletableFuture<Void> updatePackageConfiguration(
        PackageId packageId,
        io.netnotes.engine.core.system.control.nodes.ProcessConfig newProcessConfig,
        NoteBytesEphemeral password
    ) {
        if (packageConfigUpdater == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Package config updater capability not granted"));
        }
        return packageConfigUpdater.update(packageId, newProcessConfig, password);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ADD THESE FUNCTIONAL INTERFACES (at end of file, with existing ones)
    // ═══════════════════════════════════════════════════════════════════════════

    @FunctionalInterface
    interface PackageBrowser {
        CompletableFuture<List<PackageInfo>> browse();
    }

    @FunctionalInterface
    interface PackageInstaller {
        CompletableFuture<InstalledPackage> install(InstallationRequest request);
    }

    @FunctionalInterface
    interface InstalledPackagesProvider {
        CompletableFuture<List<InstalledPackage>> provide();
    }

    @FunctionalInterface
    interface PackageUninstaller {
        CompletableFuture<Void> uninstall(PackageId packageId, AsyncNoteBytesWriter progresss);
    }

    @FunctionalInterface
    interface NodeLoader {
        CompletableFuture<NodeInstance> load(NodeLoadRequest request);
    }

    @FunctionalInterface
    interface NodeUnloader {
        CompletableFuture<Void> unload(InstanceId instanceId);
    }

    @FunctionalInterface
    interface RunningInstancesProvider {
        CompletableFuture<List<NodeInstance>> provide();
    }

    @FunctionalInterface
    interface InstallationRegistryProvider {
        InstallationRegistry provide();
    }


    @FunctionalInterface
    interface InstancesByPackageProvider {
        CompletableFuture<List<NodeInstance>> provide(PackageId packageId);
    }

    @FunctionalInterface
    interface InstancesByProcessProvider {
        CompletableFuture<List<NodeInstance>> provide(String processId);
    }

    @FunctionalInterface
    interface PackageUninstallerWithData {
        CompletableFuture<Void> uninstall(
            PackageId packageId,
            boolean deleteData,
            NoteBytesEphemeral password,
            AsyncNoteBytesWriter progress
        );
    }

    @FunctionalInterface
    interface PackageConfigUpdater {
        CompletableFuture<Void> update(
            PackageId packageId,
            io.netnotes.engine.core.system.control.nodes.ProcessConfig newProcessConfig,
            NoteBytesEphemeral password
        );
    }
}