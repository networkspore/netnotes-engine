package io.netnotes.engine.noteBytes;

import java.io.IOException;

public class NoteHashTree extends NoteBytesObject {
    private int m_digestLength = 32;
    public NoteHashTree(NoteBytesPair... pairs) throws IOException{
        super(NoteBytesObject.noteBytePairsToByteArray(pairs));
    }

}
