  Netnotes Engine Layout System Documentation                                                                                          
                                                                                                                                       
  Overview                                                                                                                             
                                                                                                                                       
  The package io.netnotes.engine.ui.renderer package contains an incremental layout engine that processes nodes individually through callbacks. 
  It uses a two-callback model (content and layout) and supports grouping of children for coordinated layout updates. The system is
  designed for efficiency with object pooling and damage propagation for minimal re-rendering.                                         
                  
  Core Components                                                                                                                      
   
  1. Renderable (Base Class)                                                                                                           
                  
  The Renderable class is the foundation of the layout system. It represents any UI element that can be rendered and laid out.         
                  
  Key Features:                                                                                                                        
  - Spatial Region Management: Each renderable has a spatial region (region) and can have a requested region (requestedRegion) for
  layout constraints                                                                                                                   
  - Damage Tracking: Uses object pooling for efficient damage propagation
  - Hierarchy: Parent-child relationships with z-order sorting                                                                         
  - Event Handling: Built-in event registry for input events                                                                           
  - State Management: BitFlagStateMachine for visibility, focus, and lifecycle states                                                  
  - Floating Support: Can escape parent bounds for floating elements                                                                   
                                                                                                                                       
  Damage System:                                                                                                                       
  protected S damage = null;  // Accumulated damage region                                                                             
  protected boolean childrenDirty = false;  // Structural changes                                                                      
  protected boolean pendingInvalidate = false;  // Deferred invalidation                                                               
                                                                                                                                       
  Damage Propagation:                                                                                                                  
  - invalidate(S localRegion): Marks region as damaged                                                                                 
  - propagateDamageToParent(): Sends damage up the hierarchy                                                                           
  - reportDamage(S absoluteRegion): Final damage reporting at root                                                                     
                                                                                                                                       
  2. LayoutNode (Tree Structure)                                                                                                       
                                                                                                                                       
  LayoutNode represents a single node in the layout tree, paired 1:1 with a Renderable.                                                
                                                                                                                                       
  Two-Callback Model:                                                                                                                  
  - Content Callback (contentCallback): Fires bottom-up when getMeasuredSize() is called
  - Layout Callback (layoutCallback): Fires top-down during normal pass for positioning                                                
                                                                                       
  Key Properties:                                                                                                                      
  - calculatedLayout: Result of callback calculations (committed once per pass)                                                        
  - contentMeasured: Gate to prevent redundant content measurement                                                                     
  - ownedGroups: Groups owned by this node                                                                                             
  - memberGroup: Group this node belongs to (at most one)                                                                              
                                                                                                                                       
  3. RenderableLayoutManager (Layout Engine)                                                                                           
                                                                                                                                       
  The RenderableLayoutManager orchestrates the layout passes and manages the node lifecycle.                                           
                                                                                                                                       
  Pass Model:                                                                                                                          
  - Single Depth-Sorted Traversal: Parents before children
  - Dirty Tracking: Only processes nodes marked as dirty                                                                               
  - Damage Suppression: During layout execution, damage accumulates and render fires once at pass completion
                                                                                                                                       
  Key Phases:                                                                                                                          
  1. Content Pre-Pass: Bottom-up measurement of content-sized nodes                                                                    
  2. Layout Pass: Top-down positioning and sizing                                                                                      
  3. Group Execution: Group callbacks fire after owner commits                                                                         
                                                                                                                                       
  Layout Flow                                                                                                                          
                                                                                                                                       
  Incremental Update Process                                                                                                           
                                                                                                                                       
  1. Dirty Marking: When a renderable changes, it calls requestLayoutUpdate()                                                          
  2. Pass Scheduling: Layout manager debounces requests and schedules a pass
  3. Content Measurement: Bottom-up measurement for content-sized nodes                                                                
  4. Layout Calculation: Top-down positioning using callbacks                                                                          
  5. Application: Results committed to renderables                                                                                     
  6. Damage Propagation: Invalidated regions propagated up the tree                                                                    
  7. Rendering: Final damage regions trigger re-rendering                                                                              
                                                                                                                                       
  Callback Execution                                                                                                                   
                                                                                                                                       
  Content Callback Flow:                                                                                                               
  User calls getMeasuredSize() → LayoutContext triggers measureContent() →
  Content callback fires → Sets content axes in calculatedLayout →                                                                     
  Returns measured size to parent                                                                                                      
                                                                                                                                       
  Layout Callback Flow:                                                                                                                
  Manager reaches node in depth order → layoutCallback.calculate() fires →                                                             
  Sets position/fill axes in calculatedLayout → finalizeCalculatedLayout() ensures completeness →                                      
  applyNode() commits to renderable                                                                                                    
                                                                                                                                       
  Grouping System                                                                                                                      
                                                                                                                                       
  LayoutGroup                                                                                                                          
                                                                                                                                       
  Groups allow coordinated layout of sibling renderables:                                                                              
                  
  Features:                                                                                                                            
  - Shared Callback: Single callback manages all group members
  - Owner-Based: Group owned by parent node                                                                                            
  - Content Coordination: Members measured together, laid out together
                                                                                                                                       
  Group Execution:                                                                                                                     
  1. Owner node commits                                                                                                                
  2. Group callback fires with all member contexts                                                                                     
  3. Callback sets layout data for each member                                                                                         
  4. Members applied in depth order                                                                                                    
                                   
  Group Callback Interface                                                                                                             
                                                                                                                                       
  void calculate(LC[] contexts, Map<String, LayoutDataInterface<LD>> layoutDataInterface);                                             
                                                                                                                                       
  - contexts: Array of contexts for each group member                                                                                  
  - layoutDataInterface: Map to read/write member layout data                                                                          
                  
  Damage System Integration

  Damage Propagation Flow

  Child invalidates → accumulate damage in local region →
  translate to parent coordinates → propagateDamageUp() →
  union with parent damage → repeat up to root →
  root reports damage to container → render triggered

  Render Phase Coordination

  DETACHED → COLLECTING → APPLYING → RENDERED → IDLE

  - APPLYING: Layout data being applied
  - RENDERED: Layout complete, ready for rendering
  - IDLE: Ready for next cycle

  Object Pooling

  LayoutDataPool

  Efficient memory management through object pooling:

  LD obtain();        // Get recycled instance
  void recycle(LD);   // Return to pool

  SpatialRegionPool

  Regions recycled to minimize allocation overhead.

  Key Design Principles

  1. Incremental Updates: Only process changed nodes
  2. Single Commit: Each node committed exactly once per pass
  3. Two-Callback Model: Clear separation of content measurement and layout
  4. Damage-Driven: Minimal re-rendering through precise damage tracking
  5. Object Pooling: Memory efficiency through recycling
  6. Thread Safety: Serialized execution with virtual executors

  Usage Patterns

  Registering Renderables

  layoutManager.registerRenderable(renderable, layoutCallback);

  Marking Dirty

  renderable.requestLayoutUpdate();  // Marks dirty

  Creating Groups

  layoutManager.createGroup("myGroup");
  layoutManager.addToGroup(renderable, "myGroup");
  layoutManager.setGroupLayoutCallback("myGroup", groupCallback);

  Floating Elements

  renderable.makeFloating(anchor);  // Escape parent bounds
  renderable.makeStatic();          // Return to normal layout