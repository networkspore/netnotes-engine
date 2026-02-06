package io.netnotes.engine.utils.virtualExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Debounces task submissions to a SerializedVirtualExecutor with completion-aware debouncing.
 * 
 * This executor ensures:
 * - Only the most recent submitted task executes (trailing debounce)
 * - Minimum time between task completions (not just submissions)
 * - Only one task runs on the underlying executor at a time
 * - New submissions reset the debounce timer
 * 
 * The debounce timer starts counting down AFTER the previous task completes on the
 * underlying executor, ensuring proper spacing between actual executions.
 * 
 * Example:
 * <pre>
 * var serialExecutor = new SerializedVirtualExecutor();
 * var debounced = new SerializedDebouncedExecutor(serialExecutor, 500, TimeUnit.MILLISECONDS);
 * 
 * // Rapid submissions - only last one executes after 500ms quiet period
 * debounced.submit(() -&gt; System.out.println("Task 1"));
 * debounced.submit(() -&gt; System.out.println("Task 2"));
 * debounced.submit(() -&gt; System.out.println("Task 3")); // Only this executes
 * </pre>
 */
public final class SerializedDebouncedExecutor {

    private final SerializedVirtualExecutor targetExecutor;
    private final SerializedScheduledVirtualExecutor schedulerExecutor;
    private final long defaultDebounceDelay;
    private final TimeUnit defaultDebounceUnit;
    private final Consumer<Throwable> errorHandler;
    
    private final AtomicReference<PendingTask<?>> pendingTask = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<?>> currentExecution = new AtomicReference<>();

    /**
     * Create a debounced wrapper for a SerializedVirtualExecutor.
     * 
     * @param targetExecutor the executor to run tasks on
     * @param defaultDebounceDelay default debounce delay
     * @param defaultDebounceUnit time unit for default delay
     */
    public SerializedDebouncedExecutor(SerializedVirtualExecutor targetExecutor,
                                      long defaultDebounceDelay,
                                      TimeUnit defaultDebounceUnit) {
        this(targetExecutor, defaultDebounceDelay, defaultDebounceUnit, null);
    }

    /**
     * Create a debounced wrapper with error handler.
     * 
     * @param targetExecutor the executor to run tasks on
     * @param defaultDebounceDelay default debounce delay
     * @param defaultDebounceUnit time unit for default delay
     * @param errorHandler called when task execution throws an exception
     */
    public SerializedDebouncedExecutor(SerializedVirtualExecutor targetExecutor,
                                      long defaultDebounceDelay,
                                      TimeUnit defaultDebounceUnit,
                                      Consumer<Throwable> errorHandler) {
        this.targetExecutor = targetExecutor;
        this.schedulerExecutor = new SerializedScheduledVirtualExecutor();
        this.defaultDebounceDelay = defaultDebounceDelay;
        this.defaultDebounceUnit = defaultDebounceUnit;
        this.errorHandler = errorHandler;
    }

    /**
     * Submit a Runnable task with default debounce delay.
     */
    public CompletableFuture<Void> submit(Runnable task) {
        return submit(task, defaultDebounceDelay, defaultDebounceUnit);
    }

    /**
     * Submit a Runnable task with custom debounce delay.
     * 
     * @param task the task to execute
     * @param debounceDelay debounce delay for this submission
     * @param debounceUnit time unit for the delay
     * @return future that completes when the task finishes executing
     */
    public CompletableFuture<Void> submit(Runnable task, long debounceDelay, TimeUnit debounceUnit) {
        return submit(() -> {
            task.run();
            return null;
        }, debounceDelay, debounceUnit);
    }

    /**
     * Submit a Callable task with default debounce delay.
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return submit(task, defaultDebounceDelay, defaultDebounceUnit);
    }

    /**
     * Submit a Callable task with custom debounce delay.
     */
    public <T> CompletableFuture<T> submit(Callable<T> task, long debounceDelay, TimeUnit debounceUnit) {
        CompletableFuture<T> submissionFuture = new CompletableFuture<>();
        
        // Create pending task with typed future
        PendingTask<T> newTask = new PendingTask<>(task, submissionFuture, debounceDelay, debounceUnit);
        PendingTask<?> previousTask = pendingTask.getAndSet(newTask);
        
        // Cancel previous pending task
        if (previousTask != null) {
            cancelPendingTask(previousTask);
        }
        
        // Schedule execution after current task completes + debounce delay
        scheduleExecution(newTask);
        
        return submissionFuture;
    }

    private <T> void scheduleExecution(PendingTask<T> task) {
        // Get the currently executing task's future (if any)
        CompletableFuture<?> currentFuture = currentExecution.get();
        
        // Create a future that waits for current execution to complete
        CompletableFuture<Void> waitForCurrent;
        if (currentFuture != null && !currentFuture.isDone()) {
            // Wait for current task to finish (ignore its result/error)
            waitForCurrent = currentFuture.handle((result, throwable) -> null);
        } else {
            // No current task, can proceed immediately
            waitForCurrent = CompletableFuture.completedFuture(null);
        }
        
        // Schedule execution: wait for current + debounce delay
        CompletableFuture<Void> scheduled = waitForCurrent.thenCompose(v ->
            schedulerExecutor.schedule(() -> {
                executeTaskIfStillPending(task);
            }, task.debounceDelay, task.debounceUnit)
        );
        
        task.scheduledExecution = scheduled;
    }

    private <T> void executeTaskIfStillPending(PendingTask<T> task) {
        // Only execute if this is still the pending task (wasn't superseded)
        if (!pendingTask.compareAndSet(task, null)) {
            return;
        }
        
        // Execute on the target SerializedVirtualExecutor
        CompletableFuture<T> executionFuture = targetExecutor.submit(task.task);
        
        // Track this as the current execution
        currentExecution.set(executionFuture);
        
        // Wire up completion - this is now clean and symmetric
        executionFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                if (errorHandler != null) {
                    errorHandler.accept(throwable);
                }
                task.submissionFuture.completeExceptionally(throwable);
            } else {
                task.submissionFuture.complete(result);
            }
            
            // Clear current execution when done
            currentExecution.compareAndSet(executionFuture, null);
        });
        
        // Handle cancellation symmetrically
        task.submissionFuture.whenComplete((result, throwable) -> {
            if (task.submissionFuture.isCancelled()) {
                executionFuture.cancel(true);
            }
        });
    }

    /**
     * Execute a Runnable immediately on the target executor, bypassing debounce.
     * Cancels any pending debounced task.
     */
    public CompletableFuture<Void> executeNow(Runnable task) {
        return executeNow(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Execute a Callable immediately on the target executor, bypassing debounce.
     */
    public <T> CompletableFuture<T> executeNow(Callable<T> task) {
        // Cancel pending task
        PendingTask<?> previous = pendingTask.getAndSet(null);
        if (previous != null) {
            cancelPendingTask(previous);
        }

        // Execute immediately on target executor
        CompletableFuture<T> executionFuture = targetExecutor.submit(task);
        currentExecution.set(executionFuture);
        
        // Clear current execution when done
        executionFuture.whenComplete((result, throwable) -> {
            currentExecution.compareAndSet(executionFuture, null);
        });
        
        return executionFuture;
    }

    /**
     * Cancel a pending task cleanly.
     */
    private void cancelPendingTask(PendingTask<?> task) {
        if (task.scheduledExecution != null) {
            task.scheduledExecution.cancel(false);
        }
        task.submissionFuture.cancel(false);
    }

    /**
     * Cancel any pending task. Does not cancel currently executing task.
     */
    public boolean cancel() {
        PendingTask<?> task = pendingTask.getAndSet(null);
        if (task != null) {
            cancelPendingTask(task);
            return true;
        }
        return false;
    }

    /**
     * Check if there is a pending task waiting to execute.
     */
    public boolean hasPending() {
        PendingTask<?> task = pendingTask.get();
        return task != null && !task.submissionFuture.isDone();
    }

    /**
     * Check if a task is currently executing on the target executor.
     */
    public boolean isExecuting() {
        CompletableFuture<?> current = currentExecution.get();
        return current != null && !current.isDone();
    }

    /**
     * Get the future for the currently executing task (if any).
     */
    public CompletableFuture<?> getCurrentExecution() {
        return currentExecution.get();
    }

    /**
     * Get the future for the pending task (if any).
     */
    public CompletableFuture<?> getPendingFuture() {
        PendingTask<?> task = pendingTask.get();
        return task != null ? task.submissionFuture : null;
    }

    /**
     * Shutdown this debouncer and its scheduler (does not shutdown target executor).
     */
    public void shutdown() {
        schedulerExecutor.shutdown();
    }

    /**
     * Shutdown immediately, canceling pending tasks.
     * @return true if there was a pending task
     */
    public boolean shutdownNow() {
        boolean hadPending = cancel();
        schedulerExecutor.shutdownNow();
        return hadPending;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return schedulerExecutor.awaitTermination(timeout, unit);
    }

    public boolean isShutdown() {
        return schedulerExecutor.isShutdown();
    }

    public boolean isTerminated() {
        return schedulerExecutor.isTerminated();
    }

    /**
     * Get the target executor this debouncer wraps.
     */
    public SerializedVirtualExecutor getTargetExecutor() {
        return targetExecutor;
    }

    private static class PendingTask<T> {
        final Callable<T> task;
        final CompletableFuture<T> submissionFuture;
        final long debounceDelay;
        final TimeUnit debounceUnit;
        volatile CompletableFuture<Void> scheduledExecution;

        PendingTask(Callable<T> task, CompletableFuture<T> submissionFuture,
                   long debounceDelay, TimeUnit debounceUnit) {
            this.task = task;
            this.submissionFuture = submissionFuture;
            this.debounceDelay = debounceDelay;
            this.debounceUnit = debounceUnit;
        }
    }
}