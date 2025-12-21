package io.netnotes.engine.utils.virtualExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class ScheduledTask<T> implements Comparable<ScheduledTask<?>> {
    final Callable<T> callable;
    final CompletableFuture<T> future;
    final long executeAtNanos;
    final long sequenceNumber;

    ScheduledTask(Callable<T> callable, CompletableFuture<T> future, 
                    long executeAtNanos, long sequenceNumber) {
        this.callable = callable;
        this.future = future;
        this.executeAtNanos = executeAtNanos;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public int compareTo(ScheduledTask<?> other) {
        // Sort by execution time first
        int timeCompare = Long.compare(this.executeAtNanos, other.executeAtNanos);
        if (timeCompare != 0) {
            return timeCompare;
        }
        // If same time, sort by sequence number (FIFO)
        return Long.compare(this.sequenceNumber, other.sequenceNumber);
    }
}