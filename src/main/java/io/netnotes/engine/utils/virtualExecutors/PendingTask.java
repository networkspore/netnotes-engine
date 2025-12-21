package io.netnotes.engine.utils.virtualExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class PendingTask<T> {
    final Callable<T> task;
    final CompletableFuture<T> future;
    final CompletableFuture<Void> scheduledFuture;

    public PendingTask(Callable<T> task,
                CompletableFuture<T> future,
                CompletableFuture<Void> scheduledFuture) {
        this.task = task;
        this.future = future;
        this.scheduledFuture = scheduledFuture;
    }

    public Callable<T> getTask() {
        return task;
    }

    public CompletableFuture<T> getFuture() {
        return future;
    }

    public CompletableFuture<Void> getScheduledFuture() {
        return scheduledFuture;
    }
}
