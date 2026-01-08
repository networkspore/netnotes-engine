package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.core.system.control.ui.RenderElement;

/**
     * RenderElement - adds commands to a batch
     */
    @FunctionalInterface
    public interface TerminalRenderElement extends RenderElement<TerminalBatchBuilder> {
        void addToBatch(TerminalBatchBuilder batch);
    }