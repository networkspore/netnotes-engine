package io.netnotes.engine.memory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Background monitor for memory pressure with configurable callbacks.
 * 
 * Features:
 * - Periodic sampling of heap usage
 * - Pressure level detection (LOW/MEDIUM/HIGH/CRITICAL)
 * - Listener pattern for reactive components
 * - Optional automatic GC triggering at CRITICAL level
 */
public class MemoryPressureMonitor {
    
    private static final MemoryPressureMonitor INSTANCE = new MemoryPressureMonitor();
    
    public enum PressureLevel {
        LOW(0.0, 0.60),
        MEDIUM(0.60, 0.75),
        HIGH(0.75, 0.85),
        CRITICAL(0.85, 1.0);
        
        final double minThreshold;
        final double maxThreshold;
        
        PressureLevel(double min, double max) {
            this.minThreshold = min;
            this.maxThreshold = max;
        }
        
        static PressureLevel fromUsage(double usage) {
            for (PressureLevel level : values()) {
                if (usage >= level.minThreshold && usage < level.maxThreshold) {
                    return level;
                }
            }
            return CRITICAL; // Default to worst case
        }
    }
    
    public interface PressureListener {
        void onPressureChanged(PressureLevel oldLevel, PressureLevel newLevel, double usage);
    }
    
    private final MemoryMXBean memoryBean;
    private final List<PressureListener> listeners;
    private ScheduledExecutorService scheduler;
    
    private PressureLevel currentLevel = PressureLevel.LOW;
    private double smoothedUsage = 0.0;
    private boolean autoGcEnabled = true;
    private long lastGcTime = 0;
    private static final long MIN_GC_INTERVAL_MS = 5000; // Don't GC more than once per 5s
    
    // Statistics
    private long sampleCount = 0;
    private long pressureChanges = 0;
    private long autoGcTriggers = 0;
    
    private MemoryPressureMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.listeners = new CopyOnWriteArrayList<>();
    }
    
    public static MemoryPressureMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * Check if monitoring is running
     */
    public synchronized boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }
    
    /**
     * Start monitoring with specified interval
     */
    public synchronized void start(long intervalMs) {
        if (isRunning()) {
            return; // Already running
        }
        
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MemoryPressureMonitor");
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleAtFixedRate(
            this::sample,
            0,
            intervalMs,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Start with default 500ms interval
     */
    public void start() {
        start(500);
    }
    
    /**
     * Stop monitoring
     */
    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
            scheduler = null;
        }
    }
    
    /**
     * Sample memory and update pressure level
     */
    private void sample() {
        try {
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            long used = heapUsage.getUsed();
            long max = heapUsage.getMax();
            
            double currentUsage = (double) used / max;
            
            // Smooth the value
            if (smoothedUsage == 0.0) {
                smoothedUsage = currentUsage;
            } else {
                smoothedUsage = 0.2 * currentUsage + 0.8 * smoothedUsage;
            }
            
            sampleCount++;
            
            // Determine pressure level
            PressureLevel newLevel = PressureLevel.fromUsage(smoothedUsage);
            
            // Notify if changed
            if (newLevel != currentLevel) {
                PressureLevel oldLevel = currentLevel;
                currentLevel = newLevel;
                pressureChanges++;
                
                notifyListeners(oldLevel, newLevel, smoothedUsage);
                
                // Auto-GC at critical level
                if (autoGcEnabled && newLevel == PressureLevel.CRITICAL) {
                    long now = System.currentTimeMillis();
                    if (now - lastGcTime > MIN_GC_INTERVAL_MS) {
                        System.gc();
                        lastGcTime = now;
                        autoGcTriggers++;
                    }
                }
            }
            
        } catch (Exception e) {
            Log.logError("Error sampling memory: " + e.getMessage());
        }
    }
    
    /**
     * Notify all listeners of pressure change
     */
    private void notifyListeners(PressureLevel oldLevel, PressureLevel newLevel, double usage) {
        for (PressureListener listener : listeners) {
            try {
                listener.onPressureChanged(oldLevel, newLevel, usage);
            } catch (Exception e) {
                Log.logError("Error notifying pressure listener: " + e.getMessage());
            }
        }
    }
    
    /**
     * Add a pressure listener
     */
    public void addListener(PressureListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove a pressure listener
     */
    public void removeListener(PressureListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Get current pressure level
     */
    public PressureLevel getCurrentLevel() {
        return currentLevel;
    }
    
    /**
     * Get smoothed memory usage (0.0 to 1.0)
     */
    public double getSmoothedUsage() {
        return smoothedUsage;
    }
    
    /**
     * Enable/disable automatic GC at critical pressure
     */
    public void setAutoGcEnabled(boolean enabled) {
        this.autoGcEnabled = enabled;
    }
    
    /**
     * Get statistics
     */
    public MonitorStats getStats() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        return new MonitorStats(
            currentLevel,
            smoothedUsage,
            (double) heapUsage.getUsed() / heapUsage.getMax(),
            heapUsage.getUsed(),
            heapUsage.getMax(),
            sampleCount,
            pressureChanges,
            autoGcTriggers,
            listeners.size()
        );
    }
    
    /**
     * Statistics about the monitor
     */
    public static class MonitorStats {
        public final PressureLevel currentLevel;
        public final double smoothedUsage;
        public final double instantUsage;
        public final long usedBytes;
        public final long maxBytes;
        public final long sampleCount;
        public final long pressureChanges;
        public final long autoGcTriggers;
        public final int listenerCount;
        
        MonitorStats(
            PressureLevel currentLevel,
            double smoothedUsage,
            double instantUsage,
            long usedBytes,
            long maxBytes,
            long sampleCount,
            long pressureChanges,
            long autoGcTriggers,
            int listenerCount
        ) {
            this.currentLevel = currentLevel;
            this.smoothedUsage = smoothedUsage;
            this.instantUsage = instantUsage;
            this.usedBytes = usedBytes;
            this.maxBytes = maxBytes;
            this.sampleCount = sampleCount;
            this.pressureChanges = pressureChanges;
            this.autoGcTriggers = autoGcTriggers;
            this.listenerCount = listenerCount;
        }
        
        @Override
        public String toString() {
            return String.format(
                "MemoryMonitor[level=%s, memory=%.1f%% (smooth), %.1f%% (instant), " +
                "used=%dMB, max=%dMB, samples=%d, changes=%d, gcTriggers=%d, listeners=%d]",
                currentLevel.name(),
                smoothedUsage * 100,
                instantUsage * 100,
                usedBytes / (1024 * 1024),
                maxBytes / (1024 * 1024),
                sampleCount,
                pressureChanges,
                autoGcTriggers,
                listenerCount
            );
        }
    }
}