package io.netnotes.engine.utils.virtualExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A debounced executor that ensures only the final task in a series of rapid
 * submissions is executed, after a quiet period.
 * 
 * Pattern:
 * - Multiple rapid submissions cancel previous pending tasks
 * - Only the last submission executes after the debounce delay
 * - All submissions return the same future that completes with the final result
 * 
 * Example:
 * <pre>
 * DebouncedVirtualExecutor&lt;Void&gt; resizer = new DebouncedVirtualExecutor&lt;&gt;(100, TimeUnit.MILLISECONDS);
 * 
 * // Rapid calls - only last one executes
 * resizer.submit(() -&gt; resize(80, 24));   // Canceled
 * resizer.submit(() -&gt; resize(90, 30));   // Canceled
 * resizer.submit(() -&gt; resize(100, 40));  // Executes after 100ms
 * </pre>
 */
public final class DebouncedVirtualExecutor<T> {

    private final SerializedScheduledVirtualExecutor executor;
    private final long debounceDelay;
    private final TimeUnit debounceUnit;
    private final AtomicReference<PendingTask<T>> pendingTask = new AtomicReference<>();
    private final Consumer<Throwable> errorHandler;

    /**
     * Create a debounced executor with specified delay.
     * 
     * @param debounceDelay the delay after last submission before execution
     * @param debounceUnit the time unit of the delay
     */
    public DebouncedVirtualExecutor(long debounceDelay, TimeUnit debounceUnit) {
        this(debounceDelay, debounceUnit, null);
    }

    /**
     * Create a debounced executor with specified delay and error handler.
     * 
     * @param debounceDelay the delay after last submission before execution
     * @param debounceUnit the time unit of the delay
     * @param errorHandler optional handler for task errors (null = propagate to future)
     */
    public DebouncedVirtualExecutor(long debounceDelay, TimeUnit debounceUnit, 
                                   Consumer<Throwable> errorHandler) {
        this.executor = new SerializedScheduledVirtualExecutor();
        this.debounceDelay = debounceDelay;
        this.debounceUnit = debounceUnit;
        this.errorHandler = errorHandler;
    }

    /**
     * Submit a task for debounced execution.
     * 
     * Behavior:
     * - Cancels any previously pending task
     * - Schedules new task to execute after debounce delay
     * - Returns a future that completes when task executes (or is canceled)
     * 
     * @param task the task to execute
     * @return a CompletableFuture that completes with the result
     */
    public CompletableFuture<T> submit(Callable<T> task) {
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

    public CompletableFuture<T> submit(Runnable task) {
        return submit(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Execute a task immediately, bypassing debounce.
     * Cancels any pending debounced task.
     * 
     * @param task the task to execute
     * @return a CompletableFuture that completes when task finishes
     */
   public CompletableFuture<T> executeNow(Callable<T> task) {
        PendingTask<T> previous = pendingTask.getAndSet(null);
        if (previous != null) {
            previous.scheduledFuture.cancel(false);
            previous.future.cancel(false);
        }

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
     * 
     * @return true if a task was canceled, false if no task was pending
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
     * 
     * @return true if a task is pending
     */
    public boolean hasPending() {
        PendingTask<T> task = pendingTask.get();
        return task != null && !task.future.isDone();
    }

    /**
     * Get the current pending task's future (if any).
     * 
     * @return the pending future, or null if no task is pending
     */
    public CompletableFuture<T> getPendingFuture() {
        PendingTask<T> task = pendingTask.get();
        return task != null ? task.future : null;
    }

    /**
     * Shutdown the executor gracefully.
     * Any pending task will still execute.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Shutdown the executor immediately.
     * Cancels any pending task.
     * 
     * @return true if a task was canceled
     */
    public boolean shutdownNow() {
        boolean hadPending = cancel();
        executor.shutdownNow();
        return hadPending;
    }

    /**
     * Wait for executor to terminate.
     * 
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout
     * @return true if terminated, false if timeout elapsed
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    /**
     * Check if executor is shut down.
     */
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    /**
     * Check if executor is terminated.
     */
    public boolean isTerminated() {
        return executor.isTerminated();
    }
}
