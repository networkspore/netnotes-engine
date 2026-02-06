package io.netnotes.engine.core.system.control.terminal.layout;

import io.netnotes.engine.core.system.control.terminal.TerminalBatchBuilder;
import io.netnotes.engine.core.system.control.terminal.TerminalRectangle;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderable;
import io.netnotes.engine.core.system.control.ui.Point2D;
import io.netnotes.engine.core.system.control.ui.layout.GroupLayoutCallback;

@FunctionalInterface
public interface TerminalLayoutGroupCallback extends GroupLayoutCallback<
    TerminalBatchBuilder,
    TerminalRenderable,
    Point2D,
    TerminalRectangle,
    TerminalLayoutData,
    TerminalLayoutContext,
    TerminalLayoutNode,
    TerminalLayoutGroupCallback
> {
    
}
