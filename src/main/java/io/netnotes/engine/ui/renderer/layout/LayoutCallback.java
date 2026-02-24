package io.netnotes.engine.ui.renderer.layout;

import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.renderer.BatchBuilder;
import io.netnotes.engine.ui.renderer.Renderable;

@FunctionalInterface
public interface LayoutCallback<
    B extends BatchBuilder<S>,
    R extends Renderable<B,P,S,LC,LD,LCB,?,?,?,?,R>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    LC extends LayoutContext<B,R,P,S,LD,LCB,LC,?>,
    LD extends LayoutData<B,R,S,LD,?>,
    LCB extends LayoutCallback<B,R,P,S,LC,LD,LCB>
> {
   LD calculate(LC context);
}