package io.netnotes.engine.core.system.control.containers;


/**
 * ContainerState - Current state of a container
 * 
 * Abstract states that apply regardless of UI implementation
 */
public enum ContainerState {
    /** Container is being created */
    CREATING,
    
    /** Container is visible and active */
    VISIBLE,
    
    /** Container is hidden/minimized */
    HIDDEN,
    
    /** Container has focus (current) */
    FOCUSED,
    
    /** Container is maximized (fills screen/parent) */
    MAXIMIZED,
    
    /** Container is being destroyed */
    DESTROYING,
    
    /** Container is destroyed and inactive */
    DESTROYED
}