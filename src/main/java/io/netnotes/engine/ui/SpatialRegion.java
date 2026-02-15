package io.netnotes.engine.ui;

import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.NoteBytesReadOnly;

/**
 * Generic spatial region interface
 * 
 * @param <S> The concrete region type (for fluent operations)
 */
public abstract class SpatialRegion<
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>
> {

    public static final NoteBytesReadOnly PARENT_ABS_X = new NoteBytesReadOnly("parentAbsX");
    public static final NoteBytesReadOnly PARENT_ABS_Y = new NoteBytesReadOnly("parentAbsY");

    public abstract void setPosition(P point);

    public abstract P getPosition();

    public abstract boolean absEquals(S other);

    public abstract void transformByParent(S region);

    public abstract void collapse();

    public abstract void setToIdentity();

    public abstract boolean contains(S region);

    public abstract boolean equals(Object other);
    public abstract boolean equals(S other);

     /**
     * Copy region state from renderable
     */
    public abstract void copyFrom(S region);
    
    public abstract boolean containsPoint(P point);
    
    /**
     * Check if this region intersects with another
     */
    public abstract boolean intersects(S other);
    
    /**
     * Calculate intersection with another region
     * Returns empty region if no intersection
     */
    public abstract S intersection(S other);
    
    /**
     * Mutate this region to be the intersection with other
     */
    public abstract void intersectInPlace(S other);
    
    /**
     * Calculate union with another region (bounding volume)
     */
    public abstract  S union(S other);
    
    /**
     * Mutate this region to be the union with other
     */
    public abstract void unionInPlace(S other);
    
    /**
     * Create a copy of this region
     */
    public abstract S copy();


    /**
     * Set this region's values from another
     */
    public abstract void set(S other);
    
    /**
     * Check if region is empty (zero volume)
     */
    public abstract boolean isEmpty();
    
    /**
     * Translate this region by offset
     */
    public abstract void translate(P point);
    
    
    /**
     * Create an empty region of the same type
     */
    public abstract S createEmpty();
    
    /**
     * Check if this region contains a point
     */
    public abstract boolean contains(P point);


    public abstract NoteBytes toNoteBytesArray();

    public abstract NoteBytesObject toNoteBytes();

    public abstract P getParentAbsolutePosition();

    public abstract void setParentAbsolutePosition(P point);

    public abstract P getAbsolutePosition();

    public abstract void clear();

    public abstract void createAbsoluteFrom(S region);

}