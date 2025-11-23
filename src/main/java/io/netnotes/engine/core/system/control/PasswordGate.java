package io.netnotes.engine.core.system.control;

import java.util.concurrent.CompletableFuture;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * PasswordGate - Verifies passwords to unlock menu contexts
 * 
 * Implementations:
 * - BCryptGate (for settings)
 * - KeyManagerGate (for encrypted storage)
 * - CustomPuzzleGate (plugin-specific)
 */
@FunctionalInterface
public interface PasswordGate {
    
    /**
     * Verify password
     * @return CompletableFuture<Boolean> - true if correct, false if wrong
     */
    CompletableFuture<Boolean> verify(NoteBytesEphemeral password);
}