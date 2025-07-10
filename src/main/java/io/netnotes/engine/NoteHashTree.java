package io.netnotes.engine;

import java.io.IOException;

public class NoteHashTree extends NotePairTree {
    private int m_digestLength = 32;
    public NoteHashTree(NoteBytePair... pairs) throws IOException{
        super(NotePairTree.noteBytePairsToByteArray(pairs));
    }

}
