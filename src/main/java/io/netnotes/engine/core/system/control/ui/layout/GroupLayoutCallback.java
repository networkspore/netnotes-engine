package io.netnotes.engine.core.system.control.ui.layout;

import java.util.HashMap;

import io.netnotes.engine.core.system.control.ui.BatchBuilder;
import io.netnotes.engine.core.system.control.ui.Renderable;
import io.netnotes.engine.core.system.control.ui.SpatialPoint;
import io.netnotes.engine.core.system.control.ui.SpatialRegion;
import io.netnotes.engine.core.system.control.ui.layout.LayoutGroup.LayoutDataInterface;

@FunctionalInterface
public interface GroupLayoutCallback<
    B extends BatchBuilder<S>,
    R extends Renderable<B,P,S,LC,LD,?,GCB,?,?,?,R>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    LD extends LayoutData<B,R,S,LD,?>,
    LC extends LayoutContext<B,R,P,S,LD,?,LC,L>,
    L extends LayoutNode<B,R,P,S,LD,LC,?,GCB,?,L>,
    GCB extends GroupLayoutCallback<B,R,P,S,LD,LC,L,GCB>
> {
    /**
     * 
     * @param contexts a context array containing a context for each group member
     * @param layoutDataInterface 
     */
    void calculate(LC[] contexts, HashMap<String,LayoutDataInterface<LD>> layoutDataInterface);
}