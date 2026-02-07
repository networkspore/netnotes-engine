package io.netnotes.engine.ui;

import io.netnotes.engine.state.BitFlagStateMachine;

/**
 * ServicesStates - State flags for ServicesProcess
 * 
 * Lifecycle:
 * INITIALIZING → STARTING_CORE → STARTING_OPTIONAL → READY → SHUTTING_DOWN → STOPPED
 */
public class ServicesStates {
    
    // ===== LIFECYCLE STATES =====
    
    /**
     * ServicesProcess is initializing
     */
    public static final long INITIALIZING           = 1L << 0;
    
    /**
     * Starting core services (ContainerService)
     */
    public static final long STARTING_CORE          = 1L << 1;
    
    /**
     * Starting optional services (IODaemon, etc.)
     */
    public static final long STARTING_OPTIONAL      = 1L << 2;
    
    /**
     * All services started and operational
     */
    public static final long READY                  = 1L << 3;
    
    /**
     * Services are shutting down
     */
    public static final long SHUTTING_DOWN          = 1L << 4;
    
    /**
     * All services stopped
     */
    public static final long STOPPED                = 1L << 5;
    
    /**
     * Error occurred during startup/operation
     */
    public static final long ERROR                  = 1L << 6;
    
    // ===== SERVICE-SPECIFIC STATES =====
    
    /**
     * ContainerService is running
     */
    public static final long RENDERING_SERVICE_ACTIVE = 1L << 10;
    
    /**
     * IODaemon is running
     */
    public static final long IO_DAEMON_ACTIVE       = 1L << 11;
    
    /**
     * ContainerService failed to start
     */
    public static final long RENDERING_SERVICE_FAILED = 1L << 12;
    
    /**
     * IODaemon failed to start
     */
    public static final long IO_DAEMON_FAILED       = 1L << 13;
    
    // ===== HELPER METHODS =====
    
    /**
     * Check if services are fully operational
     */
    public static boolean isOperational(BitFlagStateMachine state) {
        return state.hasState(READY) && 
               state.hasState(RENDERING_SERVICE_ACTIVE) &&
               !state.hasState(ERROR) &&
               !state.hasState(SHUTTING_DOWN);
    }
    
    /**
     * Check if core services are running
     */
    public static boolean hasCoreServices(BitFlagStateMachine state) {
        return state.hasState(RENDERING_SERVICE_ACTIVE);
    }
    
    /**
     * Check if optional services are available
     */
    public static boolean hasIODaemon(BitFlagStateMachine state) {
        return state.hasState(IO_DAEMON_ACTIVE);
    }
    
    /**
     * Check if services can be shut down
     */
    public static boolean canShutdown(BitFlagStateMachine state) {
        return !state.hasState(SHUTTING_DOWN) && 
               !state.hasState(STOPPED);
    }
    
    /**
     * Check if services need attention (errors)
     */
    public static boolean needsAttention(BitFlagStateMachine state) {
        return state.hasState(ERROR) ||
               state.hasState(RENDERING_SERVICE_FAILED);
    }
    
    /**
     * Get count of active services
     */
    public static int getActiveServiceCount(BitFlagStateMachine state) {
        int count = 0;
        if (state.hasState(RENDERING_SERVICE_ACTIVE)) count++;
        if (state.hasState(IO_DAEMON_ACTIVE)) count++;
        return count;
    }
    
    /**
     * Get human-readable state description
     */
    public static String describe(BitFlagStateMachine state) {
        if (state.hasState(ERROR)) {
            return "Error - " + getErrorDetails(state);
        }
        
        if (state.hasState(STOPPED)) {
            return "Stopped";
        }
        
        if (state.hasState(SHUTTING_DOWN)) {
            return "Shutting down...";
        }
        
        if (state.hasState(INITIALIZING)) {
            return "Initializing...";
        }
        
        if (state.hasState(STARTING_CORE)) {
            return "Starting core services...";
        }
        
        if (state.hasState(STARTING_OPTIONAL)) {
            return "Starting optional services...";
        }
        
        if (state.hasState(READY)) {
            int activeCount = getActiveServiceCount(state);
            return String.format("Ready (%d service%s active)", 
                activeCount, activeCount == 1 ? "" : "s");
        }
        
        return "Unknown state";
    }
    
    /**
     * Get error details
     */
    private static String getErrorDetails(BitFlagStateMachine state) {
        StringBuilder errors = new StringBuilder();
        
        if (state.hasState(RENDERING_SERVICE_FAILED)) {
            errors.append("ContainerService failed ");
        }
        
        if (state.hasState(IO_DAEMON_FAILED)) {
            errors.append("IODaemon failed ");
        }
        
        return errors.length() > 0 ? errors.toString().trim() : "Unknown error";
    }
}