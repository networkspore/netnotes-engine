package io.netnotes.engine.adapters.notes;

public interface NotesAdapterInterface {
    public void msgReceived(String filePath, Runnable onRead, Runnable onRetry, Runnable onFailed);
}
