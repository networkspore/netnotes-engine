package io.netnotes.engine.virtualExecutors;


@FunctionalInterface
public interface ExecutorTask<T> {
    T call() throws Exception;
}

