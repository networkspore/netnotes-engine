package io.netnotes.engine.ui;

public enum SizePreference {
    STATIC,
    FILL,         // Take all available space (equivalent to PERCENT with 100%)
    FIT_CONTENT,  // Use preferred/requested size
    PERCENT,      // Use percentWidth or percentHeight fields
    INHERIT       // Use parent's default (or null for same effect)
}
