package io.netnotes.engine.core.system.control.ui;

import io.netnotes.engine.core.system.control.terminal.TerminalRenderable;

public interface ScrollIndicator {
    TerminalRenderable getRenderable();
    void updatePosition(int current, int max, int viewportSize);
    int getPreferredSize();
}