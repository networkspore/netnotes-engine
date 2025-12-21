package io.netnotes.engine.core.system.control.containers;

import io.netnotes.engine.state.BitFlagStateMachine;

/**
 * ContainerServiceStates - State flags for ContainerService
 * 
 * Lifecycle:
 * INITIALIZING → READY → SHUTTING_DOWN → STOPPED
 * 
 * Operational States:
 * - ACCEPTING_REQUESTS: Can handle new container requests
 * - HAS_FOCUSED_CONTAINER: At least one container has focus
 * - HAS_VISIBLE_CONTAINERS: At least one container is visible
 */
public class RendererStates {
    
    // ===== LIFECYCLE STATES =====
    
    /**
     * ContainerService is initializing
     */
    public static final long INITIALIZING           = 1L << 0;
    
    /**
     * Service is ready and accepting requests
     */
    public static final long READY                  = 1L << 1;
    
    /**
     * Service is actively processing requests
     */
    public static final long ACCEPTING_REQUESTS     = 1L << 2;
    
    /**
     * Service is shutting down
     */
    public static final long SHUTTING_DOWN          = 1L << 3;
    
    /**
     * Service is stopped
     */
    public static final long STOPPED                = 1L << 4;
    
    /**
     * Error occurred
     */
    public static final long ERROR                  = 1L << 5;
    
    // ===== OPERATIONAL STATES =====
    
    /**
     * UI renderer is connected and functional
     */
    public static final long UI_RENDERER_ACTIVE     = 1L << 10;
    
    /**
     * At least one container exists
     */
    public static final long HAS_CONTAINERS         = 1L << 11;
    
    /**
     * At least one container is visible
     */
    public static final long HAS_VISIBLE_CONTAINERS = 1L << 12;
    
    /**
     * A container currently has focus
     */
    public static final long HAS_FOCUSED_CONTAINER  = 1L << 13;
    
    /**
     * Processing a container creation
     */
    public static final long CREATING_CONTAINER     = 1L << 14;
    
    /**
     * Processing a container destruction
     */
    public static final long DESTROYING_CONTAINER   = 1L << 15;
    
    /**
     * Rendering UI changes
     */
    public static final long RENDERING              = 1L << 16;
    
    // ===== HELPER METHODS =====
    
    /**
     * Check if service is operational
     */
    public static boolean isOperational(BitFlagStateMachine state) {
        return state.hasState(READY) && 
               state.hasState(ACCEPTING_REQUESTS) &&
               state.hasState(UI_RENDERER_ACTIVE) &&
               !state.hasState(ERROR) &&
               !state.hasState(SHUTTING_DOWN);
    }
    
    /**
     * Check if service can accept new container requests
     */
    public static boolean canAcceptRequests(BitFlagStateMachine state) {
        return state.hasState(READY) && 
               state.hasState(ACCEPTING_REQUESTS) &&
               !state.hasState(SHUTTING_DOWN);
    }
    
    /**
     * Check if service is busy
     */
    public static boolean isBusy(BitFlagStateMachine state) {
        return state.hasState(CREATING_CONTAINER) ||
               state.hasState(DESTROYING_CONTAINER) ||
               state.hasState(RENDERING);
    }
    
    /**
     * Check if UI is available
     */
    public static boolean hasUI(BitFlagStateMachine state) {
        return state.hasState(UI_RENDERER_ACTIVE);
    }
    
    /**
     * Check if containers exist
     */
    public static boolean hasContainers(BitFlagStateMachine state) {
        return state.hasState(HAS_CONTAINERS);
    }
    
    /**
     * Check if any containers are visible
     */
    public static boolean hasVisibleContainers(BitFlagStateMachine state) {
        return state.hasState(HAS_VISIBLE_CONTAINERS);
    }
    
    /**
     * Check if a container has focus
     */
    public static boolean hasFocus(BitFlagStateMachine state) {
        return state.hasState(HAS_FOCUSED_CONTAINER);
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
            
            if (state.hasState(HAS_CONTAINERS)) {
                status.append(" (containers active");
                
                if (state.hasState(HAS_FOCUSED_CONTAINER)) {
                    status.append(", focused");
                }
                
                status.append(")");
            } else {
                status.append(" (idle)");
            }
            
            if (isBusy(state)) {
                status.append(" [busy]");
            }
            
            return status.toString();
        }
        
        return "Unknown state";
    }
    
    /**
     * Get detailed status
     */
    public static String describeDetailed(BitFlagStateMachine state, int containerCount) {
        StringBuilder status = new StringBuilder();
        
        status.append("ContainerService: ").append(describe(state)).append("\n");
        status.append("  Containers: ").append(containerCount).append("\n");
        
        if (state.hasState(UI_RENDERER_ACTIVE)) {
            status.append("  UI Renderer: Active\n");
        } else {
            status.append("  UI Renderer: Inactive\n");
        }
        
        if (state.hasState(HAS_VISIBLE_CONTAINERS)) {
            status.append("  Visible containers: Yes\n");
        }
        
        if (state.hasState(HAS_FOCUSED_CONTAINER)) {
            status.append("  Focused container: Yes\n");
        }
        
        if (state.hasState(ACCEPTING_REQUESTS)) {
            status.append("  Accepting requests: Yes");
        } else {
            status.append("  Accepting requests: No");
        }
        
        return status.toString();
    }
}