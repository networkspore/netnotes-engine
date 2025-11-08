package io.netnotes.engine.core;

import java.util.concurrent.CompletableFuture;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteFiles.NoteFile;

public interface AppDataInterface {
    void shutdown();
    CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path);
}
