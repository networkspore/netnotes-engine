package io.netnotes.engine.ui;


public enum SizePreference {
    STATIC,
    FILL,         // Take all available space (equivalent to PERCENT with 100%)
    FIT_CONTENT,  // Use preferred/requested size
    PERCENT,      // Use percentWidth or percentHeight fields
    INHERIT;      // Use parent's default (or null for same effect)

    // ===== AXIS CLASSIFICATION PREDICATES ====================================
    //
    // These replace scattered equality chains like:
    //   pref == SizePreference.FILL || pref == SizePreference.PERCENT
    // with self-documenting, compiler-verified calls:
    //   pref.isParentDependent()
    //
    // They also back TerminalRegion's per-axis isSizedByParent /
    // isSizedByContent helpers, which in turn drive the two-phase layout
    // in RenderableLayoutManager (Phase 1 top-down for parent-dependent axes,
    // Phase 2 bottom-up for content-dependent axes).

    /**
     * True when this preference means the dimension is determined by the
     * parent container (FILL, PERCENT).  The node must be laid out
     * <em>after</em> its parent so the parent's committed region is available.
     */
    public boolean isParentDependent() {
        return this == FILL || this == PERCENT;
    }

    /**
     * True when this preference means the dimension is determined by the
     * node's own content / children (FIT_CONTENT).  The node must be laid
     * out <em>after</em> its children so preferred sizes can bubble up.
     */
    public boolean isContentDependent() {
        return this == FIT_CONTENT;
    }

    /**
     * True when this preference means the dimension is fixed and independent
     * of both parent and children (STATIC).
     */
    public boolean isFixed() {
        return this == STATIC;
    }

    /**
     * True when this preference has a concrete, layout-resolved value —
     * i.e. any value except INHERIT.  Convenience inverse of {@link #isInherited()}.
     */
    public boolean isResolved() {
        return this != INHERIT;
    }

    /**
     * True when this preference defers to the container's default.
     */
    public boolean isInherited() {
        return this == INHERIT;
    }
}