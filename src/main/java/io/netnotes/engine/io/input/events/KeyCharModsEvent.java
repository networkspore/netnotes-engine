package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard;
import io.netnotes.engine.noteBytes.NoteBytes;

public final class KeyCharModsEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final NoteBytes codepointBytes;
    private final int stateFlags;
    private int codepointCache = -1;
    private String strCache = null;

    public KeyCharModsEvent(ContextPath sourcePath, NoteBytes codepoint, int stateFlags) {
        this.sourcePath = sourcePath;
        this.codepointBytes = codepoint;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public NoteBytes getCodepointBytes() { return codepointBytes; }

    public int getCodepoint(){
        if(codepointCache != -1){
            return codepointCache;
        }
        codepointCache = codepointBytes.getAsInt();
        return codepointCache;
    }
    public String getString(){
        if(strCache != null){
            return strCache;
        }
        NoteBytes utf8 = getUTF8();
        if(utf8 == null){
            strCache = "";
            return strCache;
        }
        strCache = new String(utf8.getBytes());
        return strCache;
    }
    public int stateFlags() { return stateFlags; }

    public NoteBytes getUTF8() { return Keyboard.getCharBytes(codepointBytes); }

    @Override 
    public String toString(){
        return getString();
    }
}


