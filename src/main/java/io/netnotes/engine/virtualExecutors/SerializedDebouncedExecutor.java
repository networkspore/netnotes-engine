package io.netnotes.engine.virtualExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Debounces task submissions to a SerializedVirtualExecutor with completion-aware
 * trailing coalescing.
 *
 * Guarantees:
 * - Only the most recent submitted task executes (trailing behavior)
 * - Debounce timer starts after the currently running task completes
 * - Only one task runs at a time on the target serialized executor
 *
 * Strategies:
 * - TRAILING: fixed-delay trailing debounce
 * - STEPPED_TRAILING: stepped delay (for coalescing bursts) with max-wait cap
 */
public final class SerializedDebouncedExecutor {

    public enum DebounceStrategy {
        TRAILING,
        STEPPED_TRAILING
    }

    private static final long[] DEFAULT_STEP_DELAYS_MS = new long[] {2L, 4L, 8L};
    private static final long DEFAULT_MAX_WAIT_MS = 8L;
    private static final int DEFAULT_SUPERSEDES_PER_STEP = 3;

    private final SerializedVirtualExecutor targetExecutor;
    private final SerializedScheduledVirtualExecutor schedulerExecutor;
    private final long defaultDebounceDelay;
    private final TimeUnit defaultDebounceUnit;
    private final long defaultDebounceDelayMs;
    private final DebounceStrategy strategy;
    private final long[] stepDelaysMs;
    private final long maxWaitMs;
    private final int supersedesPerStep;
    private final Consumer<Throwable> errorHandler;

    private final AtomicReference<PendingTask<?>> pendingTask = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<?>> currentExecution = new AtomicReference<>();
    private final AtomicLong burstStartNanos = new AtomicLong(-1L);
    private final AtomicInteger burstSupersedes = new AtomicInteger(0);
    private final AtomicLong lastScheduledDelayMs = new AtomicLong(0L);

    public SerializedDebouncedExecutor(SerializedVirtualExecutor targetExecutor,
                                      long defaultDebounceDelay,
                                      TimeUnit defaultDebounceUnit) {
        this(
            targetExecutor,
            defaultDebounceDelay,
            defaultDebounceUnit,
            DebounceStrategy.TRAILING,
            DEFAULT_STEP_DELAYS_MS,
            DEFAULT_MAX_WAIT_MS,
            DEFAULT_SUPERSEDES_PER_STEP,
            null
        );
    }

    public SerializedDebouncedExecutor(SerializedVirtualExecutor targetExecutor,
                                      long defaultDebounceDelay,
                                      TimeUnit defaultDebounceUnit,
                                      Consumer<Throwable> errorHandler) {
        this(
            targetExecutor,
            defaultDebounceDelay,
            defaultDebounceUnit,
            DebounceStrategy.TRAILING,
            DEFAULT_STEP_DELAYS_MS,
            DEFAULT_MAX_WAIT_MS,
            DEFAULT_SUPERSEDES_PER_STEP,
            errorHandler
        );
    }

    public SerializedDebouncedExecutor(SerializedVirtualExecutor targetExecutor,
                                      long defaultDebounceDelay,
                                      TimeUnit defaultDebounceUnit,
                                      DebounceStrategy strategy,
                                      Consumer<Throwable> errorHandler) {
        this(
            targetExecutor,
            defaultDebounceDelay,
            defaultDebounceUnit,
            strategy,
            DEFAULT_STEP_DELAYS_MS,
            DEFAULT_MAX_WAIT_MS,
            DEFAULT_SUPERSEDES_PER_STEP,
            errorHandler
        );
    }

    public SerializedDebouncedExecutor(SerializedVirtualExecutor targetExecutor,
                                      long defaultDebounceDelay,
                                      TimeUnit defaultDebounceUnit,
                                      DebounceStrategy strategy,
                                      long[] stepDelaysMs,
                                      long maxWaitMs,
                                      int supersedesPerStep,
                                      Consumer<Throwable> errorHandler) {
        this.targetExecutor = targetExecutor;
        this.schedulerExecutor = new SerializedScheduledVirtualExecutor();
        this.defaultDebounceDelay = defaultDebounceDelay;
        this.defaultDebounceUnit = defaultDebounceUnit;
        this.defaultDebounceDelayMs = toMillisSafe(defaultDebounceDelay, defaultDebounceUnit);
        this.strategy = strategy != null ? strategy : DebounceStrategy.TRAILING;
        this.stepDelaysMs = sanitizeSteps(stepDelaysMs);
        this.maxWaitMs = Math.max(0L, maxWaitMs);
        this.supersedesPerStep = Math.max(1, supersedesPerStep);
        this.errorHandler = errorHandler;
    }

    public CompletableFuture<Void> submit(Runnable task) {
        return submit(task, defaultDebounceDelay, defaultDebounceUnit);
    }

    public CompletableFuture<Void> submit(Runnable task, long debounceDelay, TimeUnit debounceUnit) {
        return submit(() -> {
            task.run();
            return null;
        }, debounceDelay, debounceUnit);
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return submit(task, defaultDebounceDelay, defaultDebounceUnit);
    }

    public <T> CompletableFuture<T> submit(Callable<T> task, long debounceDelay, TimeUnit debounceUnit) {
        CompletableFuture<T> submissionFuture = new CompletableFuture<>();

        long requestedDelayMs = toMillisSafe(debounceDelay, debounceUnit);
        boolean hadPending = pendingTask.get() != null;
        long delayMs = resolveDelayMs(requestedDelayMs, hadPending);

        PendingTask<T> newTask = new PendingTask<>(
            task,
            submissionFuture,
            delayMs,
            TimeUnit.MILLISECONDS
        );

        PendingTask<?> previousTask = pendingTask.getAndSet(newTask);
        if (previousTask != null) {
            cancelPendingTask(previousTask);
        }

        scheduleExecution(newTask);
        return submissionFuture;
    }

    private long resolveDelayMs(long requestedDelayMs, boolean hadPending) {
        long nowNanos = System.nanoTime();

        if (hadPending) {
            burstSupersedes.incrementAndGet();
        } else {
            burstSupersedes.set(0);
            burstStartNanos.set(nowNanos);
        }

        long delayMs;
        if (strategy == DebounceStrategy.STEPPED_TRAILING) {
            delayMs = resolveSteppedDelayMs(requestedDelayMs, nowNanos);
        } else {
            delayMs = requestedDelayMs > 0L ? requestedDelayMs : defaultDebounceDelayMs;
        }

        delayMs = Math.max(0L, delayMs);
        lastScheduledDelayMs.set(delayMs);
        return delayMs;
    }

    private long resolveSteppedDelayMs(long requestedDelayMs, long nowNanos) {
        int supersedes = burstSupersedes.get();
        int idx = Math.min(stepDelaysMs.length - 1, supersedes / supersedesPerStep);

        long steppedDelayMs = stepDelaysMs[idx];

        // Stronger coalescing while target executor already has an active execution.
        CompletableFuture<?> running = currentExecution.get();
        if (running != null && !running.isDone()) {
            steppedDelayMs = stepDelaysMs[stepDelaysMs.length - 1];
        }

        long delayMs = requestedDelayMs > 0L
            ? Math.max(requestedDelayMs, steppedDelayMs)
            : Math.max(defaultDebounceDelayMs, steppedDelayMs);

        if (maxWaitMs <= 0L) {
            return delayMs;
        }

        long burstStart = burstStartNanos.get();
        if (burstStart <= 0L) {
            return delayMs;
        }

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(nowNanos - burstStart);
        long remainingMs = maxWaitMs - elapsedMs;
        if (remainingMs <= 0L) {
            return 0L;
        }

        return Math.min(delayMs, remainingMs);
    }

    private <T> void scheduleExecution(PendingTask<T> task) {
        CompletableFuture<?> currentFuture = currentExecution.get();

        CompletableFuture<Void> waitForCurrent;
        if (currentFuture != null && !currentFuture.isDone()) {
            waitForCurrent = currentFuture.handle((result, throwable) -> null);
        } else {
            waitForCurrent = CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> scheduled = waitForCurrent.thenCompose(v ->
            schedulerExecutor.schedule(() -> {
                executeTaskIfStillPending(task);
            }, task.debounceDelay, task.debounceUnit)
        );

        task.scheduledExecution = scheduled;
    }

    private <T> void executeTaskIfStillPending(PendingTask<T> task) {
        if (!pendingTask.compareAndSet(task, null)) {
            return;
        }

        resetBurst();
        CompletableFuture<T> executionFuture = targetExecutor.submit(task.task);
        currentExecution.set(executionFuture);

        executionFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                if (errorHandler != null) {
                    errorHandler.accept(throwable);
                }
                task.submissionFuture.completeExceptionally(throwable);
            } else {
                task.submissionFuture.complete(result);
            }

            currentExecution.compareAndSet(executionFuture, null);
        });

        task.submissionFuture.whenComplete((result, throwable) -> {
            if (task.submissionFuture.isCancelled()) {
                executionFuture.cancel(true);
            }
        });
    }

    public CompletableFuture<Void> executeNow(Runnable task) {
        return executeNow(() -> {
            task.run();
            return null;
        });
    }

    public <T> CompletableFuture<T> executeNow(Callable<T> task) {
        PendingTask<?> previous = pendingTask.getAndSet(null);
        if (previous != null) {
            cancelPendingTask(previous);
        }

        resetBurst();
        CompletableFuture<T> executionFuture = targetExecutor.submit(task);
        currentExecution.set(executionFuture);

        executionFuture.whenComplete((result, throwable) -> {
            currentExecution.compareAndSet(executionFuture, null);
        });

        return executionFuture;
    }

    private void cancelPendingTask(PendingTask<?> task) {
        if (task.scheduledExecution != null) {
            task.scheduledExecution.cancel(false);
        }
        task.submissionFuture.cancel(false);
    }

    public boolean cancel() {
        PendingTask<?> task = pendingTask.getAndSet(null);
        if (task != null) {
            cancelPendingTask(task);
            resetBurst();
            return true;
        }
        return false;
    }

    public boolean hasPending() {
        PendingTask<?> task = pendingTask.get();
        return task != null && !task.submissionFuture.isDone();
    }

    public boolean isExecuting() {
        CompletableFuture<?> current = currentExecution.get();
        return current != null && !current.isDone();
    }

    public CompletableFuture<?> getCurrentExecution() {
        return currentExecution.get();
    }

    public CompletableFuture<?> getPendingFuture() {
        PendingTask<?> task = pendingTask.get();
        return task != null ? task.submissionFuture : null;
    }

    public DebounceStrategy getStrategy() {
        return strategy;
    }

    public long getLastScheduledDelayMs() {
        return lastScheduledDelayMs.get();
    }

    private void resetBurst() {
        burstStartNanos.set(-1L);
        burstSupersedes.set(0);
    }

    private static long[] sanitizeSteps(long[] input) {
        if (input == null || input.length == 0) {
            return DEFAULT_STEP_DELAYS_MS.clone();
        }

        long[] steps = input.clone();
        for (int i = 0; i < steps.length; i++) {
            steps[i] = Math.max(0L, steps[i]);
            if (i > 0 && steps[i] < steps[i - 1]) {
                steps[i] = steps[i - 1];
            }
        }
        return steps;
    }

    private static long toMillisSafe(long delay, TimeUnit unit) {
        if (delay <= 0L) {
            return 0L;
        }
        TimeUnit resolvedUnit = unit != null ? unit : TimeUnit.MILLISECONDS;
        return TimeUnit.MILLISECONDS.convert(delay, resolvedUnit);
    }

    public void shutdown() {
        schedulerExecutor.shutdown();
    }

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
