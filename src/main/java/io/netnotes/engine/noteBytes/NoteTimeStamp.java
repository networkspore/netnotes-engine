package io.netnotes.engine.noteBytes;

public class NoteTimeStamp extends NoteLong {
    
    public NoteTimeStamp(){
        super(System.currentTimeMillis());
    }

    public NoteTimeStamp create(){
        return new NoteTimeStamp();
    }
}
