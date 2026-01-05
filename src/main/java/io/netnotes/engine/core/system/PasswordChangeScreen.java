package io.netnotes.engine.core.system;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.StreamReader;
import io.netnotes.engine.core.system.control.terminal.ClientTerminalRenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TerminalCommands;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalProgressBar;
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
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

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
    private String statusMessage = null;
    private String errorMessage = null;
    
    public PasswordChangeScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public RenderState getRenderState() {
        RenderState.Builder builder = RenderState.builder();
        
        // Clear screen
        builder.add((term) -> term.clear());
        
        // Title
        builder.add((term) -> 
            term.printAt(1, (PasswordChangeScreen.this.terminal.getCols() - 21) / 2, "Change Master Password", TextStyle.BOLD));
        
        // Current prompt based on step
        String prompt;
        int cursorCol;
        switch (currentStep) {
            case VERIFY_CURRENT:
                prompt = "Enter current password:";
                cursorCol = 33;
                break;
            case ENTER_NEW:
                prompt = "Enter new password:";
                cursorCol = 30;
                break;
            case CONFIRM_NEW:
                prompt = "Confirm new password:";
                cursorCol = 32;
                break;
            default:
                prompt = "";
                cursorCol = 0;
        }
        
        if (!prompt.isEmpty()) {
            builder.add((term) -> {
                term.printAt(5, 10, prompt, TextStyle.NORMAL);
                term.moveCursor(5, cursorCol);
            });
        }
        
        // Status message
        if (statusMessage != null && !statusMessage.isEmpty()) {
            builder.add((term) -> 
                term.printAt(9, 10, statusMessage, TextStyle.INFO));
        }
        
        // Error message
        if (errorMessage != null && !errorMessage.isEmpty()) {
            builder.add((term) -> 
                term.printAt(15, 10, errorMessage, TextStyle.ERROR));
        }
        
        // Add progress bar labels and bars
        for (Map.Entry<String, Integer> entry : progressBarRows.entrySet()) {
            String scope = entry.getKey();
            int row = entry.getValue();
            String label = getScopeLabel(scope);
            
            builder.add((term) -> 
                term.printAt(row - 1, 10, label, TextStyle.NORMAL));
            
            TerminalProgressBar bar = progressBars.get(scope);
            if (bar != null) {
                builder.addAll(bar.getRenderState().getElements());
            }
        }
        
        return builder.build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentStep = Step.VERIFY_CURRENT;
        invalidate(); // Trigger initial render
        startPasswordEntry();
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    private void startPasswordEntry() {
        passwordReader = new PasswordReader(terminal.getPasswordEventHandlerRegistry());

        
        passwordReader.setOnPassword(password -> {
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
                                        statusMessage = summary;
                                        invalidate();
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
                                .thenCompose(v -> terminal.printAt(15, 10, TerminalCommands.PRESS_ANY_KEY))
                                .thenRun(() -> terminal.waitForKeyPress( () -> terminal.goBack()));
                            return null;
                        });
                } else {
                    password.close();
                    errorMessage = "Invalid password. Please try again.";
                    invalidate();
                    // Delay and retry
                    CompletableFuture.runAsync(() -> TimeHelpers.timeDelay(2), VirtualExecutors.getVirtualExecutor())
                        .thenRun(() -> {
                            errorMessage = null;
                            invalidate();
                        });
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
        invalidate();
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
            errorMessage = "Passwords do not match. Please try again.";
            invalidate();
            // Delay and retry
            CompletableFuture.runAsync(() -> TimeHelpers.timeDelay(2), VirtualExecutors.getVirtualExecutor())
                .thenRun(() -> {
                    errorMessage = null;
                    currentStep = Step.ENTER_NEW;
                    invalidate();
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
        terminal.enterRecoveryMode("password_change_in_progress")
        .thenCompose(v->terminal.clear())
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
            .thenCompose(success -> {
                // Executor is already shutdown by waitForStreamReaderCompletion
                // Just clean up passwords
                cleanupPasswords();
                
                if (success) {
                    return terminal.exitRecoveryMode()
                        .thenCompose((v)->terminal.clear())
                                .thenCompose(v -> terminal.printSuccess(
                                    "✓ Password changed successfully!\n\n" +
                                    "All files have been re-encrypted."))
                                .thenCompose(v -> terminal.printAt(10, 10, TerminalCommands.PRESS_ANY_KEY))
                                .thenRun(() -> terminal.waitForKeyPress( () -> terminal.goBack()));
                        
                } else {
                    return terminal.clear()
                        .thenCompose(v -> terminal.printError(
                            "✗ Password change completed with errors\n\n" +
                            "Some files may not have been re-encrypted."))
                        .thenCompose(v -> terminal.printAt(10, 10, TerminalCommands.PRESS_ANY_KEY))
                        .thenCompose(v -> terminal.printAt(12, 10, "Press any key to enter recovery..."))
                        .thenCompose((v) -> terminal.waitForKeyPress(() -> {
                            // Transition to recovery screen
                            terminal.getStateMachine().removeState(SystemTerminalContainer.SHOWING_SCREEN);
                            terminal.getStateMachine().addState(SystemTerminalContainer.FAILED_SETTINGS);
                        }));
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
        NoteBytes typeBytes = map.get(Keys.TYPE);
        
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
            
            // Create new progress bar
            progressBar = new TerminalProgressBar(
                terminal, row, 10, 50, TerminalProgressBar.Style.CLASSIC);
            progressBars.put(scope, progressBar);
            
            // Trigger redraw to show new progress bar
            invalidate();
        }
        
        // Format progress message based on scope
        String formattedProgress = formatProgressMessage(scope, completed, total, percentage, message);
        
        // Update the progress bar (thread-safe because we're in single-threaded executor)
        progressBar.updatePercent(percentage, formattedProgress);
       
        // Trigger redraw
        invalidate();
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
                TerminalCommands.PRESS_ANY_KEY))
            .thenCompose((v) -> terminal.waitForKeyPress(() -> {
                // Transition to recovery screen
                terminal.getStateMachine().removeState(SystemTerminalContainer.SHOWING_SCREEN);
                terminal.getStateMachine().addState(SystemTerminalContainer.FAILED_SETTINGS);
            }));
        
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
            .thenRun(() -> terminal.waitForKeyPress(() -> terminal.goBack()));
    }
    
    private void cleanup() {
        if (passwordReader != null) {
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