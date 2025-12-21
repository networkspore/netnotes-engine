package io.netnotes.engine.utils.virtualExecutors;


@FunctionalInterface
public interface ExecutorTask<T> {
    T call() throws Exception;
}

