package io.netnotes.engine.core.system;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.crypto.SecretKey;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.terminal.input.TerminalInputReader;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.TimeHelpers;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * FailedSettingsScreen - Handle settings file corruption or absence
 * 
 * Scenarios:
 * 1. Settings file exists but is corrupt → Attempt recovery
 * 2. Settings file missing but data directory exists → Warn user about data loss
 * 3. Unrecoverable corruption → Offer fresh start
 * 
 * Recovery Strategy:
 * - Try to extract salt keys (critical for decryption)
 * - If salt recovered, can recreate bcrypt with new password using same salt
 * - If salt lost, data is unrecoverable → must delete and start fresh
 */
class FailedSettingsScreen extends TerminalScreen {
    
    private enum DiagnosisState {
        CHECKING,
        CORRUPT_FILE,
        MISSING_FILE_WITH_DATA,
        ATTEMPTING_RECOVERY,
        RECOVERY_SUCCESS,
        RECOVERY_FAILED,
        UNRECOVERABLE
    }
    
    private DiagnosisState currentState = DiagnosisState.CHECKING;
    private String errorDetails = "";
    private Map<NoteBytes, RecoveredData> recoveredData = new HashMap<>();
    private PasswordReader passwordReader;
    private MenuNavigator menuNavigator;
    private TerminalInputReader inputReader;
    
    private static class RecoveredData {
        NoteBytes value;
        long position;
        int declaredLength;
        int actualLength;
        boolean lengthMismatch;
        
        RecoveredData(NoteBytes value, long position, int declaredLength) {
            this.value = value;
            this.position = position;
            this.declaredLength = declaredLength;
            this.actualLength = value.byteLength();
            this.lengthMismatch = (declaredLength != actualLength);
        }
    }
    
    public FailedSettingsScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentState = DiagnosisState.CHECKING;
        return render().thenRun(this::startDiagnosis);
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    @Override
    public CompletableFuture<Void> render() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Settings Error"))
            .thenCompose(v -> {
                switch (currentState) {
                    case CHECKING:
                        return terminal.printAt(5, 10, "Diagnosing settings issue...");
                    
                    case CORRUPT_FILE:
                        return showCorruptFileScreen();
                    
                    case MISSING_FILE_WITH_DATA:
                        return showMissingFileScreen();
                    
                    case ATTEMPTING_RECOVERY:
                        return terminal.printAt(5, 10, "Attempting to recover settings data...");
                    
                    case RECOVERY_SUCCESS:
                        return showRecoverySuccessScreen();
                    
                    case RECOVERY_FAILED:
                        return showRecoveryFailedScreen();
                    
                    case UNRECOVERABLE:
                        return showUnrecoverableScreen();
                    
                    default:
                        return CompletableFuture.completedFuture(null);
                }
            });
    }
    
    private void startDiagnosis() {
        boolean settingsExists = SettingsData.isSettingsData();
        boolean dataExists = SettingsData.isIdDataFile();
        
        if (settingsExists) {
            // Settings file exists but had error - try to diagnose
            diagnoseCorruptFile();
        } else if (dataExists) {
            // No settings but data exists - unrecoverable without salt
            currentState = DiagnosisState.MISSING_FILE_WITH_DATA;
            render();
        } else {
            // No settings, no data - safe to start fresh
            terminal.printAt(7, 10, "No existing data found. Redirecting to setup...")
                .thenRunAsync(() -> TimeHelpers.timeDelay(2))
                .thenRun(() -> terminal.showScreen("first-run-password"));
        }
    }
    
    private void diagnoseCorruptFile() {
        terminal.printAt(7, 10, "Analyzing settings file...")
            .thenCompose(v -> SettingsData.loadSettingsMap())
            .thenAccept(map -> {
                // File loaded successfully - shouldn't be here
                terminal.printAt(9, 10, "Settings file is valid. Retrying authentication...")
                    .thenRunAsync(() -> TimeHelpers.timeDelay(2))
                    .thenRun(() -> {
                        terminal.getState().removeState(SystemTerminalContainer.FAILED_SETTINGS);
                        terminal.getState().addState(SystemTerminalContainer.AUTHENTICATING);
                    });
            })
            .exceptionally(ex -> {
                // File is corrupt - attempt recovery
                errorDetails = ex.getMessage();
                currentState = DiagnosisState.CORRUPT_FILE;
                render();
                return null;
            });
    }
    
    private CompletableFuture<Void> showCorruptFileScreen() {
        return terminal.printAt(5, 10, "Settings file is corrupted")
            .thenCompose(v -> terminal.printAt(7, 10, "Error: " + truncateError(errorDetails)))
            .thenCompose(v -> terminal.printAt(9, 10, "Choose an option below:"))
            .thenRun(() -> showCorruptFileMenu());
    }
    
    private void showCorruptFileMenu() {
        menuNavigator = new MenuNavigator(terminal);
        
        ContextPath menuPath = terminal.getSessionPath().append("menu", "corrupt-file");
        MenuContext menu = new MenuContext(menuPath, "Recovery Options");
        
        menu.addItem("recover", "Attempt Recovery", 
            "Try to extract encryption keys from corrupted file",
            () -> {
                cleanupMenuNavigator();
                currentState = DiagnosisState.ATTEMPTING_RECOVERY;
                render().thenRun(this::attemptRecovery);
            });
        
        menu.addItem("delete", "Delete Data and Start Fresh",
            "⚠ Permanently delete all data and begin setup",
            () -> {
                cleanupMenuNavigator();
                confirmDeleteAndStartFresh();
            });
        
        menu.addItem("exit", "Exit Application",
            "Close application (to restore backup manually)",
            () -> System.exit(0));
        
     
        menuNavigator.showMenu(menu);
    }
    
    private void attemptRecovery() {
        CompletableFuture.runAsync(() -> {
            try {
                recoverSettingsFile();
            } catch (Exception e) {
                errorDetails = e.getMessage();
                currentState = DiagnosisState.RECOVERY_FAILED;
                render();
            }
        });
    }
    
    private void recoverSettingsFile() throws IOException {
        File settingsFile = SettingsData.getSettingsFile();
        
        // Create search patterns for each key
        byte[] bcryptKeyPattern = createKeyPattern(SettingsData.BCRYPT_KEY);
        byte[] saltKeyPattern = createKeyPattern(SettingsData.SALT_KEY);
        byte[] oldBcryptKeyPattern = createKeyPattern(SettingsData.OLD_BCRYPT_KEY);
        byte[] oldSaltKeyPattern = createKeyPattern(SettingsData.OLD_SALT_KEY);
        
        Map<NoteBytes, Long> keyPositions = new HashMap<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(settingsFile, "r")) {
            byte[] buffer = new byte[(int) raf.length()];
            raf.readFully(buffer);
            
            // Search for each key pattern
            long bcryptPos = searchPattern(buffer, bcryptKeyPattern);
            long saltPos = searchPattern(buffer, saltKeyPattern);
            long oldBcryptPos = searchPattern(buffer, oldBcryptKeyPattern);
            long oldSaltPos = searchPattern(buffer, oldSaltKeyPattern);
            
            if (bcryptPos >= 0) keyPositions.put(SettingsData.BCRYPT_KEY, bcryptPos);
            if (saltPos >= 0) keyPositions.put(SettingsData.SALT_KEY, saltPos);
            if (oldBcryptPos >= 0) keyPositions.put(SettingsData.OLD_BCRYPT_KEY, oldBcryptPos);
            if (oldSaltPos >= 0) keyPositions.put(SettingsData.OLD_SALT_KEY, oldSaltPos);
            
            // Extract values for each found key
            for (Map.Entry<NoteBytes, Long> entry : keyPositions.entrySet()) {
                try {
                    RecoveredData data = extractValue(buffer, entry.getValue(), keyPositions);
                    recoveredData.put(entry.getKey(), data);
                } catch (Exception e) {
                    Log.logError("[Recovery] Failed to extract " + entry.getKey() + ": " + e.getMessage());
                }
            }
        }
        
        // Check if we recovered critical data (at least SALT_KEY)
        if (recoveredData.containsKey(SettingsData.SALT_KEY)) {
            currentState = DiagnosisState.RECOVERY_SUCCESS;
        } else {
            currentState = DiagnosisState.RECOVERY_FAILED;
        }
        
        render();
    }
    
    private byte[] createKeyPattern(NoteBytes key) {
        int keyBytesLength = key.byteLength() + NoteBytesMetaData.STANDARD_META_DATA_SIZE;
        byte[] keyBytes = new byte[keyBytesLength];
        NoteBytes.writeNote(key, keyBytes, 0);
        return keyBytes;
    }
    
    private long searchPattern(byte[] data, byte[] pattern) {
        for (int i = 0; i <= data.length - pattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }
    
    private RecoveredData extractValue(byte[] buffer, long keyPosition, Map<NoteBytes, Long> allPositions) throws IOException {
        // Skip past the key (type + length + key_content)
        int offset = (int) keyPosition;
        offset++; // Skip type byte
        
        // Read key length
        int keyLength = bytesToIntBigEndian(buffer, offset);
        offset += 4;
        
        // Verify key length matches actual key content
        int keyEndOffset = offset + keyLength;
        if (keyEndOffset > buffer.length) {
            throw new IOException("Key extends beyond buffer");
        }
        
        offset += keyLength; // Skip key content
        
        // Now we're at the value
        if (offset >= buffer.length) {
            throw new IOException("Value position beyond buffer");
        }
        
        byte valueType = buffer[offset++];
        int declaredLength = bytesToIntBigEndian(buffer, offset);
        offset += 4;
        
        // Find next key position to determine actual available space
        long nextKeyPos = buffer.length;
        for (Long pos : allPositions.values()) {
            if (pos > keyPosition && pos < nextKeyPos) {
                nextKeyPos = pos;
            }
        }
        
        // Calculate actual space available
        int actualLength = (int) (nextKeyPos - offset);
        
        // Check for metadata mismatch
        if (declaredLength != actualLength) {
            Log.logError("[Recovery] Length mismatch detected: declared=" + 
                declaredLength + ", actual=" + actualLength);
        }
        
        // Validate we're not reading into another key's space
        if (offset + declaredLength > nextKeyPos) {
            throw new IOException("Value would overlap with next key");
        }
        
        int lengthToRead = Math.min(declaredLength, actualLength);
        
        if (offset + lengthToRead > buffer.length) {
            throw new IOException("Value extends beyond buffer");
        }
        
        byte[] valueBytes = new byte[lengthToRead];
        System.arraycopy(buffer, offset, valueBytes, 0, lengthToRead);
        
        NoteBytes value = NoteBytes.of(valueBytes, valueType);
        return new RecoveredData(value, keyPosition, declaredLength);
    }
    
    private int bytesToIntBigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
               ((bytes[offset + 1] & 0xFF) << 16) |
               ((bytes[offset + 2] & 0xFF) << 8) |
               (bytes[offset + 3] & 0xFF);
    }
    
    private CompletableFuture<Void> showRecoverySuccessScreen() {
        boolean hasLengthMismatch = recoveredData.values().stream()
            .anyMatch(d -> d.lengthMismatch);
        
        return terminal.printAt(5, 10, "Recovery Status: Partial Success")
            .thenCompose(v -> terminal.printAt(7, 10, "Recovered data:"))
            .thenCompose(v -> {
                int row = 9;
                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                
                for (Map.Entry<NoteBytes, RecoveredData> entry : recoveredData.entrySet()) {
                    String keyName = getKeyName(entry.getKey());
                    RecoveredData data = entry.getValue();
                    String status = data.lengthMismatch ? " (length mismatch)" : " ✓";
                    
                    final int currentRow = row++;
                    chain = chain.thenCompose(x -> 
                        terminal.printAt(currentRow, 12, "• " + keyName + status));
                }
                
                return chain;
            })
            .thenCompose(v -> {
                if (hasLengthMismatch) {
                    return terminal.printAt(11, 10, "⚠ Warning: File appears manually edited");
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenCompose(v -> {
                if (recoveredData.containsKey(SettingsData.SALT_KEY)) {
                    return terminal.printAt(13, 10, "Salt recovered! You can recreate your password.")
                        .thenCompose(x -> terminal.printAt(14, 10, "Using the same salt preserves data access."))
                        .thenCompose(x -> terminal.printAt(16, 10, "Enter new password to recover system:"))
                        .thenCompose(x -> terminal.moveCursor(16, 45))
                        .thenRun(this::startPasswordRecovery);
                } else {
                    return terminal.printAt(13, 10, "⚠ Critical: Salt not recovered")
                        .thenCompose(x -> terminal.printAt(14, 10, "Cannot decrypt existing data."))
                        .thenCompose(x -> terminal.printAt(16, 10, "Press any key to see options..."))
                        .thenRun(() -> terminal.waitForKeyPress(() -> {
                            currentState = DiagnosisState.UNRECOVERABLE;
                            render();
                        }));
                }
            });
    }
    
    private void startPasswordRecovery() {
        passwordReader = new PasswordReader(terminal.getPasswordEventHandlerRegistry());

        passwordReader.setOnPassword(password -> {
            passwordReader.close();
            passwordReader = null;
            
            performRecovery(password);
        });
    }
    
    private void performRecovery(NoteBytesEphemeral password) {
        NoteBytes salt;
        NoteBytes bcrypt;
        NoteBytes oldSalt;
        NoteBytes oldBcrypt;

        try {
            salt = recoveredData.get(SettingsData.SALT_KEY).value;
            bcrypt = recoveredData.containsKey(SettingsData.BCRYPT_KEY)
                ? recoveredData.get(SettingsData.BCRYPT_KEY).value
                : null;
            oldSalt = recoveredData.containsKey(SettingsData.OLD_SALT_KEY)
                ? recoveredData.get(SettingsData.OLD_SALT_KEY).value
                : null;
            oldBcrypt = recoveredData.containsKey(SettingsData.OLD_BCRYPT_KEY)
                ? recoveredData.get(SettingsData.OLD_BCRYPT_KEY).value
                : null;
        } catch (Exception e) {
            password.close();
            terminal.printError("Recovery failed: " + e.getMessage())
                .thenCompose(x -> terminal.printAt(20, 10, "Press any key..."))
                .thenRun(() -> terminal.waitForKeyPress( () -> render()));
            return;
        }

        CompletableFuture<SettingsData> rebuildFuture = CompletableFuture.supplyAsync(() -> {
            // bcrypt logic
           

            try(NoteBytesEphemeral passwordCopy = password.copy()){
                NoteBytes newBcrypt = (bcrypt != null)
                    ? (HashServices.verifyBCryptPassword(password, bcrypt)
                        ? bcrypt
                        : throwInvalidPassword())
                    : HashServices.getBcryptHash(password);
                // Secret key
                SecretKey secretKey = CryptoService.createKey(password, salt);

                // SettingsData
                SettingsData sd;
                if (oldSalt != null && oldBcrypt != null)
                    sd = new SettingsData(secretKey, salt, newBcrypt, oldSalt, oldBcrypt);
                else if (oldSalt != null)
                    sd = new SettingsData(secretKey, salt, newBcrypt, oldSalt);
                else
                    sd = new SettingsData(secretKey, salt, newBcrypt);

                sd.save();
                return sd;
            }catch(Exception e){
                throw new CompletionException(e);
            }finally{
                password.close();
            }
        });

        terminal.printAt(18, 10, "Recreating settings with recovered salt...")
            .thenCompose(v -> rebuildFuture)
            .thenCompose(sd -> {
                cleanup();
                return terminal.clear() 
                    .thenCompose(v-> terminal.printSuccess("Recovery successful!"))
                    .thenCompose(x -> terminal.printAt(20, 10, "The system is now accessible."))
                    .thenCompose(x -> terminal.printAt(21, 10, "Press any key to continue..."))
                    .thenRun(() -> terminal.waitForKeyPress( ()->terminal.recoverSystem(sd)));
             
            })
            .exceptionallyCompose(ex -> {
                password.close();
                Throwable root = unwrapCompletionException(ex);

                return terminal.printError("Recovery failed: " + root.getMessage())
                    .thenCompose(x -> terminal.printAt(20, 10, "Press any key..."))
                    .thenRun(() -> terminal.waitForKeyPress( () -> render()));
            });
      
    }

    private Throwable unwrapCompletionException(Throwable ex) {
        return (ex instanceof CompletionException && ex.getCause() != null)
            ? ex.getCause()
            : ex;
    }

    private NoteBytes throwInvalidPassword() {
         throw new CompletionException(new SettingsData.InvalidPasswordException("Password does not match previous password"));
    }
    
    private CompletableFuture<Void> showRecoveryFailedScreen() {
        return terminal.printAt(5, 10, "Recovery Failed")
            .thenCompose(v -> terminal.printAt(7, 10, "Could not recover critical data from settings file."))
            .thenCompose(v -> terminal.printAt(8, 10, "Error: " + truncateError(errorDetails)))
            .thenCompose(v -> terminal.printAt(10, 10, "Recovered keys:"))
            .thenCompose(v -> {
                if (recoveredData.isEmpty()) {
                    return terminal.printAt(11, 12, "• None");
                } else {
                    int row = 11;
                    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                    for (NoteBytes key : recoveredData.keySet()) {
                        final int currentRow = row++;
                        chain = chain.thenCompose(x -> 
                            terminal.printAt(currentRow, 12, "• " + getKeyName(key)));
                    }
                    return chain;
                }
            })
            .thenCompose(v -> terminal.printAt(13, 10, "Choose an option below:"))
            .thenRun(this::showRecoveryFailedMenu);
    }
    
    private void showRecoveryFailedMenu() {
        menuNavigator = new MenuNavigator( terminal );
        
        ContextPath menuPath = terminal.getSessionPath().append("menu", "recovery-failed");
        MenuContext menu = new MenuContext(menuPath, "Recovery Failed");
        
        menu.addItem("delete", "Delete All Data and Start Fresh",
            "⚠ Permanently delete encrypted data",
            () -> {
                cleanupMenuNavigator();
                confirmDeleteAndStartFresh();
            });
        
        menu.addItem("exit", "Exit Application",
            "Close to restore from backup",
            () -> System.exit(0));
        
   
        menuNavigator.showMenu(menu);
    }
    
    private CompletableFuture<Void> showMissingFileScreen() {
        return terminal.printAt(5, 10, "Settings File Missing")
            .thenCompose(v -> terminal.printAt(7, 10, "⚠ WARNING: Existing encrypted data detected!"))
            .thenCompose(v -> terminal.printAt(9, 10, "The settings file containing encryption keys"))
            .thenCompose(v -> terminal.printAt(10, 10, "is missing, but encrypted data exists."))
            .thenCompose(v -> terminal.printAt(12, 10, "Without the original settings file, this data"))
            .thenCompose(v -> terminal.printAt(13, 10, "CANNOT be recovered."))
            .thenCompose(v -> terminal.printAt(15, 10, "If you have a backup of settings.dat, restore it"))
            .thenCompose(v -> terminal.printAt(16, 10, "to the data directory and restart."))
            .thenCompose(v -> terminal.printAt(18, 10, "Choose an option below:"))
            .thenRun(this::showMissingFileMenu);
    }
    
    private void showMissingFileMenu() {
        menuNavigator = new MenuNavigator( terminal);
        
        ContextPath menuPath = terminal.getSessionPath().append("menu", "missing-file");
        MenuContext menu = new MenuContext(menuPath, "Missing Settings");
        
        menu.addItem("delete", "Delete Encrypted Data and Start Fresh",
            "⚠ Lose all encrypted data permanently",
            () -> {
                cleanupMenuNavigator();
                confirmDeleteAndStartFresh();
            });
        
        menu.addItem("exit", "Exit to Restore Backup",
            "Close application to restore settings.dat manually",
            () -> System.exit(0));

        menuNavigator.showMenu(menu);
    }
    
    private CompletableFuture<Void> showUnrecoverableScreen() {
        return terminal.printAt(5, 10, "Unrecoverable Corruption")
            .thenCompose(v -> terminal.printAt(7, 10, "The settings file is corrupted beyond recovery."))
            .thenCompose(v -> terminal.printAt(8, 10, "Critical encryption data cannot be extracted."))
            .thenCompose(v -> terminal.printAt(10, 10, "All encrypted data will be lost."))
            .thenCompose(v -> terminal.printAt(12, 10, "Press any key to delete data and start fresh..."))
            .thenRun(() -> terminal.waitForKeyPress( this::deleteDataAndStartFresh));
    }
    
    private void confirmDeleteAndStartFresh() {
        terminal.clear()
            .thenCompose(v -> terminal.printTitle("Confirm Data Deletion"))
            .thenCompose(v -> terminal.printAt(7, 10, "⚠ WARNING ⚠"))
            .thenCompose(v -> terminal.printAt(9, 10, "This will permanently delete:"))
            .thenCompose(v -> terminal.printAt(10, 12, "• All encrypted data"))
            .thenCompose(v -> terminal.printAt(11, 12, "• All settings"))
            .thenCompose(v -> terminal.printAt(12, 12, "• The entire data directory"))
            .thenCompose(v -> terminal.printAt(14, 10, "This action CANNOT be undone."))
            .thenCompose(v -> terminal.printAt(16, 10, "Type 'DELETE' to confirm: "))
            .thenCompose(v -> terminal.moveCursor(16, 37))
            .thenRun(this::waitForDeleteConfirmation);
    }
    
    private void waitForDeleteConfirmation() {
        inputReader = new TerminalInputReader(terminal, 16, 37, 10);
        
        inputReader.setOnComplete(input -> {
          
            inputReader.close();
            inputReader = null;
            
            if ("DELETE".equals(input)) {
                deleteDataAndStartFresh();
            } else {
                terminal.printAt(18, 10, "Deletion cancelled. Must type 'DELETE' exactly.")
                    .thenRunAsync(() -> TimeHelpers.timeDelay(2))
                    .thenCompose(v -> render());
            }
        });
        
    }
    
    private void deleteDataAndStartFresh() {
        terminal.clear()
            .thenCompose(v -> terminal.printTitle("Deleting Data"))
            .thenCompose(v -> terminal.printAt(7, 10, "Removing data directory..."))
            .thenCompose(v -> CompletableFuture.runAsync(() -> {
                try {
                    File dataDir = SettingsData.getDataDir();
                    deleteDirectory(dataDir);
                    
                    // Recreate empty data directory
                    dataDir.mkdirs();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete data: " + e.getMessage());
                }
            }))
            .thenCompose(v -> terminal.printSuccess("Data deleted successfully"))
            .thenCompose(v -> terminal.printAt(9, 10, "Redirecting to setup..."))
            .thenRunAsync(() -> TimeHelpers.timeDelay(2))
            .thenRun(() -> {
                terminal.getState().removeState(SystemTerminalContainer.FAILED_SETTINGS);
                terminal.getState().addState(SystemTerminalContainer.FIRST_RUN);
            })
            .exceptionally(ex -> {
                terminal.printError("Failed to delete data: " + ex.getMessage())
                    .thenCompose(v -> terminal.printAt(11, 10, "Press any key to exit..."))
                    .thenRun(() -> terminal.waitForKeyPress( () -> System.exit(1)));
                return null;
            });
    }
    
    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        if (!file.delete()) {
                            throw new IOException("Failed to delete: " + file.getAbsolutePath());
                        }
                    }
                }
            }
            if (!directory.delete()) {
                throw new IOException("Failed to delete directory: " + directory.getAbsolutePath());
            }
        }
    }
    
    private String getKeyName(NoteBytes key) {
        if (key.equals(SettingsData.BCRYPT_KEY)) return "BCRYPT_KEY";
        if (key.equals(SettingsData.SALT_KEY)) return "SALT_KEY";
        if (key.equals(SettingsData.OLD_BCRYPT_KEY)) return "OLD_BCRYPT_KEY";
        if (key.equals(SettingsData.OLD_SALT_KEY)) return "OLD_SALT_KEY";
        return "UNKNOWN";
    }
    
    private String truncateError(String error) {
        if (error == null) return "Unknown error";
        return error.length() > 60 ? error.substring(0, 57) + "..." : error;
    }
    
    private void cleanupMenuNavigator() {
        if (menuNavigator != null) {
            menuNavigator.cleanup();
            menuNavigator = null;
        }
    }
    
    private void cleanup() {
        cleanupMenuNavigator();
        
        if (passwordReader != null) {
         
            passwordReader.close();
            passwordReader = null;
        }
        
        if (inputReader != null) {
        
            inputReader.close();
            inputReader = null;
        }
        
        recoveredData.clear();
    }
}