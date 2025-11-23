package io.netnotes.engine.io.events;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class ExecutorConsumer<T> implements Consumer<T> {

    private final Executor executor;
    private final Consumer<T> delegate;

    public ExecutorConsumer(Executor executor, Consumer<T> delegate) {
        this.executor = executor;
        this.delegate = delegate;
    }

    @Override
    public void accept(T value) {
        executor.execute(() -> delegate.accept(value));
    }
}
