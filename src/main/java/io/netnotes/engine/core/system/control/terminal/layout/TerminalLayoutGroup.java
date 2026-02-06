package io.netnotes.engine.core.system.control.terminal.layout;

import io.netnotes.engine.core.system.control.terminal.TerminalBatchBuilder;
import io.netnotes.engine.core.system.control.terminal.TerminalRectangle;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderable;
import io.netnotes.engine.core.system.control.ui.Point2D;
import io.netnotes.engine.core.system.control.ui.layout.LayoutGroup;

public class TerminalLayoutGroup extends LayoutGroup<
    TerminalBatchBuilder,
    TerminalRenderable,
    Point2D,
    TerminalRectangle,
    TerminalLayoutData,
    TerminalLayoutContext,
    TerminalLayoutGroupCallback,
    TerminalLayoutNode,
    TerminalGroupCallbackEntry,
    TerminalLayoutGroup
> {

    public TerminalLayoutGroup(String groupId) {
        super(groupId);
    }
    
}
