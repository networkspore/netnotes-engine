package io.netnotes.engine.core.system.control.ui;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import io.netnotes.engine.core.system.control.ui.layout.LayoutCallback;
import io.netnotes.engine.core.system.control.ui.layout.LayoutContext;
import io.netnotes.engine.core.system.control.ui.layout.LayoutData;

public class FloatingLayerManager<
    B extends BatchBuilder<S>,
    R extends Renderable<B,P,S,LC,LD,LCB,?,?,?,?,R>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    LC extends LayoutContext<B,R,P,S,LD,LCB,LC,?>,
    LD extends LayoutData<B,R,S,LD,?>,
    LCB extends LayoutCallback<B,R,P,S,LC,LD,LCB>
> {
    protected final List<R> floatingElements = new CopyOnWriteArrayList<>();
    protected final String containerName;
    protected final SpatialRegionPool<S> regionPool;
    
    public FloatingLayerManager(String containerName, SpatialRegionPool<S> regionPool) {
        this.containerName = containerName;
        this.regionPool = regionPool;
    }
    
    public void add(R element) {
        if (!floatingElements.contains(element)) {
            floatingElements.add(element);
        }
    }
    
    public void remove(R element) {
        floatingElements.remove(element);
    }
    
    public boolean contains(R element) {
        return floatingElements.contains(element);
    }
    
    public List<R> getElements() {
        return new ArrayList<>(floatingElements);
    }
    
    public void toBatch(B batch, S viewportRegion) {
        if (floatingElements.isEmpty()) {
            return;
        }
        
        List<R> sorted = new ArrayList<>(floatingElements);
        sorted.sort(Comparator
            .comparingInt(R::getLayerIndex)
            .thenComparingInt(R::getZOrder));
        
        for (R element : sorted) {
            if (element.isEffectivelyVisible()) {
                element.toBatch(batch, viewportRegion);
            }
        }
    }
    
    public void clear() {
        floatingElements.clear();
    }
    
    public List<R> getElementsByLayer(int layer) {
        List<R> result = new ArrayList<>();
        for (R element : floatingElements) {
            if (element.getLayerIndex() == layer) {
                result.add(element);
            }
        }
        result.sort(Comparator.comparingInt(R::getZOrder));
        return result;
    }
}