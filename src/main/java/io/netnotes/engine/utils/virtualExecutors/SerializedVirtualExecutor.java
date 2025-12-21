package io.netnotes.engine.utils.virtualExecutors;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;

/**
 * A virtual-thread-based executor that guarantees serial execution semantics.
 * Tasks are executed one at a time in submission order, with each task
 * completing before the next begins.
 * 
 * Unlike traditional single-threaded executors, this uses virtual threads
 * for lightweight blocking operations while maintaining ordering guarantees.
 */
public final class SerializedVirtualExecutor {

    private static final class Task<T> {
        final Callable<T> callable;
        final CompletableFuture<T> future;

        Task(Callable<T> callable, CompletableFuture<T> future) {
            this.callable = callable;
            this.future = future;
        }
    }

    private final BlockingQueue<Task<?>> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final Thread dispatcher;

    public SerializedVirtualExecutor() {
        dispatcher = Thread.ofVirtual().name("SerialVT-Dispatcher").start(this::dispatchLoop);
    }

    /**
     * Dispatch loop runs tasks serially - each task completes before the next starts.
     */
    private void dispatchLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Task<?> task = queue.take();
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

    private <T> void runTask(Task<T> task) {
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
     * Submits a Runnable task for serial execution.
     * 
     * @param runnable the task to execute
     * @return a CompletableFuture that completes when the task finishes
     */
    public CompletableFuture<Void> execute(Runnable runnable) {
        return submit(runnable, null);
    }

    /**
     * Submits a Callable task for serial execution.
     * 
     * @param callable the task to execute
     * @return a CompletableFuture that will contain the task's result
     */
    public <T> CompletableFuture<T> submit(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();

        if (shutdown.get()) {
            future.completeExceptionally(
                new CancellationException("Executor is shut down"));
            return future;
        }

        queue.add(new Task<>(callable, future));
        return future;
    }

    /**
     * Submits a Runnable task with a result value.
     * 
     * @param runnable the task to execute
     * @param result the result to return upon successful completion
     * @return a CompletableFuture that will contain the result
     */
    public <T> CompletableFuture<T> submit(Runnable runnable, T result) {
        return submit(() -> {
            runnable.run();
            return result;
        });
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
}