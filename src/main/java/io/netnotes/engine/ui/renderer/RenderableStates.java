package io.netnotes.engine.ui.renderer;

import io.netnotes.engine.state.BitFlagStateMachine.StateSnapshot;

/**
 * State bit positions for ConcurrentBitFlagStateMachine
 * 
 * THREE-LAYER MODEL:
 * REQUEST -> user intent (set by app code)
 * RESOLUTION -> computed effective state (managed by LayoutManager)
 * EXECUTION -> final render decision (STATE_RENDERABLE)
 */
public final class RenderableStates {

    // ===== REQUEST LAYER =====
    // User never directly requests "VISIBLE" - it's derived

    public static final int STATE_FORCED_HIDDEN_DESIRED = 1;
    public static final int STATE_HIDDEN_DESIRED = 2;
    public static final int STATE_INVISIBLE_DESIRED = 3;
    public static final int STATE_FOCUS_DESIRED = 4;


    // ===== EXECUTION LAYER =====
  
    public static final int STATE_RENDERABLE = 6;

    
    // ===== LIFECYCLE =====
    public static final int STATE_STARTED = 8;
    
    // ===== INTERNAL =====

    public static final int STATE_NEEDS_FULL_RENDER = 9;
    public static final int STATE_VISIBILITY_DIRTY = 10;

    // ===== HIERARCHY =====
    public static final int STATE_ATTACHED = 11;

    // ===== INTERACTION =====
    public static final int STATE_FOCUSED = 12;
    public static final int STATE_HOVERED = 13;
    public static final int STATE_PRESSED = 14;
    public static final int STATE_EFFECTIVELY_HIDDEN = 15;
    public static final int STATE_EFFECTIVELY_INVISIBLE = 16;
    // ===== RESOLUTION LAYER =====



    public static final int STATE_CONTAINER_LAYOUT_MANAGED = 17;
    public static final int STATE_CONTAINER_OFF_SCREEN = 18;

    public static final int DESTROYED = 19;


    public static boolean isRenderableVisible(StateSnapshot snap){
        return !snap.hasAnyState(STATE_EFFECTIVELY_HIDDEN, STATE_HIDDEN_DESIRED);
    }

    public static boolean isRenderableInvisible(StateSnapshot snap){
        return snap.hasAnyState(STATE_EFFECTIVELY_INVISIBLE, STATE_INVISIBLE_DESIRED);
    }
    
    public static boolean isShowing(StateSnapshot snap){ return snap.hasState(STATE_RENDERABLE); }

}
