package io.netnotes.engine.io.input.events.keyboardEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;

public final class KeyCharEvent extends RoutedKeyboardEvent {
    private final ContextPath sourcePath;
    private final NoteBytes codepointBytes;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;

    private int codepointCache = -1;
    private String strCache = null;
    private NoteBytes utf8Cache = null;
    public KeyCharEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int stateFlags, NoteBytes codepoint) {
        this.sourcePath = sourcePath;
        this.codepointBytes = codepoint;
        this.stateFlags = stateFlags;
        this.typeBytes = typeBytes;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public NoteBytes getCodepointData() { return codepointBytes; }
    @Override
    public int getStateFlags() { return stateFlags; }

    public void setStateFlags(int flags) { stateFlags = flags; }

    

    public int getCodepoint(){
        if(codepointCache > -1){
            return codepointCache;
        }
        codepointCache = codepointBytes.getAsInt();
        return codepointCache;
    }

    public String getString(){
        if(strCache != null){
            return strCache;
        }
        int cp =  getCodepoint();
        strCache = Character.toString(cp);
        return strCache;
        

    }
    
    public NoteBytes getUTF8() { 
        if(utf8Cache == null){
            NoteBytes charBytes = Keyboard.codePointToASCII(codepointBytes);
            if(charBytes != null){
                utf8Cache = charBytes;
                return utf8Cache;
            }else{
                int cp = getCodepoint();
                utf8Cache = Keyboard.codePointToUtf8(cp);
                return utf8Cache;
            }
        }else{
            return utf8Cache;
        }
    }

    @Override
    public String toString(){
        return getString();
    }

    @Override
    public NoteBytesReadOnly getEventTypeBytes() {
        return typeBytes;
    }

  
}
