package io.netnotes.engine.core.system.control.ui;

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

    public static final int STATE_ENABLED_DESIRED = 1;
    public static final int STATE_HIDDEN_DESIRED = 2;
    public static final int STATE_INVISIBLE_DESIRED = 3;
    public static final int STATE_FOCUS_DESIRED = 4;


    // ===== EXECUTION LAYER =====
    public static final int STATE_INVISIBLE = 5;
    public static final int STATE_RENDERABLE = 6;
    public static final int STATE_HIDDEN = 7;
    
    // ===== INTERNAL =====

    public static final int STATE_NEEDS_FULL_RENDER = 8;
    public static final int STATE_VISIBILITY_DIRTY = 9;

    // ===== HIERARCHY =====
    public static final int STATE_ATTACHED = 10;

    // ===== INTERACTION =====
    public static final int STATE_PRESSED = 11;
    public static final int STATE_FOCUSED = 12;
    public static final int STATE_HOVERED = 13;

    // ===== RESOLUTION LAYER =====

    public static final int STATE_EFFECTIVELY_ENABLED = 14;
    public static final int STATE_EFFECTIVELY_VISIBLE = 15;

    public static final int DESTROYED = 16;

    public static boolean isVisible(StateSnapshot snap){ 
        return !snap.hasState(STATE_HIDDEN) && !snap.hasState(STATE_INVISIBLE) && snap.hasState(STATE_EFFECTIVELY_VISIBLE);
    }
    
    public static boolean isShowing(StateSnapshot snap){ return snap.hasState(STATE_RENDERABLE); }

}
