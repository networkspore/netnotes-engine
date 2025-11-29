package io.netnotes.engine.core;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteFiles.NoteFile;

public interface AppDataInterface {
    void shutdown();
    CompletableFuture<NoteFile> getNoteFile(ContextPath path);
}
