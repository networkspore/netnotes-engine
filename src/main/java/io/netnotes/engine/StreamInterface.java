package io.netnotes.engine;

import io.netnotes.engine.noteBytes.NoteBytesPair;

public interface StreamInterface {
    void sendMsg(NoteBytesPair msg);
}
