package io.netnotes.engine.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class VirtualExecutors {
    private static final ExecutorService virtualExecutor =  Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService virtualSchedualed  = Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());

    public static ExecutorService getVirtualExecutor(){ return virtualExecutor; }
    public static ScheduledExecutorService getVirtualSchedualedExecutor() { return virtualSchedualed; }
    
}