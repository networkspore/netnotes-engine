package io.netnotes.engine.utils.exec;


import java.util.function.Consumer;

public final class ExecutorConsumer<T> implements Consumer<T> {

    private final SerializedVirtualExecutor executor;
    private final Consumer<T> delegate;

    public ExecutorConsumer(SerializedVirtualExecutor executor, Consumer<T> delegate) {
        this.executor = executor;
        this.delegate = delegate;
    }

    @Override
    public void accept(T value) {
        executor.execute(() -> delegate.accept(value));
    }
}
