package io.netnotes.engine.io.input.ephemeralEvents;

import java.util.Arrays;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralKeyCharEvent - Character event with ephemeral codepoint
 * SECURITY CRITICAL: Never convert to int - keep as bytes
 */
public class EphemeralKeyCharEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral codepointBytes;
    private final NoteBytesEphemeral stateFlagsBytes;
    private NoteBytes utf8Cache = null;
    private int stateFlagsCache = -1;
    private int[] codepointCache = null;
    private String strCache = null;
    
    public EphemeralKeyCharEvent(ContextPath sourcePath,
                                 NoteBytesEphemeral codepointData,
                                 NoteBytesEphemeral stateFlags) {
        super(sourcePath);
        this.codepointBytes = codepointData;
        this.stateFlagsBytes = stateFlags;
    }
    
    /**
     * Get codepoint as bytes (DO NOT convert to int for passwords)
     */
    public NoteBytesEphemeral getCodepointBytes() {
        return codepointBytes;
    }

    public int[] getCodepoint(){
        if(codepointCache != null){
            return codepointCache;
        }
        codepointCache = new int[]{ codepointBytes.getAsInt() };
        return codepointCache;
    }

    /**
     * Not recommended for secure use cases
     * 
     * @return String representation of char
     */
    public String getString(){
        if(strCache != null){
            return strCache;
        }
        if(codepointCache != null){
            strCache = new String(codepointCache, 0, 1);
            return strCache;
        }
        NoteBytes utf8 = getUTF8();
        strCache = utf8.getAsString();
        return strCache;
    }

    /**
     * Do no clear returned value, use close
     * @return UTF8 bytes
     */
    public NoteBytes getUTF8() { 
        if(utf8Cache == null){
            NoteBytes charBytes = Keyboard.codePointToASCII(codepointBytes);
            if(charBytes != null){
                // doesn't cache bytes so that they are not cleared
                return charBytes;
            }else{
                // codepoint isn't ASCII, computation required
                int[] cp = getCodepoint();
                // use cache clears on close
                utf8Cache =  Keyboard.codePointToUtf8(cp[0]);
                return utf8Cache;
            }
        }else{
            return utf8Cache;
        }
    }
    
    public NoteBytesEphemeral getStateFlagsBytes() {
        return stateFlagsBytes;
    }

    public int getStateFlags(){
        if(stateFlagsCache != -1){
            return stateFlagsCache;
        }
        stateFlagsCache = stateFlagsBytes.getAsInt();
        return stateFlagsCache;
    }

    private void clearCodePointCache(){
        if(codepointCache != null){
            Arrays.fill(codepointCache, 0);
            for (int i = 0; i < codepointCache.length; i++) {
                NoteBytes.clearanceVerifier ^= codepointCache[i]; 
                if (codepointCache[i] != 0) {
                    System.err.println("Warning: Memory clear verification failed at index " + codepointCache[i]);
                }
            }
            Thread.yield();
        }
    }
    
    @Override
    public void close() {
        clearCodePointCache();
        if(utf8Cache != null){
            utf8Cache.destroy();
        }
        codepointBytes.close();
        stateFlagsBytes.close();

        Thread.yield();
    }
}