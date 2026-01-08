package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.core.system.control.ui.Renderable;

public interface TerminalRenderable extends Renderable<TerminalBatchBuilder, TerminalRenderElement> {

    @Override
	public TerminalRenderState getRenderState();
    

}
