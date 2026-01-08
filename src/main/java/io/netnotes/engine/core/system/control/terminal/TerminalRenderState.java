package io.netnotes.engine.core.system.control.terminal;

import java.util.List;

import io.netnotes.engine.core.system.control.ui.RenderState;

/**
 * RenderState - collection of render elements
 */
public class TerminalRenderState extends RenderState<TerminalBatchBuilder, TerminalRenderElement> {

    
    private TerminalRenderState(List<TerminalRenderElement> elements) {
        super(elements);
    }
    
    public TerminalBatchBuilder toBatch(TerminalContainerHandle terminal, long generation) {
        TerminalBatchBuilder batch = terminal.batch(generation);
        
        for (TerminalRenderElement element : elements) {
            element.addToBatch(batch);
        }
        
        return batch;
    }
    
    public int getElementCount() {
        return elements.size();
    }
    
    public List<TerminalRenderElement> getElements() {
        return elements;
    }
    
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    
    @SuppressWarnings("unchecked")
	public static TerminalStateBuilder builder() {
        return new TerminalStateBuilder();
    }

    public static final class TerminalStateBuilder extends RenderState.Builder<TerminalBatchBuilder, TerminalRenderElement> 
    {
        @Override
         public TerminalStateBuilder add(TerminalRenderElement element) {
            this.elements.add(element);
            return this;
        }
        @Override
        public TerminalStateBuilder addAll(List<TerminalRenderElement> elements) {
            this.elements.addAll(elements);
            return this;
        }
        

        public TerminalStateBuilder addAll(TerminalRenderElement... elements) {
            this.elements.addAll(List.of(elements));
            return this;
        }
        
        @Override
        public TerminalStateBuilder addIf(boolean condition, TerminalRenderElement element) {
            if (condition) {
                elements.add(element);
            }
            return this;
        }
        
        @Override
        public TerminalRenderState build() {
            return new TerminalRenderState( this.elements);
        }
    }
    
}