package io.netnotes.engine.utils.virtualExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A debounced executor with configurable execution strategies.
 * 
 * Strategies:
 * - TRAILING: Execute only the last task after quiet period (default)
 * - LEADING: Execute first task immediately, suppress subsequent until quiet period
 * - HYBRID: Execute first immediately AND last after quiet period
 * 
 * Example:
 * <pre>
 * // Trailing (default): only last call executes after delay
 * var trailing = new DebouncedVirtualExecutor&lt;&gt;(100, TimeUnit.MILLISECONDS);
 * 
 * // Leading: first call executes immediately, others suppressed
 * var leading = new DebouncedVirtualExecutor&lt;&gt;(
 *     100, TimeUnit.MILLISECONDS, 
 *     DebounceStrategy.LEADING
 * );
 * 
 * // Hybrid: first executes immediately, last executes after delay
 * var hybrid = new DebouncedVirtualExecutor&lt;&gt;(
 *     100, TimeUnit.MILLISECONDS,
 *     DebounceStrategy.HYBRID
 * );
 * </pre>
 */
public final class DebouncedVirtualExecutor<T> {

    public enum DebounceStrategy {
        /** Execute only the last task after quiet period */
        TRAILING,
        /** Execute first task immediately, suppress subsequent until quiet period */
        LEADING,
        /** Execute first task immediately AND last task after quiet period */
        HYBRID
    }

    private final SerializedScheduledVirtualExecutor executor;
    private final long debounceDelay;
    private final TimeUnit debounceUnit;
    private final DebounceStrategy strategy;
    private final AtomicReference<PendingTask<T>> pendingTask = new AtomicReference<>();
    private final Consumer<Throwable> errorHandler;
    
    // For LEADING and HYBRID: track if we're in cooldown period
    private volatile long lastExecutionTime = 0;

    /**
     * Create a debounced executor with TRAILING strategy.
     */
    public DebouncedVirtualExecutor(long debounceDelay, TimeUnit debounceUnit) {
        this(debounceDelay, debounceUnit, DebounceStrategy.TRAILING, null);
    }

    /**
     * Create a debounced executor with specified strategy.
     */
    public DebouncedVirtualExecutor(long debounceDelay, TimeUnit debounceUnit, 
                                   DebounceStrategy strategy) {
        this(debounceDelay, debounceUnit, strategy, null);
    }

    /**
     * Create a debounced executor with specified strategy and error handler.
     */
    public DebouncedVirtualExecutor(long debounceDelay, TimeUnit debounceUnit, 
                                   DebounceStrategy strategy,
                                   Consumer<Throwable> errorHandler) {
        this.executor = new SerializedScheduledVirtualExecutor();
        this.debounceDelay = debounceDelay;
        this.debounceUnit = debounceUnit;
        this.strategy = strategy;
        this.errorHandler = errorHandler;
    }

    /**
     * Submit a task for debounced execution according to the configured strategy.
     */
    public CompletableFuture<T> submit(Callable<T> task) {
        long now = System.nanoTime();
        long debounceNanos = debounceUnit.toNanos(debounceDelay);
        boolean inCooldown = (now - lastExecutionTime) < debounceNanos;

        switch (strategy) {
            case LEADING:
                return submitLeading(task, inCooldown, now, debounceNanos);
            case HYBRID:
                return submitHybrid(task, inCooldown, now, debounceNanos);
            case TRAILING:
            default:
                return submitTrailing(task);
        }
    }

    private CompletableFuture<T> submitTrailing(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        CompletableFuture<Void> scheduled = executor.schedule(() -> {
            PendingTask<T> current = pendingTask.getAndSet(null);
            if (current == null) return;

            try {
                T result = current.task.call();
                current.future.complete(result);
            } catch (Throwable t) {
                if (errorHandler != null) errorHandler.accept(t);
                current.future.completeExceptionally(t);
            }
        }, debounceDelay, debounceUnit);

        PendingTask<T> next = new PendingTask<>(task, future, scheduled);
        PendingTask<T> previous = pendingTask.getAndSet(next);

        if (previous != null) {
            previous.scheduledFuture.cancel(false);
            previous.future.cancel(false);
        }

        return future;
    }

    private CompletableFuture<T> submitLeading(Callable<T> task, boolean inCooldown, 
                                               long now, long debounceNanos) {
        if (!inCooldown) {
            // Execute immediately - we're not in cooldown
            lastExecutionTime = now;
            CompletableFuture<T> future = new CompletableFuture<>();
            
            executor.execute(() -> {
                try {
                    future.complete(task.call());
                } catch (Throwable t) {
                    if (errorHandler != null) errorHandler.accept(t);
                    future.completeExceptionally(t);
                }
            });
            
            // Schedule cooldown reset
            executor.schedule(() -> {
                // Cooldown period ended
            }, debounceDelay, debounceUnit);
            
            return future;
        } else {
            // In cooldown - suppress this call
            // Return a cancelled future to indicate suppression
            CompletableFuture<T> future = new CompletableFuture<>();
            future.cancel(false);
            return future;
        }
    }

    private CompletableFuture<T> submitHybrid(Callable<T> task, boolean inCooldown,
                                             long now, long debounceNanos) {
        CompletableFuture<T> future = new CompletableFuture<>();

        if (!inCooldown) {
            // Execute immediately - this is the first in a burst
            lastExecutionTime = now;
            
            executor.execute(() -> {
                try {
                    future.complete(task.call());
                } catch (Throwable t) {
                    if (errorHandler != null) errorHandler.accept(t);
                    future.completeExceptionally(t);
                }
            });
        }

        // Always schedule the trailing execution (cancel previous if exists)
        CompletableFuture<Void> scheduled = executor.schedule(() -> {
            PendingTask<T> current = pendingTask.getAndSet(null);
            if (current == null) return;

            // Only execute if this wasn't the leading edge execution
            if (inCooldown) {
                try {
                    T result = current.task.call();
                    current.future.complete(result);
                } catch (Throwable t) {
                    if (errorHandler != null) errorHandler.accept(t);
                    current.future.completeExceptionally(t);
                }
            }
        }, debounceDelay, debounceUnit);

        // For hybrid: if we executed immediately, return that future
        // Otherwise, set up trailing execution
        if (inCooldown) {
            PendingTask<T> next = new PendingTask<>(task, future, scheduled);
            PendingTask<T> previous = pendingTask.getAndSet(next);

            if (previous != null) {
                previous.scheduledFuture.cancel(false);
                previous.future.cancel(false);
            }
        } else {
            // Leading edge executed, but still track for potential trailing
            PendingTask<T> next = new PendingTask<>(task, new CompletableFuture<>(), scheduled);
            pendingTask.set(next);
        }

        return future;
    }

    public CompletableFuture<T> submit(Runnable task) {
        return submit(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Execute a task immediately, bypassing debounce.
     * Cancels any pending debounced task.
     */
    public CompletableFuture<T> executeNow(Callable<T> task) {
        PendingTask<T> previous = pendingTask.getAndSet(null);
        if (previous != null) {
            previous.scheduledFuture.cancel(false);
            previous.future.cancel(false);
        }

        lastExecutionTime = System.nanoTime();
        CompletableFuture<T> future = new CompletableFuture<>();
        
        executor.execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                if (errorHandler != null) errorHandler.accept(t);
                future.completeExceptionally(t);
            }
        });

        return future;
    }

    /**
     * Cancel any pending task.
     */
    public boolean cancel() {
        PendingTask<T> task = pendingTask.getAndSet(null);
        if (task != null) {
            task.scheduledFuture.cancel(true);
            task.future.cancel(true);
            return true;
        }
        return false;
    }

    /**
     * Check if there is a pending task waiting to execute.
     */
    public boolean hasPending() {
        PendingTask<T> task = pendingTask.get();
        return task != null && !task.future.isDone();
    }

    /**
     * Get the current pending task's future (if any).
     */
    public CompletableFuture<T> getPendingFuture() {
        PendingTask<T> task = pendingTask.get();
        return task != null ? task.future : null;
    }

    /**
     * Get the configured strategy.
     */
    public DebounceStrategy getStrategy() {
        return strategy;
    }

    public void shutdown() {
        executor.shutdown();
    }

    public boolean shutdownNow() {
        boolean hadPending = cancel();
        executor.shutdownNow();
        return hadPending;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    public boolean isTerminated() {
        return executor.isTerminated();
    }

    private static class PendingTask<T> {
        final Callable<T> task;
        final CompletableFuture<T> future;
        final CompletableFuture<Void> scheduledFuture;

        PendingTask(Callable<T> task, CompletableFuture<T> future, 
                   CompletableFuture<Void> scheduledFuture) {
            this.task = task;
            this.future = future;
            this.scheduledFuture = scheduledFuture;
        }
    }
}