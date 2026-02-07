package io.netnotes.engine.ui;

import io.netnotes.engine.state.BitFlagStateMachine;

public class RenderingServiceStates {
    // Service lifecycle
    public static final int INITIALIZING       = 0;
    public static final int READY              = 1;
    public static final int ACCEPTING_REQUESTS = 2;
    public static final int SHUTTING_DOWN      = 3;
    public static final int STOPPED            = 4;
    public static final int ERROR              = 5;
/**
     * UI renderer is connected and functional
     */
    public static final int UI_RENDERER_ACTIVE     = 10;
     
    /**
     * Check if service can accept new container requests
     */
    public static boolean canAcceptRequests(BitFlagStateMachine state) {
        return state.hasState(READY) && 
               state.hasState(ACCEPTING_REQUESTS) &&
               !state.hasState(SHUTTING_DOWN);
    }

    public static boolean isOperational(BitFlagStateMachine state) {
        return state.hasState(READY) && 
               state.hasState(UI_RENDERER_ACTIVE) &&
               !state.hasState(ERROR) &&
               !state.hasState(SHUTTING_DOWN);
    }

     /**
     * Check if UI is available
     */
    public static boolean hasUI(BitFlagStateMachine state) {
        return state.hasState(UI_RENDERER_ACTIVE);
    }

    /**
     * Check if service needs attention
     */
    public static boolean needsAttention(BitFlagStateMachine state) {
        return state.hasState(ERROR);
    }

     /**
     * Get human-readable state description
     */
    public static String describe(BitFlagStateMachine state) {
        if (state.hasState(ERROR)) {
            return "Error";
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
        
        if (state.hasState(READY)) {
            StringBuilder status = new StringBuilder("Ready");
            
            return status.toString();
        }
        
        return "Unknown state";
    }
}