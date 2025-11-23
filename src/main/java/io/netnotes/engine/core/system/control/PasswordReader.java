package io.netnotes.engine.core.system.control;

import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.bouncycastle.util.Arrays;

import io.netnotes.engine.io.events.ExecutorConsumer;
import io.netnotes.engine.io.events.RoutedEvent;
import io.netnotes.engine.io.input.Keyboard.KeyCode;
import io.netnotes.engine.io.events.KeyDownEvent;
import io.netnotes.engine.io.events.KeyCharEvent;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * PasswordReader - Secure password input reader
 * 
 * SECURITY CRITICAL:
 * - Uses NoteBytesEphemeral for password storage
 * - Clears password bytes on escape/error
 * - Returns password via CompletableFuture
 * - Caller MUST close the returned password
 * 
 * Usage:
 *   CompletableFuture<NoteBytesEphemeral> passwordFuture = new CompletableFuture<>();
 *   PasswordReader reader = new PasswordReader(passwordFuture);
 *   
 *   // Register with input source (ClaimedDevice or BaseKeyboardInput)
 *   inputDevice.addEventConsumer("password-reader", reader.getEventConsumer());
 *   
 *   passwordFuture.thenAccept(password -> {
 *       try {
 *           // Use password
 *       } finally {
 *           password.close(); // ALWAYS close
 *       }
 *   });
 */
public class PasswordReader {
    public static final int MAX_PASSWORD_BYTE_LENGTH = 256;
    public static final int MAX_KEYSTROKE_COUNT = 128;

    private NoteBytesEphemeral m_passwordBytes = new NoteBytesEphemeral(new byte[MAX_PASSWORD_BYTE_LENGTH]);
    private byte[] m_keystrokeLengths = new byte[MAX_KEYSTROKE_COUNT];
    private int m_currentLength = 0; // Total bytes used
    private int m_keystrokeCount = 0; // Number of keystrokes
    
    private volatile boolean active = true;
    
    // Event consumer for input device
    private final ExecutorConsumer<RoutedEvent> eventConsumer;
    private Consumer<NoteBytesEphemeral> onPassword;
    
    public PasswordReader() {
        // Create event consumer with virtual thread executor
        this.eventConsumer = new ExecutorConsumer<>(
            Executors.newVirtualThreadPerTaskExecutor(),
            this::handleEvent
        );
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
     */
    private void handleEvent(RoutedEvent event) {
        if (!active) return;

        // Handle key events
        if (event instanceof KeyDownEvent keyDown) {
            handleKeyDown(keyDown);
        } else if (event instanceof KeyCharEvent keyChar) {
            handleKeyChar(keyChar);
        }

    }
    
    /**
     * Handle key down events (for special keys)
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
     * Handle character events (for regular input)
     */
    private void handleKeyChar(KeyCharEvent event) {
        int codepoint = event.getCodepoint();
        
        // Convert codepoint to UTF-8 bytes
        String str = new String(Character.toChars(codepoint));
        byte[] utf8Bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
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
        onPassword.accept(new NoteBytesEphemeral(m_passwordBytes.getBytes(m_currentLength)));
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
}