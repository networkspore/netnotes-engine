package io.netnotes.engine.core.system;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.StreamReader;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalProgressBar;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.TimeHelpers;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.ExecutorConsumer;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;

/**
 * PasswordChangeScreen - Change master password
 * Three-step process: verify current, enter new, confirm new
 * Displays different progress bars for different operation scopes
 * 
 * Thread Safety: Uses single-threaded executor to serialize all UI updates
 */
class PasswordChangeScreen extends TerminalScreen {
    
    private PasswordReader passwordReader;
    
    private enum Step {
        VERIFY_CURRENT,
        ENTER_NEW,
        CONFIRM_NEW
    }
    
    private Step currentStep = Step.VERIFY_CURRENT;
    private NoteBytesEphemeral currentPassword;
    private NoteBytesEphemeral newPassword;
    
    // Progress tracking
    private Map<String, TerminalProgressBar> progressBars = new HashMap<>();
    private Map<String, Integer> progressBarRows = new HashMap<>();
    private int currentRow = 10;
    
    public PasswordChangeScreen(String name, SystemTerminalContainer terminal, InputDevice keyboard) {
        super(name, terminal, keyboard);
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentStep = Step.VERIFY_CURRENT;
        return render();
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    @Override
    public CompletableFuture<Void> render() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Change Master Password"))
            .thenCompose(v -> {
                switch (currentStep) {
                    case VERIFY_CURRENT:
                        return terminal.printAt(5, 10, "Enter current password:")
                            .thenCompose(v2 -> terminal.moveCursor(5, 33));
                    case ENTER_NEW:
                        return terminal.printAt(5, 10, "Enter new password:")
                            .thenCompose(v2 -> terminal.moveCursor(5, 30));
                    case CONFIRM_NEW:
                        return terminal.printAt(5, 10, "Confirm new password:")
                            .thenCompose(v2 -> terminal.moveCursor(5, 32));
                    default:
                        return CompletableFuture.completedFuture(null);
                }
            })
            .thenRun(this::startPasswordEntry);
    }
    
    private void startPasswordEntry() {
        passwordReader = new PasswordReader();
        keyboard.setEventConsumer(passwordReader.getEventConsumer());
        
        passwordReader.setOnPassword(password -> {
            keyboard.setEventConsumer(null);
            passwordReader.close();
            passwordReader = null;
            
            switch (currentStep) {
                case VERIFY_CURRENT -> handleVerifyCurrent(password);
                case ENTER_NEW -> handleEnterNew(password);
                case CONFIRM_NEW -> handleConfirmNew(password);
            }
        });
    }
    
    private void handleVerifyCurrent(NoteBytesEphemeral password) {
        RuntimeAccess access = terminal.getSystemAccess();
        if (access == null) {
            password.close();
            terminal.clear()
                .thenCompose(v->terminal.printError("System access not available"))
                .thenCompose(v -> terminal.printAt(15, 10, "Press any key..."))
                .thenRun(() -> waitForKeyPress(keyboard, () -> terminal.goBack()));
            return;
        }
        
        terminal.printAt(7, 10, "Verifying current password...")
            .thenCompose(v -> access.verifyPassword(password))
            .thenAccept(valid -> {
                if (valid) {
                    currentPassword = password.copy();
                    password.close();
                    
                    terminal.printAt(8, 10, "Validating disk space...")
                        .thenCompose(v -> access.validateDiskSpaceForReEncryption())
                        .thenAccept(validation -> {
                            if (validation.isValid()) {
                                String summary = String.format(
                                    "Files to re-encrypt: %d (%.2f MB)",
                                    validation.getNumberOfFiles(),
                                    validation.getTotalFileSizes() / (1024.0 * 1024.0)
                                );
                                terminal.printAt(9, 10, summary)
                                    .thenRun(() -> {
                                        currentStep = Step.ENTER_NEW;
                                        render();
                                    });
                            } else {
                                currentPassword.close();
                                currentPassword = null;
                                showDiskSpaceError(validation);
                            }
                        })
                        .exceptionally(ex -> {
                            currentPassword.close();
                            currentPassword = null;
                            terminal.clear()
                                .thenCompose(v->terminal.printError("Validation failed: " + ex.getMessage()))
                                .thenCompose(v -> terminal.printAt(15, 10, "Press any key..."))
                                .thenRun(() -> waitForKeyPress(keyboard, () -> terminal.goBack()));
                            return null;
                        });
                } else {
                    password.close();
                    terminal.clear()
                        .thenCompose(v->terminal.printError("Invalid password. Please try again."))
                        .thenRunAsync(()->TimeHelpers.timeDelay(2))
                        .thenCompose(v -> render());
                }
            })
            .exceptionally(ex -> {
                password.close();
                terminal.printError("Verification failed: " + ex.getMessage())
                    .thenRun(() -> terminal.goBack());
                return null;
            });
    }
    
    private void handleEnterNew(NoteBytesEphemeral password) {
        newPassword = password.copy();
        password.close();
        currentStep = Step.CONFIRM_NEW;
        render();
    }
    
    private void handleConfirmNew(NoteBytesEphemeral password) {
        boolean match = newPassword.equals(password);
        
        if (match) {
            password.close();
            performPasswordChange();
        } else {
            newPassword.close();
            newPassword = null;
            password.close();
            terminal.clear()
                .thenCompose(v-> terminal.printError("Passwords do not match. Please try again."))
                .thenRunAsync(()->TimeHelpers.timeDelay(2))
                .thenRun(() -> {
                    currentStep = Step.ENTER_NEW;
                    render();
                });
        }
    }

    private StreamReader streamReader;
    private SerializedVirtualExecutor msgExecutor;
    
    /**
     * Performs the password change operation with progress tracking
     * 
     * Thread Architecture:
     * - Main thread: Coordinates the operation
     * - StreamReader thread (from runAsync): Reads progress messages from pipe
     * - msgExecutor (single thread): Processes all UI updates serially
     * 
     * Message Flow:
     * 1. changePassword() writes NoteBytes to AsyncNoteBytesWriter
     * 2. StreamReader thread reads from PipedInputStream
     * 3. emitEvent() calls ExecutorConsumer.accept()
     * 4. ExecutorConsumer submits to msgExecutor
     * 5. msgExecutor runs handleProgressMessage() on single thread
     * 
     * This ensures all terminal operations are serialized, preventing race conditions.
     */
    private void performPasswordChange() {
        RuntimeAccess access = terminal.getSystemAccess();
        
        terminal.clear()
            .thenCompose(v -> terminal.printTitle("Changing Password"))
            .thenCompose(v -> terminal.printAt(5, 10, "Re-encrypting files..."))
            .thenCompose(v -> terminal.printAt(7, 10, "This may take a while. Please wait."))
            .thenCompose(v -> {
                // Reset progress tracking
                progressBars.clear();
                progressBarRows.clear();
                currentRow = 10;
                
                streamReader = new StreamReader("progress-reader");
                int batchSize = 10;
                
                // CRITICAL: Use single-threaded executor for thread-safe UI updates
                // All terminal operations must be serialized to prevent race conditions
                msgExecutor = new SerializedVirtualExecutor();
                
                ExecutorConsumer<NoteBytes> progressConsumer = new ExecutorConsumer<>(
                    msgExecutor,
                    nextNoteBytes -> handleProgressMessage(nextNoteBytes)
                );

                streamReader.addEventConsumer("progressId", progressConsumer);
                streamReader.start();
          
                return access.changePassword(currentPassword, newPassword, batchSize, streamReader.getWriter());
            })
            .thenCompose(success -> {
                // CRITICAL: Wait for all pending messages to be processed
                // Phase 1: StreamReader's future ensures all messages are read
                // Phase 2: msgExecutor drains its queue to complete all UI updates
                return waitForStreamReaderCompletion()
                    .thenApply(v -> success);
            })
            .thenAccept(success -> {
                // Executor is already shutdown by waitForStreamReaderCompletion
                // Just clean up passwords
                cleanupPasswords();
                
                if (success) {
                    terminal.clear()
                        .thenCompose(v -> terminal.printSuccess(
                            "✓ Password changed successfully!\n\n" +
                            "All files have been re-encrypted."))
                        .thenCompose(v -> terminal.printAt(10, 10, 
                            "Press any key to continue..."))
                        .thenRun(() -> waitForKeyPress(keyboard, () -> terminal.goBack()));
                } else {
                    terminal.clear()
                        .thenCompose(v -> terminal.printError(
                            "✗ Password change completed with errors\n\n" +
                            "Some files may not have been re-encrypted."))
                        .thenCompose(v -> terminal.printAt(10, 10, 
                            "Press any key to continue..."))
                        .thenRun(() -> waitForKeyPress(keyboard, () -> terminal.goBack()));
                }
            })
            .exceptionally(ex -> handlePasswordChangeError(ex));
    }
    
    /**
     * Wait for StreamReader to complete all pending message processing
     * This ensures we don't declare success before all progress updates are done
     * 
     * Two-phase completion:
     * 1. Wait for StreamReader's thread to finish reading all messages
     * 2. Wait for msgExecutor to finish processing all submitted UI update tasks
     */
    private CompletableFuture<Void> waitForStreamReaderCompletion() {
        if (streamReader == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Phase 1: Wait for StreamReader to finish reading the stream
        CompletableFuture<Void> streamComplete = streamReader.getStreamFuture();
        
        // Phase 2: After stream reading completes, wait for executor to drain its queue
        return streamComplete.thenCompose(v -> {
            if (msgExecutor != null) {
                return CompletableFuture.runAsync(() -> {
             
                    // Graceful shutdown - no new tasks, but finish queued ones
                    msgExecutor.shutdown();
                    try {
                        // Wait for all queued UI updates to complete
                        if (!msgExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                            Log.logError("[PasswordChange] Warning: Executor did not terminate in time");
                            msgExecutor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        msgExecutor.shutdownNow();
                    }
                });
            }
            return CompletableFuture.completedFuture(null);
        }).exceptionally(ex -> {
            // Log but don't fail on cleanup errors
            if(!msgExecutor.isShutdown()){
                msgExecutor.shutdownNow();
            }
            Log.logError("[PasswordChange] Error during stream completion: " + ex.getMessage());
            return null;
        });
    }
    
    private void handleProgressMessage(NoteBytes nextNoteBytes) {
        if (nextNoteBytes.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            return;
        }
        
        NoteBytesMap map = nextNoteBytes.getAsMap();
        NoteBytes typeBytes = map.get(Keys.EVENT);
        
        if (typeBytes == null) {
            return;
        }
        
        String type = typeBytes.getAsString();
        
        switch (type) {
            case "progress":
                handleProgressUpdate(map);
                break;
            case "error":
                handleErrorMessage(map);
                break;
        }
    }
    
    /**
     * Thread-safe progress update handler
     * This method is called from the single-threaded executor, so no synchronization needed
     */
    private void handleProgressUpdate(NoteBytesMap map) {
        NoteBytes scopeBytes = map.get(Keys.SCOPE);
        
        if (scopeBytes == null) {
            return;
        }
        
        String scope = scopeBytes.getAsString();
        long completed = ProgressMessage.getCompleted(map);
        long total = ProgressMessage.getTotal(map);
        
        // Only show progress bars for scopes with total > 0
        if (total <= 0) {
            return;
        }
        
        double percentage = ProgressMessage.getPercentage(map);
        String message = ProgressMessage.getMessage(map);
        
        // Get or create progress bar for this scope
        TerminalProgressBar progressBar = progressBars.get(scope);
        
        if (progressBar == null) {
            // Allocate a new row for this scope
            int row = currentRow;
            progressBarRows.put(scope, row);
            currentRow += 3; // Space between progress bars
            
            // Create label for this scope
            String label = getScopeLabel(scope);
            terminal.printAt(row - 1, 10, label);
            
            // Create new progress bar
            progressBar = new TerminalProgressBar(
                terminal, row, 10, 50, TerminalProgressBar.Style.CLASSIC);
            progressBars.put(scope, progressBar);
        }
        
        // Format progress message based on scope
        String formattedProgress = formatProgressMessage(scope, completed, total, percentage, message);
        
        // Update the progress bar (thread-safe because we're in single-threaded executor)
        progressBar.update(percentage, formattedProgress);
    }
    
    private String getScopeLabel(String scope) {
        return switch (scope) {
            case "ITEM_INFO" -> "File Processing:";
            case "INFO" -> "Ledger Parsing:";
            case "UPDATED_ENCRYPTION" -> "Encryption Updates:";
            default -> scope + ":";
        };
    }
    
    private String formatProgressMessage(String scope, long completed, long total, 
                                        double percentage, String message) {
        return switch (scope) {
            case "ITEM_INFO" -> 
                String.format("Files: %d/%d (%.1f%%) - %s", 
                    completed, total, percentage, message);
            
            case "INFO" -> 
                String.format("Bytes: %d/%d (%.1f%%) - %s", 
                    completed, total, percentage, truncateFilePath(message));
            
            case "UPDATED_ENCRYPTION" -> 
                String.format("Updates: %d/%d (%.1f%%) - %s", 
                    completed, total, percentage, message);
            
            default -> 
                String.format("Progress: %d/%d (%.1f%%) - %s", 
                    completed, total, percentage, message);
        };
    }
    
    private String truncateFilePath(String path) {
        if (path == null || path.length() <= 40) {
            return path;
        }
        // Show beginning and end of long paths
        return path.substring(0, 15) + "..." + path.substring(path.length() - 22);
    }
    
    /**
     * Thread-safe error message handler
     * This method is called from the single-threaded executor
     */
    private void handleErrorMessage(NoteBytesMap map) {
        NoteBytes exception = map.get(Keys.EXCEPTION);
        
        if (exception != null) {
            String errMsg = "Error: ";
            try {
                Object e = exception.getAsObject();
                if (e instanceof Throwable t) {
                    errMsg += t.getMessage();
                }
            } catch (IOException ex) {
                errMsg += "Error serialization error";
            } catch (ClassNotFoundException cnfE) {
                errMsg += "Error class not found";
            }
            
            // Display error below progress bars
            int errorRow = currentRow + 2;
            terminal.printAt(errorRow, 10, "⚠ " + errMsg);
            currentRow = errorRow + 1;
        }
    }
    
    private Void handlePasswordChangeError(Throwable ex) {
        // Shutdown executor
        if (msgExecutor != null) {
            msgExecutor.shutdownNow();
        }
        
        cleanupPasswords();
        
        terminal.clear()
            .thenCompose(v -> terminal.printError(
                "✗ Password change failed\n\n" +
                "Error: " + ex.getMessage() + "\n\n" +
                "System may require recovery."))
            .thenCompose(v -> terminal.printAt(10, 10, 
                "Press any key to continue..."))
            .thenRun(() -> waitForKeyPress(keyboard, () -> terminal.goBack()));
        
        return null;
    }
    
    /**
     * Clean up password data securely
     */
    private void cleanupPasswords() {
        if (currentPassword != null) {
            currentPassword.close();
            currentPassword = null;
        }
        if (newPassword != null) {
            newPassword.close();
            newPassword = null;
        }
    }
    
    private void showDiskSpaceError(io.netnotes.engine.noteFiles.DiskSpaceValidation validation) {
        String error = String.format(
            "Insufficient disk space!\n\n" +
            "Files to re-encrypt: %d\n" +
            "Total size: %.2f MB\n" +
            "Space required: %.2f MB\n" +
            "Space available: %.2f MB\n" +
            "Additional needed: %.2f MB\n\n" +
            "Please free up disk space and try again.",
            validation.getNumberOfFiles(),
            validation.getTotalFileSizes() / (1024.0 * 1024.0),
            (validation.getRequiredSpace() + validation.getBufferSpace()) / (1024.0 * 1024.0),
            validation.getAvailableSpace() / (1024.0 * 1024.0),
            ((validation.getRequiredSpace() + validation.getBufferSpace()) - 
                validation.getAvailableSpace()) / (1024.0 * 1024.0)
        );
        
        terminal.clear()
            .thenCompose(v -> terminal.printTitle("Insufficient Disk Space"))
            .thenCompose(v -> terminal.printError(error))
            .thenCompose(v -> terminal.printAt(15, 10, "Press any key to go back..."))
            .thenRun(() -> waitForKeyPress(keyboard, () -> terminal.goBack()));
    }
    
    private void cleanup() {
        if (passwordReader != null) {
            keyboard.setEventConsumer(null);
            passwordReader.close();
            passwordReader = null;
        }
        
        cleanupPasswords();
        
        // StreamReader cleans itself up via try-with-resources in its future
        // Just null out the reference
        if (streamReader != null) {
            streamReader = null;
        }
        
        if (msgExecutor != null) {
            msgExecutor.shutdownNow();
            msgExecutor = null;
        }
        
        progressBars.clear();
        progressBarRows.clear();
    }
}