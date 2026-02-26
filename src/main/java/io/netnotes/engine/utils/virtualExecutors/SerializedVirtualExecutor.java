package io.netnotes.engine.utils.virtualExecutors;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netnotes.engine.utils.LoggingHelpers.Log;

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

    


    private final BlockingQueue<SerializedTask<?>> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final Thread dispatcher;

    private final ThreadLocal<Boolean> onDispatcherThread = ThreadLocal.withInitial(() -> false);
    

    public SerializedVirtualExecutor() {
        dispatcher = Thread.ofVirtual().name("SerialVT-Dispatcher").start(this::dispatchLoop);
    }

    public boolean isCurrentThread(){
        return onDispatcherThread.get();
    }

    /**
     * Dispatch loop runs tasks serially - each task completes before the next starts.
     */
    private void dispatchLoop() {
        onDispatcherThread.set(true);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                SerializedTask<?> task = queue.take();
                runTask(task);
            }
        } catch (InterruptedException e) {
            // Expected during shutdown
        } finally {
            onDispatcherThread.set(false);
            terminated.set(true);
            synchronized (this) {
                this.notifyAll();
            }
        }
    }


    private <T> void runTask(SerializedTask<T> task) {
        CompletableFuture<T> f = task.getFuture();
        if(f != null && f.isCancelled()){
            return;
        }
        if(f == null){
            try{
                task.call();
            }catch(Exception e){
                Log.logError("[SerializedVirtualExecutor]", "runTask", e);
            }
        }else{
            try {
                T result = task.call();
                f.complete(result);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        }
    }

    /**
     * Submits a Runnable task for serial execution.
     * If already on dispatcher thread, executes immediately (reentrant).
     * 
     * @param runnable the task to execute
     * @return a CompletableFuture that completes when the task finishes
     */
    public CompletableFuture<Void> execute(Runnable runnable) {

        if (onDispatcherThread.get()) {
            /* Only defer fire and forget methods
                if (deferDepth.get() > 0) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                deferredQueue.add(() -> {
                    try {
                        runnable.run();
                        future.complete(null);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
                return future;
            }*/


            CompletableFuture<Void> future = new CompletableFuture<>();
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }

            return future;
        }
        

        return submit(runnable, null);
    }

   
     public void executeFireAndForget(Runnable runnable) {
        if (onDispatcherThread.get()) {
            try{
                runnable.run();
            }catch(Exception e){
                Log.logError("[SerializedVirtualExecutor]", "executeFireAndForget", e);
            }
            return;
        }
        
        if (shutdown.get()) {
            return; // Just drop it
        }
        
        queue.add(new SimpleTask(runnable));
    }

    public <T> CompletableFuture<T> submit(Callable<T> callable) {
 
        if (onDispatcherThread.get()) {
            /* don't defer methods with return values 
                if (deferDepth.get() > 0) {
                CompletableFuture<T> future = new CompletableFuture<>();
                deferredQueue.add(() -> {
                    try {
                        future.complete(callable.call());
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
                return future;
            }*/
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                T result = callable.call();
                future.complete(result);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            return future;
        }
        
 
        CompletableFuture<T> future = new CompletableFuture<>();

        if (shutdown.get()) {
            future.completeExceptionally(
                new CancellationException("Executor is shut down"));
            return future;
        }

        queue.add(new SerializedTask<T>(callable, future));
        return future;
    }

    public <T> CompletableFuture<T> submit(Runnable runnable, T result) {

        if (onDispatcherThread.get()) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                runnable.run();
                future.complete(result);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            return future;
        }
        
        return submit(() -> {
            runnable.run();
            return result;
        });
    }

    public void executeBatch(List<Runnable> tasks) {
        execute(() -> {
            for (Runnable task : tasks) {
                task.run();
            }
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
    public void shutdownNow() {
        shutdown.set(true);

     
         queue.forEach(t->{
            if(t != null){
                t.cancel();
            }
        });

        queue.clear();

        dispatcher.interrupt();
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