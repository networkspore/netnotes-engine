package io.netnotes.engine.core.system.control.terminal.layout;

import io.netnotes.engine.core.system.control.terminal.TerminalBatchBuilder;
import io.netnotes.engine.core.system.control.terminal.TerminalRectangle;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderable;
import io.netnotes.engine.core.system.control.ui.FloatingLayerManager;
import io.netnotes.engine.core.system.control.ui.Point2D;
import io.netnotes.engine.core.system.control.ui.SpatialRegionPool;

public class TerminalFloatingLayoutManager extends FloatingLayerManager<
    TerminalBatchBuilder,
    TerminalRenderable,
    Point2D,
    TerminalRectangle,
    TerminalLayoutContext,
    TerminalLayoutData,
    TerminalLayoutCallback
> {

    public TerminalFloatingLayoutManager(String containerName, SpatialRegionPool<TerminalRectangle> regionPool) {
        super(containerName, regionPool);
    }
    
}
