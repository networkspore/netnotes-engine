package io.netnotes.engine.utils.virtualExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

class SerializedTask<T> {
    private final Callable<T> callable;
    private final CompletableFuture<T> future;
    protected volatile boolean isStarted = false;

    SerializedTask(Callable<T> callable, CompletableFuture<T> future) {
        this.callable = callable;
        this.future = future;
    }

    Callable<T> getCallable() {
        return callable;
    }

    CompletableFuture<T> getFuture() {
        return future;
    }

    T call() throws Exception {
        if (!isStarted) {
            isStarted = true;
            return callable.call();
        } else {
            throw new IllegalStateException("Task can only be called once");
        }
    }

    void cancel(){
        if(future != null){
            future.cancel(false);
        }
        isStarted = true;
    }


    boolean isStarted() { return this.isStarted; }


}