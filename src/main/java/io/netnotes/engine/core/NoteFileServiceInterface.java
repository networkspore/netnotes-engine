package io.netnotes.engine.core;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.NoteFile;

public interface NoteFileServiceInterface {

    CompletableFuture<NoteFile> getNoteFile(ContextPath path);
    ContextPath getDataRootPath();
    CompletableFuture<Void> deleteNoteFile(ContextPath path, boolean recurrsive, AsyncNoteBytesWriter progress);
}
