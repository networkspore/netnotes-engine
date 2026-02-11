package io.netnotes.engine.ui;

public enum LabelTruncation {
    NONE,           // No truncation (may overflow)
    END,            // "This is a long tex..."
    START,          // "...is a long text"
    MIDDLE          // "This is...ng text"
}