package io.netnotes.engine.noteBytes.processing;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.collections.NoteBytesPairEphemeral;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;


public class AsyncNoteBytesWriter implements AutoCloseable {
    private final NoteBytesWriter delegate;
    private final Semaphore semaphore = new Semaphore(1, true);
    private final Executor executor;
    private boolean m_wasClosed = false;

    public AsyncNoteBytesWriter(OutputStream outputStream, Executor executor) {
        this.delegate = new NoteBytesWriter(outputStream);
        this.executor = executor;
    }

    // Convenience constructor using common pool
    public AsyncNoteBytesWriter(OutputStream outputStream) {
        this(outputStream, Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()));
    }

    private CompletableFuture<Void> runAsync(IOConsumer<NoteBytesWriter> action) {
        return CompletableFuture.runAsync(() -> {
            try {
                semaphore.acquire();
                try {
                    action.accept(delegate);
                } finally {
                    semaphore.release();
                }
            } catch (IOException e) {
                throw new RuntimeException(NoteMessaging.Error.IO, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
            }
        }, executor);
    }

    private <R> CompletableFuture<R> supplyAsync(IOFunction<NoteBytesWriter, R> action) {
        return CompletableFuture.supplyAsync(() -> {

                try {
                    semaphore.acquire();
                    try {
                        return action.apply(delegate);
                    } finally {
                        semaphore.release();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(NoteMessaging.Error.IO, e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
                }

        }, executor);
    }

    // Async write methods

    public CompletableFuture<Integer> writeAsync(NoteBytes noteBytes) {
        return supplyAsync(w -> w.write(noteBytes));
    }

    public CompletableFuture<Integer> writeAsync(NoteBytesMetaData metaData) {
        return supplyAsync(w -> w.write(metaData));
    }

    public CompletableFuture<Integer> writeAsync(byte[] data) {
        return supplyAsync(w -> w.write(data));
    }

    public CompletableFuture<Integer> writeAsync(byte[] data, int offset, int length) {
        return supplyAsync(w -> w.write(data, offset, length));
    }

    public CompletableFuture<Integer> writeAsync(NoteBytesPair pair) {
        return supplyAsync(w -> w.write(pair));
    }

    public CompletableFuture<Integer> writeAsync(NoteBytesPairEphemeral pair) {
        return supplyAsync(w -> w.write(pair));
    }

    public CompletableFuture<Integer> writeAsync(NoteBytes key, NoteBytes value) {
        return supplyAsync(w -> w.write(key, value));
    }

    public CompletableFuture<Integer> writeAsync(NoteBytesArray noteBytesArray) {
        return supplyAsync(w -> w.write(noteBytesArray));
    }

    public CompletableFuture<Void> flushAsync() {
        return runAsync(NoteBytesWriter::flush);
    }

    public boolean wasClosed(){
        return m_wasClosed;
    }

    @Override
    public void close() throws IOException {
        m_wasClosed = true;
        delegate.close();
    }


    @FunctionalInterface
    private interface IOConsumer<T> {
        void accept(T t) throws IOException;
    }

    @FunctionalInterface
    private interface IOFunction<T, R> {
        R apply(T t) throws IOException;
    }
}
