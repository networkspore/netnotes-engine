package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard;
import io.netnotes.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralKeyCharEvent - Character event with ephemeral codepoint
 * SECURITY CRITICAL: Never convert to int - keep as bytes
 */
public class EphemeralKeyCharEvent extends EphemeralKeyboardEvent {
    private final NoteBytesEphemeral codePointBytes;

    private NoteBytesEphemeral utf8Cache = null;
    private int codepointCache = -1;
    private String strCache = null;
    
    public EphemeralKeyCharEvent(ContextPath sourcePath,
                                 NoteBytesEphemeral typeBytes,
                                int stateFlags,
                                NoteBytesEphemeral codepointData) {
        super(sourcePath, typeBytes, stateFlags);
        this.codePointBytes = codepointData;
    }
    
    /**
     * Get codepoint as bytes (DO NOT convert to int for passwords)
     */
    public NoteBytesEphemeral getCodePointBytes() {
        return codePointBytes;
    }

    public int getCodepoint(){
        if(codepointCache != -1){
            return codepointCache;
        }
        codepointCache = codePointBytes.getAsInt();
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
        int cp = getCodepoint();
        strCache = Character.toString(cp);
        return strCache;
  
    }

    /**
     * Do no clear returned value, use close
     * @return UTF8 bytes
     */
    public NoteBytesEphemeral getUTF8() { 
        if(utf8Cache == null){
            NoteBytesEphemeral charBytes = Keyboard.codePointToASCII(codePointBytes);
            if(charBytes != null){
                // doesn't cache bytes so that they are not cleared
                return charBytes;
            }else{
                // codepoint isn't ASCII, computation required
                int cp = getCodepoint();
                // use cache clears on close
                utf8Cache =  Keyboard.codePointToUtf8(cp);
                return utf8Cache;
            }
        }else{
            return utf8Cache;
        }
    }
    
 


 
    
    @Override
    public void close() {
        super.close();
        strCache = null;
        codepointCache = Integer.MAX_VALUE;
        if(utf8Cache != null){
            utf8Cache.close();
        }
        codePointBytes.close();
    }
}