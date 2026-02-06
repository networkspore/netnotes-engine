package io.netnotes.engine.core.system.control;

import java.util.List;
import java.util.function.Consumer;

import org.bouncycastle.util.Arrays;

import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyCharEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.EventHandlerRegistry.RoutedEventHandler;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyCharEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.engine.noteBytes.KeyRunTable;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * PasswordReader - Secure password input reader with ephemeral event support
 * 
 */
public class PasswordReader {
    public static final int MAX_PASSWORD_BYTE_LENGTH = 256;
    public static final int MAX_KEYSTROKE_COUNT = 128;

    private final EventHandlerRegistry eventRegistry;

    private NoteBytesEphemeral m_passwordBytes = new NoteBytesEphemeral(new byte[MAX_PASSWORD_BYTE_LENGTH]);
    private byte[] m_keystrokeLengths = new byte[MAX_KEYSTROKE_COUNT];
    private int m_currentLength = 0; // Total bytes used
    private int m_keystrokeCount = 0; // Number of keystrokes
    private List<RoutedEventHandler> keyDownHandlers = null;
    private List<RoutedEventHandler> keyCharHandlers = null;
    
  
    private final KeyRunTable m_keyRunTable = new KeyRunTable(
        new NoteBytesRunnablePair(KeyCodeBytes.ENTER, ()->onComplete()),
        new NoteBytesRunnablePair(KeyCodeBytes.BACKSPACE ,()->handleBackspace()),
        new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE ,()->escape())
    );
   
    private Consumer<NoteBytesEphemeral> onPassword;
    
    public PasswordReader(EventHandlerRegistry eventRegistry) {
        this.eventRegistry = eventRegistry;
        keyDownHandlers = eventRegistry.unregister(EventBytes.EVENT_KEY_DOWN);
        keyCharHandlers = eventRegistry.unregister(EventBytes.EVENT_KEY_CHAR);
        eventRegistry.register(EventBytes.EVENT_KEY_DOWN, this:: handleKeyDown);
        eventRegistry.register(EventBytes.EVENT_KEY_CHAR, this:: handleKeyChar);
    }
    
   
    public void setOnPassword(Consumer<NoteBytesEphemeral> onPassword){
        this.onPassword = onPassword;
    }

    /**
     * Handle input events from device
     * Supports both regular and ephemeral events
     */
    private void handleKeyDown(RoutedEvent event) {
        if (event instanceof KeyDownEvent keyDown) {
            handleKeyDown(keyDown);
        } else if (event instanceof EphemeralKeyDownEvent keyDown) {
            handleEphemeralKeyDown(keyDown);
            keyDown.close();
        }
    }

    private void handleKeyChar(RoutedEvent event){
        if(event instanceof KeyCharEvent keyChar){
            handleKeyChar(keyChar);
        }if (event instanceof EphemeralKeyCharEvent keyChar) {
            handleEphemeralKeyChar(keyChar);
            keyChar.close();
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
     * Handle ephemeral character event
     */
    private void handleEphemeralKeyChar(EphemeralKeyCharEvent event) {
        // Get UTF-8 bytes from registry
        
        NoteBytes keyUTF8 = event.getUTF8();
        
        if(keyUTF8 == null){
            Log.logError("Key is not in lookup table");
        }

        // Check buffer space
        if (m_keystrokeCount >= MAX_KEYSTROKE_COUNT) {
            Log.logError("Password too long (max keystrokes exceeded)");
            return;
        }

        int keyUTF8ByteLength = keyUTF8.byteLength();
        
        if (m_currentLength + keyUTF8ByteLength > MAX_PASSWORD_BYTE_LENGTH) {
            Log.logError("Password too long (max bytes exceeded)");
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
            Log.logError("Key is not in lookup table");
            return;
        }
        // Check buffer space
        if (m_keystrokeCount >= MAX_KEYSTROKE_COUNT) {
            Log.logError("Password too long (max keystrokes exceeded)");
            return;
        }

        int utf8ByteLength = utf8.byteLength();
        
        if (m_currentLength + utf8ByteLength > MAX_PASSWORD_BYTE_LENGTH) {
            Log.logError("Password too long (max bytes exceeded)");
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
        eventRegistry.unregister(EventBytes.EVENT_KEY_DOWN);
        eventRegistry.unregister(EventBytes.EVENT_KEY_CHAR);
        
        if(!keyDownHandlers.isEmpty()){
            eventRegistry.register(EventBytes.EVENT_KEY_DOWN, keyDownHandlers);
        }
        if(! keyCharHandlers.isEmpty()){
            eventRegistry.register(EventBytes.EVENT_KEY_CHAR, keyCharHandlers);
        }
        escape();
    }
    
}