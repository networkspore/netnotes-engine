package io.netnotes.engine.core.system.control.ui;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.engine.core.system.control.containers.ContainerHandle;

/**
 * RenderState - collection of render elements
 */
/**
 * RenderState - collection of render elements
 * 
 * @param <B> BatchBuilder type
 * @param <E> RenderElement type
 */
public class RenderState<B extends BatchBuilder, E extends RenderElement<B>> {
    protected final List<E> elements;
    
    protected RenderState(List<E> elements) {
        this.elements = List.copyOf(elements);
    }
    
     public <H extends ContainerHandle<B, E, H, ?>> B toBatch(H handle, long generation) {
        B batch = handle.batch(generation);
        
        for (E element : elements) {
            element.addToBatch(batch);
        }
        
        return batch;
    }
    
    
    public int getElementCount() {
        return elements.size();
    }
    
    public List<E> getElements() {
        return elements;
    }
    
    public boolean isEmpty() {
        return elements.isEmpty();
    }
    
    public static <B extends BatchBuilder, E extends RenderElement<B>> Builder<B, E> builder() {
        return new Builder<>();
    }
    
    public static class Builder<B extends BatchBuilder, E extends RenderElement<B>> {
        protected final List<E> elements = new ArrayList<>();
        
        public Builder<B, E> add(E element) {
            elements.add(element);
            return this;
        }
        
        public Builder<B, E> addAll(List<E> elements) {
            this.elements.addAll(elements);
            return this;
        }
        
   
        
        public Builder<B, E> addIf(boolean condition, E element) {
            if (condition) {
                elements.add(element);
            }
            return this;
        }
        
        public RenderState<B, E> build() {
            return new RenderState<>(elements);
        }
    }
}