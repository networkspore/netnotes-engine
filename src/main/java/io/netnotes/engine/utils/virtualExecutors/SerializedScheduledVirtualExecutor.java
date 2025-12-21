package io.netnotes.engine.utils.virtualExecutors;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import java.util.List;

/**
 * A virtual-thread-based scheduled executor that guarantees serial execution.
 * Tasks execute one at a time in scheduled order (by execution time), with
 * each task completing before the next begins.
 * 
 * Uses virtual threads for lightweight scheduling and blocking, while
 * maintaining strict ordering and serial execution guarantees.
 */
public final class SerializedScheduledVirtualExecutor {

    private final PriorityBlockingQueue<ScheduledTask<?>> queue = new PriorityBlockingQueue<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private final Thread dispatcher;

    public SerializedScheduledVirtualExecutor() {
        dispatcher = Thread.ofVirtual()
            .name("SerialScheduledVT-Dispatcher")
            .start(this::dispatchLoop);
    }

    /**
     * Dispatch loop runs tasks serially in scheduled order.
     * Waits for scheduled time, then executes task to completion.
     */
    private void dispatchLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ScheduledTask<?> task = queue.take();
                
                // Wait until scheduled execution time
                long now = System.nanoTime();
                long delayNanos = task.executeAtNanos - now;
                
                if (delayNanos > 0) {
                    // Sleep until execution time
                    // Virtual thread makes this very cheap
                    Thread.sleep(delayNanos / 1_000_000, (int)(delayNanos % 1_000_000));
                }
                
                // Execute task serially
                runTask(task);
            }
        } catch (InterruptedException e) {
            // Expected during shutdown
        } finally {
            terminated.set(true);
            synchronized (this) {
                this.notifyAll();
            }
        }
    }

    private <T> void runTask(ScheduledTask<T> task) {
        if (task.future.isCancelled()) {
            return;
        }

        try {
            T result = task.callable.call();
            task.future.complete(result);
        } catch (Throwable t) {
            task.future.completeExceptionally(t);
        }
    }

    /**
     * Execute a task immediately (no delay).
     * 
     * @param runnable the task to execute
     * @return a CompletableFuture that completes when the task finishes
     */
    public CompletableFuture<Void> execute(Runnable runnable) {
        return schedule(runnable, 0, TimeUnit.NANOSECONDS);
    }

    /**
     * Schedule a task to execute after a delay.
     * 
     * @param runnable the task to execute
     * @param delay the delay before execution
     * @param unit the time unit of the delay
     * @return a CompletableFuture that completes when the task finishes
     */
    public CompletableFuture<Void> schedule(Runnable runnable, long delay, TimeUnit unit) {
        return schedule(() -> {
            runnable.run();
            return null;
        }, delay, unit);
    }

    /**
     * Schedule a Callable task to execute after a delay.
     * 
     * @param callable the task to execute
     * @param delay the delay before execution
     * @param unit the time unit of the delay
     * @return a CompletableFuture that will contain the task's result
     */
    public <T> CompletableFuture<T> schedule(Callable<T> callable, long delay, TimeUnit unit) {
        CompletableFuture<T> future = new CompletableFuture<>();

        if (shutdown.get()) {
            future.completeExceptionally(
                new CancellationException("Executor is shut down"));
            return future;
        }

        long executeAtNanos = System.nanoTime() + unit.toNanos(delay);
        long sequence = sequenceCounter.getAndIncrement();
        
        ScheduledTask<T> task = new ScheduledTask<>(callable, future, executeAtNanos, sequence);
        queue.add(task);
        
        return future;
    }

    public CompletableFuture<Void> scheduleAtFixedRate(
        Runnable runnable,
        long initialDelay,
        long period,
        TimeUnit unit
    ) {
        return scheduleAtFixedRate(
            Executors.callable(runnable, null),
            initialDelay,
            period,
            unit
        );
    }

    public CompletableFuture<Void> scheduleAtFixedRate(
        Callable<Void> callable,
        long initialDelay,
        long period,
        TimeUnit unit
    ) {
        if (shutdown.get()) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(
                new CancellationException("Executor is shut down"));
            return f;
        }

        FixedRateTask fixed = new FixedRateTask(callable, period, unit);

        long startAt = System.nanoTime() + unit.toNanos(initialDelay);
        fixed.scheduleNext(startAt);

        return fixed.controlFuture;
    }
   

    /**
     * Initiates graceful shutdown. Previously submitted tasks will execute,
     * but no new tasks will be accepted.
     */
    public void shutdown() {
        shutdown.set(true);
    }

    /**
     * Attempts to stop all actively executing tasks and cancels queued tasks.
     * 
     * @return list of tasks that were awaiting execution
     */
    public List<Runnable> shutdownNow() {
        shutdown.set(true);

        // Cancel all queued tasks
        List<Runnable> notExecuted = new ArrayList<>();
        queue.forEach(t -> {
            t.future.cancel(true);
            notExecuted.add(() -> {
                try {
                    t.callable.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        });
        queue.clear();

        dispatcher.interrupt();
        return notExecuted;
    }

    /**
     * Blocks until all tasks have completed after a shutdown request,
     * or the timeout occurs, or the current thread is interrupted.
     * 
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return true if executor terminated, false if timeout elapsed
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        
        synchronized (this) {
            while (!terminated.get()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                this.wait(TimeUnit.NANOSECONDS.toMillis(remaining));
            }
        }
        return true;
    }

    /**
     * Returns true if this executor has been shut down.
     */
    public boolean isShutdown() {
        return shutdown.get();
    }

    /**
     * Returns true if all tasks have completed after shutdown.
     */
    public boolean isTerminated() {
        return terminated.get();
    }

    /**
     * Returns the approximate number of tasks waiting to execute.
     */
    public int getQueueSize() {
        return queue.size();
    }


    private final class FixedRateTask {

        final Callable<Void> callable;
        final long periodNanos;
        final CompletableFuture<Void> controlFuture =
            new CompletableFuture<>();

        FixedRateTask(Callable<Void> callable, long period, TimeUnit unit) {
            this.callable = callable;
            this.periodNanos = unit.toNanos(period);
        }

        void scheduleNext(long executeAtNanos) {
            if (shutdown.get() || controlFuture.isCancelled()) return;

            long sequence = sequenceCounter.getAndIncrement();

            ScheduledTask<Void> task = new ScheduledTask<>(
                () -> {
                    if (controlFuture.isCancelled()) {
                        throw new CancellationException();
                    }

                    callable.call();

                    long next = executeAtNanos + periodNanos;
                    scheduleNext(next);
                    return null;
                },
                new CompletableFuture<>(),
                executeAtNanos,
                sequence
            );

            queue.add(task);
        }
    }
}
