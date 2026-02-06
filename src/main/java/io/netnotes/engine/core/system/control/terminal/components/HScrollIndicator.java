package io.netnotes.engine.core.system.control.terminal.components;


import io.netnotes.engine.core.system.control.terminal.TerminalBatchBuilder;
import io.netnotes.engine.core.system.control.terminal.TerminalRectangle;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderable;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.ui.ScrollIndicator;

public class HScrollIndicator extends TerminalRenderable implements ScrollIndicator {
    
    private int current = 0;
    private int max = 0;
    private int viewportSize = 0;
    
    public HScrollIndicator(String name) {
        super(name);
    }
    
    @Override
    public TerminalRenderable getRenderable() {
        return this;
    }
    
    @Override
    public void updatePosition(int current, int max, int viewportSize) {
        if (this.current != current || this.max != max || this.viewportSize != viewportSize) {
            this.current = current;
            this.max = max;
            this.viewportSize = viewportSize;
            invalidate();
        }
    }
    
    @Override
    public int getPreferredSize() {
        return 1;
    }
    
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        int width = getWidth();
        if (width <= 0 || max <= 0) return;

        int thumbWidth = Math.max(1,
                (int)((double)viewportSize / (max + viewportSize) * width));

        int thumbPosition = (max > 0)
                ? (int)((double)current / max * (width - thumbWidth))
                : 0;

        fillRegion(batch,
            new TerminalRectangle(0, 0, width, 1),
            '─',
            TextStyle.NORMAL
        );
        printAt(batch, thumbPosition, 0, "█", TextStyle.NORMAL);

    }
}