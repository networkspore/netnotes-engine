package io.netnotes.engine.core.system.control;


import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.bouncycastle.util.Arrays;

import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyCharModsEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.ExecutorConsumer;
import io.netnotes.engine.io.input.events.KeyCharEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;

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
    
  
    private final KeyRunTable m_keyRunTable = new KeyRunTable(
        new NoteBytesRunnablePair(KeyCodeBytes.ENTER, ()->onComplete()),
        new NoteBytesRunnablePair(KeyCodeBytes.BACKSPACE ,()->handleBackspace()),
        new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE ,()->escape())
    );
   
    
    // Event consumer for input device
    private final Consumer<RoutedEvent> eventConsumer;
    private Consumer<NoteBytesEphemeral> onPassword;
    
    public PasswordReader() {
        // Create event consumer with virtual thread executor
        this.eventConsumer = this::handleEvent;

    }
    
    /**
     * Get event consumer for registration with input device
     */
    public Consumer<RoutedEvent> getEventConsumer() {
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
        // lookup keycode and run
        m_keyRunTable.run(event.getKeyCodeBytes());
    }
    
    /**
     * Handle ephemeral character event (SECURITY CRITICAL)
     * Never converts to int codepoint - works directly with UTF-8 bytes
     */
    private void handleEphemeralKeyChar(EphemeralKeyCharModsEvent event) {
        // Get UTF-8 bytes from registry
        NoteBytes keyUTF8 = event.getUTF8();
    
        
        if(keyUTF8 == null){
            System.err.println("Key is not in lookup table");
        }

        // Check buffer space
        if (m_keystrokeCount >= MAX_KEYSTROKE_COUNT) {
            System.err.println("Password too long (max keystrokes exceeded)");
            return;
        }

        int keyUTF8ByteLength = keyUTF8.byteLength();
        
        if (m_currentLength + keyUTF8ByteLength > MAX_PASSWORD_BYTE_LENGTH) {
            System.err.println("Password too long (max bytes exceeded)");
            return;
        }
        
        // Copy UTF-8 bytes into password buffer
        System.arraycopy(keyUTF8.get(), 0, m_passwordBytes.get(), m_currentLength, keyUTF8ByteLength);
        
        // Record this keystroke
        m_keystrokeLengths[m_keystrokeCount] = (byte) keyUTF8ByteLength;
        m_keystrokeCount++;
        m_currentLength += keyUTF8ByteLength;
        
       
    }
    
    /**
     * Handle key down events (for special keys) - Regular events
     */
    private void handleKeyDown(KeyDownEvent event) {
        m_keyRunTable.run(event.getKeyCodeBytes());
    }
    
    /**
     * Handle character events (for regular input) - Regular events
     */
    private void handleKeyChar(KeyCharEvent event) {
        NoteBytes utf8 = event.getUTF8();

        if(utf8 == null){
            System.err.println("Key is not in lookup table");
            return;
        }
        // Check buffer space
        if (m_keystrokeCount >= MAX_KEYSTROKE_COUNT) {
            System.err.println("Password too long (max keystrokes exceeded)");
            return;
        }

        int utf8ByteLength = utf8.byteLength();
        
        if (m_currentLength + utf8ByteLength > MAX_PASSWORD_BYTE_LENGTH) {
            System.err.println("Password too long (max bytes exceeded)");
            return;
        }
        
        // Copy UTF-8 bytes into password buffer
        System.arraycopy(utf8.get(), 0, m_passwordBytes.get(), m_currentLength, utf8ByteLength);
        
        // Record this keystroke
        m_keystrokeLengths[m_keystrokeCount] = (byte) utf8ByteLength;
        m_keystrokeCount++;
        m_currentLength += utf8ByteLength;
        
       
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
    }
    
    public void close() {
        escape();
    }
    
}