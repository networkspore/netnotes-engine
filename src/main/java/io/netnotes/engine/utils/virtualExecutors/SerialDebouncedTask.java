package io.netnotes.engine.utils.virtualExecutors;

import java.util.concurrent.CompletableFuture;

class SerialDebouncedTask extends SerializedTask<Void> {
    volatile long executeAtNanos;
    volatile Runnable runnable;
    final long requestedDebounceNanos;
    final long firstSubmittedAtNanos;

    SerialDebouncedTask(Runnable runnable, long debounceNanos, CompletableFuture<Void> future) {
        this(runnable, debounceNanos, System.nanoTime(), future);
    }

    SerialDebouncedTask(
        Runnable runnable, 
        long debounceNanos,
        long firstSubmittedAtNanos,
        CompletableFuture<Void> future
    ) {
        super(null, future);

        this.runnable = runnable;
        this.requestedDebounceNanos = debounceNanos;
        this.firstSubmittedAtNanos = firstSubmittedAtNanos;
        this.executeAtNanos = firstSubmittedAtNanos + debounceNanos;

    }

    long getExecuteAtNanos(){
        return executeAtNanos;
    }

    void updateWithNewTask(Runnable newRunnable, long newDebounceNanos) {
        this.runnable = newRunnable;
        
        // Calculate new potential execute time from FIRST submission
        long newExecuteAt = firstSubmittedAtNanos + newDebounceNanos;
        
        // Only extend if new request needs more time than we already planned
        if (newExecuteAt > this.executeAtNanos) {
            this.executeAtNanos = newExecuteAt;
        }
        // Otherwise keep original executeAtNanos (don't reset the clock)
    }
    
    @Override
    Void call() throws Exception{
        if (!isStarted) {
            isStarted = true;
            
            runnable.run();
            
            return null;
        } else {
            throw new IllegalStateException("Task can only be called once");
        }
    }
}
