package io.netnotes.engine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.noteFiles.notePath.NotePath;

public interface AppDataInterface {
    void shutdown();
    HostServicesInterface getHostServices();
    ExecutorService getExecService();
    CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path);
    CompletableFuture<NotePath> deleteNoteFilePath(NoteStringArrayReadOnly path, boolean recursive, 
        AsyncNoteBytesWriter progressStream);
}
