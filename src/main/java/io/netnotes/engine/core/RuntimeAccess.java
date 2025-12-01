package io.netnotes.engine.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.DiskSpaceValidation;
import io.netnotes.engine.noteFiles.notePath.NoteFileService.FileEncryptionAnalysis;


/**
 * AppDataSystemAccess - Capability container filled by AppData
 * 
 * Pattern:
 * 1. SystemSessionProcess creates: new AppDataSystemAccess()
 * 2. Passes to AppData: new AppData(settingsData, processService, systemAccess)
 * 3. AppData fills with closures over private fields
 * 4. SystemSessionProcess uses: systemAccess.changePassword(...)
 * 
 * Result: Capabilities WITHOUT AppData exposing getters
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
    
    // === PACKAGE-PRIVATE SETTERS (called by AppData.grantSystemAccess) ===
    
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
    
    // === PUBLIC GETTERS (called by SystemSessionProcess) ===
    
    public CompletableFuture<Boolean> changePassword(
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
    
    public CompletableFuture<Boolean> verifyPassword(NoteBytesEphemeral password) {
        if (passwordVerifier == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Password verifier capability not granted"));
        }
        return passwordVerifier.verify(password);
    }
    
    public CompletableFuture<Boolean> verifyOldPassword(NoteBytesEphemeral oldPassword) {
        if (oldPasswordVerifier == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Old password verifier capability not granted"));
        }
        return oldPasswordVerifier.verify(oldPassword);
    }
    
    public CompletableFuture<FileEncryptionAnalysis> investigateFileEncryption() {
        if (investigator == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Investigator capability not granted"));
        }
        return investigator.investigate();
    }
    
    public CompletableFuture<Boolean> performRecovery(
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
    
    public CompletableFuture<Boolean> performRollback(
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
    
    public CompletableFuture<Boolean> performComprehensiveRecovery(
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
    
    public CompletableFuture<Boolean> performSwap(
        FileEncryptionAnalysis analysis,
        AsyncNoteBytesWriter progressWriter
    ) {
        if (swapPerformer == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Swap performer capability not granted"));
        }
        return swapPerformer.perform(analysis, progressWriter);
    }
    
    public CompletableFuture<Boolean> performTempFileCleanup(FileEncryptionAnalysis analysis) {
        if (cleanupPerformer == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cleanup performer capability not granted"));
        }
        return cleanupPerformer.cleanup(analysis);
    }
    
    public CompletableFuture<Void> rollbackSettingsData() {
        if (settingsRollback == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Settings rollback capability not granted"));
        }
        return settingsRollback.rollback();
    }
    
    public boolean hasOldKeyForRecovery() {
        if (oldKeyChecker == null) {
            throw new IllegalStateException("Old key checker capability not granted");
        }
        return oldKeyChecker.hasOldKey();
    }
    
    public void clearOldKey() {
        if (oldKeyClearer == null) {
            throw new IllegalStateException("Old key clearer capability not granted");
        }
        oldKeyClearer.clear();
    }
    
    public CompletableFuture<DiskSpaceValidation> validateDiskSpaceForReEncryption() {
        if (diskSpaceValidator == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Disk space validator capability not granted"));
        }
        return diskSpaceValidator.validate();
    }
    
    public CompletableFuture<Boolean> deleteCorruptedFiles(List<String> files) {
        if (corruptedFilesDeleter == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Corrupted files deleter capability not granted"));
        }
        return corruptedFilesDeleter.delete(files);
    }
    
    public CompletableFuture<Integer> getFileCount() {
        if (fileCounter == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("File counter capability not granted"));
        }
        return fileCounter.count();
    }
    
    // === FUNCTIONAL INTERFACES ===
    
    @FunctionalInterface
    public interface PasswordChanger {
        CompletableFuture<Boolean> change(
            NoteBytesEphemeral current,
            NoteBytesEphemeral newPassword,
            int batchSize,
            AsyncNoteBytesWriter progressWriter
        );
    }
    
    @FunctionalInterface
    public interface PasswordVerifier {
        CompletableFuture<Boolean> verify(NoteBytesEphemeral password);
    }
    
    @FunctionalInterface
    public interface InvestigationProvider {
        CompletableFuture<FileEncryptionAnalysis> investigate();
    }
    
    @FunctionalInterface
    public interface RecoveryPerformer {
        CompletableFuture<Boolean> perform(
            FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter,
            int batchSize
        );
    }
    
    @FunctionalInterface
    public interface RollbackPerformer {
        CompletableFuture<Boolean> perform(
            FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter,
            int batchSize
        );
    }
    
    @FunctionalInterface
    public interface ComprehensiveRecoveryPerformer {
        CompletableFuture<Boolean> perform(
            FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter,
            int batchSize
        );
    }
    
    @FunctionalInterface
    public interface SwapPerformer {
        CompletableFuture<Boolean> perform(
            FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter
        );
    }
    
    @FunctionalInterface
    public interface CleanupPerformer {
        CompletableFuture<Boolean> cleanup(FileEncryptionAnalysis analysis);
    }
    
    @FunctionalInterface
    public interface SettingsRollback {
        CompletableFuture<Void> rollback();
    }
    
    @FunctionalInterface
    public interface OldKeyChecker {
        boolean hasOldKey();
    }
    
    @FunctionalInterface
    public interface OldKeyClearer {
        void clear();
    }
    
    @FunctionalInterface
    public interface DiskSpaceValidator {
        CompletableFuture<DiskSpaceValidation> validate();
    }
    
    @FunctionalInterface
    public interface CorruptedFilesDeleter {
        CompletableFuture<Boolean> delete(List<String> files);
    }
    
    @FunctionalInterface
    public interface FileCounter {
        CompletableFuture<Integer> count();
    }
}