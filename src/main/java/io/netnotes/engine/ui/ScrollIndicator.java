package io.netnotes.engine.ui;


public interface ScrollIndicator<R> {
    R getRenderable();
    void updatePosition(int current, int max, int viewportSize);
    int getPreferredSize();
}