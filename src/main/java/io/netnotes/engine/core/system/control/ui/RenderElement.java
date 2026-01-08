package io.netnotes.engine.core.system.control.ui;

/**
 * RenderElement - adds commands to a batch
 * 
 * @param <B> BatchBuilder type this element can add to
 */
@FunctionalInterface
public interface RenderElement<B extends BatchBuilder> {
    void addToBatch(B batch);
}