package io.netnotes.engine.utils.virtualExecutors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class VirtualExecutors {
    
    /**
     * Get the shared virtual thread executor.
     * Use getSerializedVirtualExecutor() for ordered execution
     */
    public static ExecutorService getVirtualExecutor() { 
        return Executors.newVirtualThreadPerTaskExecutor(); 
    }

    /**
     * Get the shared scheduled virtual thread executor.
     * Use getSerializedScheduledVirtualExecutor() for ordered execution
     */
    public static ScheduledExecutorService getVirtualScheduledExecutor() { 
        return Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());
    }
    
    /**
     * Get the shared serialized virtual executor.
     * Guarantees serial execution order - tasks execute one at a time.
     * 
     * @return the shared SerializedVirtualExecutor
     */
    public static SerializedVirtualExecutor getSerializedVirtualExecutor() {
        return new SerializedVirtualExecutor();
    }
    
    /**
     * Get the shared serialized scheduled virtual executor.
     * Guarantees serial execution order with scheduling support.
     * 
     * @return the shared SerializedScheduledVirtualExecutor
     */
    public static SerializedScheduledVirtualExecutor getSerializedScheduledVirtualExecutor() {
        return new SerializedScheduledVirtualExecutor();
    }
    
    /**
     * Create a new DebouncedVirtualExecutor with specified delay.
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
            Consumer<Throwable> errorHandler) {
        return new DebouncedVirtualExecutor<>(
            delayMs, 
            TimeUnit.MILLISECONDS, 
            errorHandler
        );
    }
    
}