package io.netnotes.engine.utils.virtualExecutors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.netnotes.engine.utils.virtualExecutors.DebouncedVirtualExecutor.DebounceStrategy;

public class VirtualExecutors {
    
    // Traditional executors (for compatibility)
    private static final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private static final ScheduledExecutorService virtualScheduled = 
        Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());
    
    // Serialized virtual executors (for ordered execution)
    private static final SerializedVirtualExecutor serializedVirtual = new SerializedVirtualExecutor();
    private static final SerializedScheduledVirtualExecutor serializedScheduledVirtual = 
        new SerializedScheduledVirtualExecutor();

    /**
     * Get the shared virtual thread executor.
     * Use getSerializedVirtualExecutor() for ordered execution
     */
    public static ExecutorService getVirtualExecutor() { 
        return virtualExecutor; 
    }

    /**
     * Get the shared scheduled virtual thread executor.
     * Use getSerializedScheduledVirtualExecutor() for ordered execution
     */
    public static ScheduledExecutorService getVirtualScheduledExecutor() { 
        return virtualScheduled; 
    }
    
    /**
     * Get the shared serialized virtual executor.
     * Guarantees serial execution order - tasks execute one at a time.
     * 
     * @return the shared SerializedVirtualExecutor
     */
    public static SerializedVirtualExecutor getSerializedVirtualExecutor() {
        return serializedVirtual;
    }
    
    /**
     * Get the shared serialized scheduled virtual executor.
     * Guarantees serial execution order with scheduling support.
     * 
     * @return the shared SerializedScheduledVirtualExecutor
     */
    public static SerializedScheduledVirtualExecutor getSerializedScheduledVirtualExecutor() {
        return serializedScheduledVirtual;
    }

    /**
     *  Create a debounced executor with TRAILING strategy with specified delay.
     * Each instance maintains its own debounce state.
     * 
     * @param <T> the result type
     * @param delayMs the debounce delay in milliseconds
     * @return a new DebouncedVirtualExecutor
     */
    public static <T> DebouncedVirtualExecutor<T> createDebouncedExecutor(long delayMs) {
        return new DebouncedVirtualExecutor<>(delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Create a new DebouncedVirtualExecutor with specified delay and error handler.
     * 
     * @param <T> the result type
     * @param delayMs the debounce delay in milliseconds
     * @param errorHandler handler for task errors
     * @return a new DebouncedVirtualExecutor
     */
    public static <T> DebouncedVirtualExecutor<T> createDebouncedExecutor(
            long delayMs,
            DebounceStrategy debounceStrategy,
            Consumer<Throwable> errorHandler) {
        return new DebouncedVirtualExecutor<>(
            delayMs, 
            TimeUnit.MILLISECONDS,
            debounceStrategy, 
            errorHandler
        );
    }
    
}