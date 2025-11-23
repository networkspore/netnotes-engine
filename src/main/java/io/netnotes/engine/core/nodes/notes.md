// ============================================================================
// ARCHITECTURAL SUMMARY
// ============================================================================


 * KEY DESIGN PRINCIPLES:
 * 
 * 1. ROBUST INode INTERFACE
 *    - All FlowProcess methods exposed through INode
 *    - NodeController stores everything as INode
 *    - Works with any implementation (BaseNode, DelegatingNode, custom)
 * 
 * 2. DUAL CHANNEL SEPARATION
 *    
 *    FlowProcess Channel (Control):
 *      ✓ Negotiation ("Can I send you data?")
 *      ✓ Permission granting ("Yes, proceed")
 *      ✓ Configuration ("Update your settings")
 *      ✓ Status queries ("How are you doing?")
 *      ✓ Event notifications (progress, errors, completion)
 *      ✓ Monitoring (subscribe to all node events)
 *      → Small messages, doesn't interfere with data traffic
 *    
 *    Stream Channel (Data):
 *      ✓ Large file transfers
 *      ✓ Database query results
 *      ✓ Continuous data streams
 *      ✓ Binary data
 *      → Dedicated connection, isolated from control traffic
 * 
 * 3. TYPICAL WORKFLOW
 *    Step 1: Negotiate via FlowProcess
 *      Client: "I want to send 100MB file"
 *      Server: "OK, I'm ready"
 *    
 *    Step 2: Transfer via Stream
 *      Large file flows through dedicated pipe
 *      Control messages still flowing separately
 *    
 *    Step 3: Notify via FlowProcess
 *      Client: "Transfer complete"
 *      Monitor: (receives notification)
 * 
 * 4. BENEFITS
 *    ✓ Control and data traffic separated
 *    ✓ Large transfers don't block control messages
 *    ✓ Monitoring doesn't see data payloads
 *    ✓ Negotiation before resource commitment
 *    ✓ Clear channel selection rules
 * 
 * 5. WHEN TO USE EACH CHANNEL
 *    
 *    Use FlowProcess when:
 *      - Message < 1KB
 *      - Need publish-subscribe
 *      - Need monitoring/observability
 *      - Negotiating or requesting permission
 *      - Sending status/progress updates
 *    
 *    Use Streams when:
 *      - Data > 1KB
 *      - Continuous data flow
 *      - Want isolation from other traffic
 *      - Bidirectional pipe needed
 *      - Natural backpressure desired
