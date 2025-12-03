package io.netnotes.engine.core.system.control.nodes;

import io.netnotes.engine.core.system.SystemProcess;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

/**
 * ProcessConfig - Defines namespace and data inheritance
 * 
 * DECIDED AT INSTALL TIME, NOT LOAD TIME
 * 
 * ProcessId determines:
 * - Data root path: /data/nodes/{processId}
 * - Flow base path: /system/nodes/{processId}
 * - Namespace sharing with other packages
 * 
 * Examples:
 * 1. Standalone: processId = packageId (own namespace)
 * 2. Shared namespace: processId = "shared-workspace" (multiple packages)
 * 3. Cluster member: processId = "cluster-leader-id" (inherits from leader)
 */
public class ProcessConfig {
    private final NoteBytesReadOnly processId;  // Namespace identifier
    private final ContextPath dataRootPath;     // /data/nodes/{processId}
    private final ContextPath flowBasePath;     // /system/nodes/{processId}
    private final InheritanceMode mode;
    
    public enum InheritanceMode {
        STANDALONE,      // Own namespace
        SHARED,          // Shared namespace (multiple packages)
        CLUSTER_LEADER,  // Leader of cluster
        CLUSTER_MEMBER   // Member inheriting from leader


    }

    private ProcessConfig(NoteBytesReadOnly processId, ContextPath dataRootPath, ContextPath flowBasePath, InheritanceMode mode) {
        this.processId = processId;
        this.dataRootPath = dataRootPath;
        this.flowBasePath = flowBasePath;
        this.mode = mode;
    }
    
    public ProcessConfig(NoteBytesReadOnly processId, InheritanceMode mode) {
        this.processId = processId;
        this.mode = mode;

        
        this.dataRootPath = SystemProcess.NODE_DATA_PATH.append(processId);
        this.flowBasePath = SystemProcess.NODES_PATH.append(processId);
    }
    
    public NoteBytesReadOnly getProcessId() { return processId; }
    public ContextPath getDataRootPath() { return dataRootPath; }
    public ContextPath getFlowBasePath() { return flowBasePath; }
    public InheritanceMode getMode() { return mode; }
    
    /**
     * Create standalone config (most common)
     */
    public static ProcessConfig standalone(NoteBytesReadOnly packageId) {
        return new ProcessConfig(packageId, InheritanceMode.STANDALONE);
    }
    
    /**
     * Create shared namespace config
     */
    public static ProcessConfig shared(NoteBytesReadOnly sharedProcessId) {
        return new ProcessConfig(sharedProcessId, InheritanceMode.SHARED);
    }
    
    /**
     * Create cluster leader config
     */
    public static ProcessConfig clusterLeader(NoteBytesReadOnly clusterId) {
        return new ProcessConfig(clusterId, InheritanceMode.CLUSTER_LEADER);
    }
    
    /**
     * Create cluster member config (inherits leader's namespace)
     */
    public static ProcessConfig clusterMember(NoteBytesReadOnly leaderProcessId) {
        return new ProcessConfig(leaderProcessId, InheritanceMode.CLUSTER_MEMBER);
    }

    public NoteBytesObject toNoteBytes(){
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.PROCESS_ID, processId),
            new NoteBytesPair("data_root_path", dataRootPath.getSegments()),
            new NoteBytesPair("flow_base_path", flowBasePath.getSegments()),
            new NoteBytesPair(Keys.MODE, mode.toString())
        });
    }

    public static ProcessConfig fromNoteBytes(NoteBytesMap map){
        NoteBytesReadOnly processId = map.getReadOnly(Keys.PROCESS_ID);
        NoteBytes dataRootPath = map.get("data_root_path");
        NoteBytes flowBasePath = map.get("flow_base_path");
        NoteBytes mode = map.get(Keys.MODE);
       
        return new ProcessConfig(
            processId,
            ContextPath.of(dataRootPath.getAsNoteBytesArrayReadOnly()),
            ContextPath.of(flowBasePath.getAsNoteBytesArrayReadOnly()),
            InheritanceMode.valueOf(mode.getAsString().toUpperCase()) 
        );
    }
}
