package io.netnotes.engine.core.system.control;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.bouncycastle.util.Arrays;

import io.netnotes.engine.io.input.Keyboard.KeyCode;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyCharModsEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.ExecutorConsumer;
import io.netnotes.engine.io.input.events.KeyCharEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * PasswordReader - Secure password input reader with ephemeral event support
 * 
 * SECURITY CRITICAL:
 * - Uses NoteBytesEphemeral for password storage
 * - Handles both regular and ephemeral events
 * - For encrypted devices: processes EphemeralKeyCharModsEvent directly
 * - For plaintext devices: processes KeyCharEvent normally
 * - Clears password bytes on escape/error
 * - Returns password via callback
 * - Caller MUST close the returned password
 * 
 * Usage:
 *   PasswordReader reader = new PasswordReader();
 *   reader.setOnPassword(password -> {
 *       try {
 *           // Use password
 *       } finally {
 *           password.close(); // ALWAYS close
 *       }
 *   });
 *   
 *   // Register with encrypted input source
 *   inputDevice.addEventConsumer("password-reader", reader.getEventConsumer());
 */
public class PasswordReader {
    public static final int MAX_PASSWORD_BYTE_LENGTH = 256;
    public static final int MAX_KEYSTROKE_COUNT = 128;

    private NoteBytesEphemeral m_passwordBytes = new NoteBytesEphemeral(new byte[MAX_PASSWORD_BYTE_LENGTH]);
    private byte[] m_keystrokeLengths = new byte[MAX_KEYSTROKE_COUNT];
    private int m_currentLength = 0; // Total bytes used
    private int m_keystrokeCount = 0; // Number of keystrokes
    
    private volatile boolean active = true;
    private final Map<NoteBytesReadOnly, Runnable> m_keyCodes = new HashMap<>();
   
    
    // Event consumer for input device
    private final ExecutorConsumer<RoutedEvent> eventConsumer;
    private Consumer<NoteBytesEphemeral> onPassword;
    
    public PasswordReader() {
        // Create event consumer with virtual thread executor
        this.eventConsumer = new ExecutorConsumer<>(
            Executors.newVirtualThreadPerTaskExecutor(),
            this::handleEvent
        );
        setupKeyCodeBytes();
    }

    private void setupKeyCodeBytes(){
        m_keyCodes.put(new NoteBytesReadOnly(KeyCode.ENTER) ,()->onComplete());
        m_keyCodes.put(new NoteBytesReadOnly(KeyCode.BACKSPACE) ,()->handleBackspace());
        m_keyCodes.put(new NoteBytesReadOnly(KeyCode.ESCAPE) ,()->escape());
    }
    
    /**
     * Get event consumer for registration with input device
     */
    public ExecutorConsumer<RoutedEvent> getEventConsumer() {
        return eventConsumer;
    }

    public void setOnPassword(Consumer<NoteBytesEphemeral> onPassword){
        this.onPassword = onPassword;
    }

    /**
     * Handle input events from device
     * Supports both regular and ephemeral events
     */
    private void handleEvent(RoutedEvent event) {
        if (!active) return;
    
        // Handle ephemeral events (from encrypted devices)
        if (event instanceof EphemeralRoutedEvent ephemeralEvent) {
            try (ephemeralEvent) { // Auto-close to wipe memory
                handleEphemeralEvent(ephemeralEvent);
            }
            return;
        }

        // Handle regular events (from plaintext devices)
        if (event instanceof KeyDownEvent keyDown) {
            handleKeyDown(keyDown);
        } else if (event instanceof KeyCharEvent keyChar) {
            handleKeyChar(keyChar);
        }
    }
    
    /**
     * Handle ephemeral events from encrypted device
     */
    private void handleEphemeralEvent(EphemeralRoutedEvent event) {
        if (event instanceof EphemeralKeyDownEvent keyDown) {
            handleEphemeralKeyDown(keyDown);
        } else if (event instanceof EphemeralKeyCharModsEvent keyChar) {
            handleEphemeralKeyChar(keyChar);
        }
    }
    
  
    /**
     * Handle ephemeral key down (for special keys)
     */
    private void handleEphemeralKeyDown(EphemeralKeyDownEvent event) {
        // Extract key code from ephemeral data
        Runnable runnable = m_keyCodes.get(event.getKeyData());
        if(runnable != null){
            runnable.run();
        }
    }
    
    /**
     * Handle ephemeral character event (SECURITY CRITICAL)
     * Never converts to int codepoint - works directly with UTF-8 bytes
     */
    private void handleEphemeralKeyChar(EphemeralKeyCharModsEvent event) {
        // Get UTF-8 bytes from registry
        NoteBytesReadOnly readOnly = event.getUTF8();
        

        // Check buffer space
        if (m_keystrokeCount >= MAX_KEYSTROKE_COUNT) {
            System.err.println("Password too long (max keystrokes exceeded)");
            return;
        }
        
        if (m_currentLength + readOnly.byteLength() > MAX_PASSWORD_BYTE_LENGTH) {
            System.err.println("Password too long (max bytes exceeded)");
            return;
        }
        
        // Copy UTF-8 bytes into password buffer
        System.arraycopy(readOnly.get(), 0, m_passwordBytes.get(), m_currentLength, readOnly.byteLength());
        
        // Record this keystroke
        m_keystrokeLengths[m_keystrokeCount] = (byte) readOnly.byteLength();
        m_keystrokeCount++;
        m_currentLength += readOnly.byteLength();
        
       
    }
    
    /**
     * Handle key down events (for special keys) - Regular events
     */
    private void handleKeyDown(KeyDownEvent event) {
        int keyCode = event.getKeyCode();
        
        // Handle special keys
        if (keyCode == KeyCode.ENTER) {
            onComplete();
            return;
        }
        
        if (keyCode == KeyCode.BACKSPACE) {
            handleBackspace();
            return;
        }
        
        if (keyCode == KeyCode.ESCAPE) {
            escape();
            return;
        }
    }
    
    /**
     * Handle character events (for regular input) - Regular events
     */
    private void handleKeyChar(KeyCharEvent event) {
        int codepoint = event.getCodepoint();
        
        // Convert codepoint to UTF-8 bytes
        String str = new String(Character.toChars(codepoint));
        byte[] utf8Bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        try {
            // Check buffer space
            if (m_keystrokeCount >= MAX_KEYSTROKE_COUNT) {
                System.err.println("Password too long (max keystrokes exceeded)");
                return;
            }
            
            if (m_currentLength + utf8Bytes.length > MAX_PASSWORD_BYTE_LENGTH) {
                System.err.println("Password too long (max bytes exceeded)");
                return;
            }
            
            // Copy UTF-8 bytes into password buffer
            System.arraycopy(utf8Bytes, 0, m_passwordBytes.get(), m_currentLength, utf8Bytes.length);
            
            // Record this keystroke
            m_keystrokeLengths[m_keystrokeCount] = (byte) utf8Bytes.length;
            m_keystrokeCount++;
            m_currentLength += utf8Bytes.length;
            
        } finally {
            // Wipe UTF-8 bytes after use
            Arrays.fill(utf8Bytes, (byte) 0);
        }
    }
    
    /**
     * Handle backspace
     */
    private void handleBackspace() {
        if (m_keystrokeCount > 0) {
            // Get the length of the last keystroke
            m_keystrokeCount--;
            int lastKeystrokeLength = m_keystrokeLengths[m_keystrokeCount];
            
            // Zero out the bytes of the last keystroke
            for (int i = 0; i < lastKeystrokeLength; i++) {
                m_passwordBytes.get()[m_currentLength - lastKeystrokeLength + i] = 0;
            }
            
            m_currentLength -= lastKeystrokeLength;
            m_keystrokeLengths[m_keystrokeCount] = 0;
        }
    }
    
    /**
     * Complete password entry (ENTER pressed)
     */
    private void onComplete() {
        if (!active) return;
        
        active = false;
        
        if (onPassword != null) {
            // Create new ephemeral containing only the password bytes
            onPassword.accept(new NoteBytesEphemeral(m_passwordBytes.getBytes(m_currentLength)));
        }
        
        // Clear our working buffer
        escape();
    }
    
    /**
     * Clear password buffer (SECURITY CRITICAL)
     */
    public void escape() {
        m_passwordBytes.close();
        m_currentLength = 0;
        m_keystrokeCount = 0;
        Arrays.fill(m_keystrokeLengths, (byte) 0);
        active = false;
    }
    
    public void close() {
        escape();
    }
    
    public boolean isActive() {
        return active;
    }
}