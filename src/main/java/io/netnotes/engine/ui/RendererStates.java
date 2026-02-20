package io.netnotes.engine.ui;

import io.netnotes.engine.state.ConcurrentBitFlagStateMachine;
import io.netnotes.engine.state.BitFlagStateMachine.StateSnapshot;

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
    
    // Renderer lifecycle (all renderers)
    public static final int INITIALIZING       = 0;
    public static final int READY              = 1;
    public static final int SHUTTING_DOWN      = 2;
    public static final int STOPPED            = 3;
    public static final int ERROR              = 4;

    // Container tracking (all renderers)
    public static final int HAS_CONTAINERS         = 10;
    public static final int HAS_FOCUSED_CONTAINER  = 11;
    public static final int HAS_VISIBLE_CONTAINERS = 12;
    public static final int CREATING_CONTAINER     = 13;
    public static final int DESTROYING_CONTAINER   = 14;
    
    // ===== OPERATIONAL STATES =====
    
    
    /**
     * Rendering UI changes
     */
    public static final int RENDERING              = 16;


    // Console-specific states (bits 20+)
    public static final int HAS_ACTIVE         = 20;
    public static final int SWITCHING_FOCUS    = 21;
    public static final int CLEARING_SCREEN    = 22;
    public static final int HANDLING_RESIZE    = 23;
    
    // ===== HELPER METHODS =====
    
   
   
    
    /**
     * Check if service is busy
     */
    public static boolean isBusy(StateSnapshot state) {
        return state.hasState(CREATING_CONTAINER) ||
               state.hasState(DESTROYING_CONTAINER) ||
               state.hasState(RENDERING);
    }
    
   
    public static boolean canAcceptRequests(StateSnapshot state) {
        return state.hasState(READY) && 
               !state.hasState(SHUTTING_DOWN);
    }
 
       /**
     * Check if containers exist
     */
    public static boolean hasContainers(ConcurrentBitFlagStateMachine state) {
        return state.hasState(HAS_CONTAINERS);
    }
    /**
     * Check if any containers are visible
     */
    public static boolean hasVisibleContainers(ConcurrentBitFlagStateMachine state) {
        return state.hasState(HAS_VISIBLE_CONTAINERS);
    }
    
    /**
     * Check if a container has focus
     */
    public static boolean hasFocus(ConcurrentBitFlagStateMachine state) {
        return state.hasState(HAS_FOCUSED_CONTAINER);
    }
    
    /**
     * Check if service needs attention
     */
    public static boolean needsAttention(ConcurrentBitFlagStateMachine state) {
        return state.hasState(ERROR);
    }
    
    /**
     * Get human-readable state description
     */
    public static String describe(StateSnapshot state) {
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
    public static String describeDetailed(StateSnapshot state, int containerCount) {
        StringBuilder status = new StringBuilder();
        
        status.append("ContainerService: ").append(describe(state)).append("\n");
        status.append("  Containers: ").append(containerCount).append("\n");
        

        if (state.hasState(HAS_VISIBLE_CONTAINERS)) {
            status.append("  Visible containers: Yes\n");
        }
        
        if (state.hasState(HAS_FOCUSED_CONTAINER)) {
            status.append("  Focused container: Yes\n");
        }
      
        return status.toString();
    }
}