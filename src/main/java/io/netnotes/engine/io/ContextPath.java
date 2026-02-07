package io.netnotes.engine.io;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesArray;
import io.netnotes.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.noteBytes.processing.ByteDecoding;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;

/**
 * ContextPath - Hierarchical path for organizing input sources.
 * 
 * Root Path:
 *   - Special case: the root itself
 *   - String form: "/"
 *   - isEmpty() returns true
 *   - isAbsolute() returns true
 */
public final class ContextPath {
    public final static String DELIMITER = "/";
    
    public static final NoteBytesReadOnly forwardSlash = new NoteBytesReadOnly("/");
    public static final NoteBytesReadOnly doubleBackSlash = new NoteBytesReadOnly("\\");



    private final NoteStringArrayReadOnly segments;
    private final String pathString;

    public static final ContextPath ROOT = new ContextPath(NoteStringArrayReadOnly.EMPTY);
    

    private ContextPath(NoteStringArrayReadOnly segments) {
        this.segments = segments;
        this.pathString = buildPathString();
    }

    

    public static ContextPath parseExternal(String path, boolean urlEncoded) {
        if (path == null || path.isEmpty() || path.equals(DELIMITER)) return ROOT;
        
        if (urlEncoded) {
            // Decode each segment
            String[] parts = path.split("/");
            List<String> decoded = new ArrayList<>();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    decoded.add(ByteDecoding.UrlDecode(part, NoteBytesMetaData.STRING_TYPE));
                }
            }
            NoteStringArrayReadOnly segments = new NoteStringArrayReadOnly(decoded.toArray(new String[0]));
  
            return new ContextPath(segments);
        }
        
        return parse(path);  // Regular parsing for internal use
    }

    /** Parse a path string into a ContextPath */
    public static ContextPath parse(String path) {
        if (path == null || path.isEmpty() || path.equals(DELIMITER)) return ROOT;

        // Parse segments
        NoteStringArrayReadOnly segments = NoteStringArrayReadOnly.parse(path, DELIMITER);
        
        return new ContextPath(segments);
    }

    public static ContextPath fromNoteBytes(NoteBytes noteBytes){
        if(noteBytes == null){
            return ROOT;
        }
        if(noteBytes.byteLength() == 0){
            return ROOT;
        }

        byte type = noteBytes.getType();

        if(ByteDecoding.isStringType(type)){
            return new ContextPath(new NoteStringArrayReadOnly(noteBytes));
        }else if(noteBytes instanceof NoteBytesArrayReadOnly array){
            return verifyArray(array.getAsArray());
        }else if(noteBytes instanceof NoteBytesArray array){
            return verifyArray(array.getAsArray());
        }else if(type == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE){
            NoteBytesArrayReadOnly array = noteBytes.getAsNoteBytesArrayReadOnly();
            return verifyArray(array.getAsArray());
        }else{
            throw new IllegalArgumentException(" Invalid or corrupt [fromNoteBytes] ContextPath: " + noteBytes);
        }
    }
   
    public static ContextPath of(NoteBytes... arrayOfNoteBytes) {
        if(arrayOfNoteBytes == null){
            return ROOT;
        }

        if(arrayOfNoteBytes.length == 1){
            return fromNoteBytes(arrayOfNoteBytes[0]);
        }

        return verifyArray(arrayOfNoteBytes);
    }

    private static ContextPath verifyArray(NoteBytes... arrayOfNoteBytes){
        for(int i = 0; i < arrayOfNoteBytes.length ; i++){
            if(!ByteDecoding.isStringType(arrayOfNoteBytes[i].getType())){
                throw new IllegalStateException("ContextPath requires string types");
            }
            
        }
        return new ContextPath(new NoteStringArrayReadOnly(arrayOfNoteBytes));
    }


    /** Create ABSOLUTE path from varargs segments */
    public static ContextPath of(String... segments) {
        if (segments == null || segments.length == 0) return ROOT;
        for (String s : segments) {
            if (s != null && !s.isEmpty()) {
                validateSegment(s);
            }
        }
        NoteStringArrayReadOnly array = new NoteStringArrayReadOnly(segments);
        
        return new ContextPath(array);
    }

    public String[] getStringSegments(){
        return segments.getAsStringArray();
    }
    
    /** Create path from varargs segments
    public static ContextPath relative(String... segments) {
        if (segments == null || segments.length == 0) {
            throw new IllegalArgumentException("Relative path must have at least one segment");
        }
        
        List<String> valid = new ArrayList<>();
        for (String s : segments) {
            if (s != null && !s.isEmpty()) {
                validateSegment(s);
                valid.add(s);
            }
        }
        
        if (valid.isEmpty()) {
            throw new IllegalArgumentException("Relative path must have at least one valid segment");
        }
        
        // Validate that first segment is NOT a root segment
        if (isRootSegment(valid.get(0))) {
            throw new IllegalArgumentException(
                "Relative path cannot start with root segment: " + valid.get(0) + 
                " (use ContextPath.of() for absolute paths)");
        }
        
        NoteStringArrayReadOnly array = new NoteStringArrayReadOnly(valid.toArray(new String[0]));
        return new ContextPath(array);
    } */
    
 

    /** Segment validation */
    private static void validateSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            throw new IllegalArgumentException("Segment cannot be null or empty");
        }
        if (segment.contains("/") || segment.contains("\\")) {
            throw new IllegalArgumentException("Segment cannot contain path separators: " + segment);
        }
    }
    


    private static void validateSegment(NoteBytesReadOnly segment) {
        if (segment == null || segment.isEmpty()) {
            throw new IllegalArgumentException("Segment cannot be null or empty");
        }
        if(!ByteDecoding.isStringType(segment.getType())){
            throw new IllegalArgumentException("Segment must be a string type");
        }

        if (segment.containsBytes(forwardSlash) || segment.containsBytes(doubleBackSlash)) {
            throw new IllegalArgumentException("Segment cannot contain path separators: " + segment);
        }

    }

    public boolean containsPathTraversal() {
        NoteBytesReadOnly[] bytesArray = segments.getAsArray();
        for (NoteBytesReadOnly segment : bytesArray) {
            if (segment.containsBytes(forwardSlash) || 
                segment.containsBytes(doubleBackSlash)) {
                return true;
            }
        }
        return false;
    }

    /** Build string representation */
    private String buildPathString() {
        if (segments.isEmpty()) return "/";
        
        return "/" + segments.getAsString();
      
    }


    // === Delegated API ===

    public NoteStringArrayReadOnly getSegments() {
        return segments;
    }

    public NoteBytesReadOnly getRoot(){
        return segments.getRoot();
    }

    public String getRootString(){
        return segments.getRootString();
    }

    public NoteBytesReadOnly getLeaf(){
        return segments.getLeaf();
    }

    public String getLeafString(){
        return segments.getLeafString();
    }

    public int depth() {
        return segments.size();
    }

    public int size() {
        return segments.size();
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public boolean isRoot() {
        return segments.isEmpty();
    }
    
    public ContextPath getParent() {
        return parent();
    }

    public String getLastSegment(){
        return getLeafString();
    }

    public ContextPath parent() {
        if (isRoot()) return null;
        if (segments.size() == 1) return ROOT;
        
        // Maintain absolute/relative nature
        return new ContextPath(
            segments.subPath(0, segments.size() - 1)
        );
    }

    public String name() {
        if (isRoot()) return "";
        return getLeafString();
    }

    public ContextPath append(String segment) {
        validateSegment(segment);
        return new ContextPath(segments.append(segment));
    }

    public ContextPath append(NoteBytesReadOnly segment) {
        validateSegment(segment);
        return new ContextPath(segments.append(segment));
    }

    public ContextPath append(String... more) {
        for (String s : more) {
            validateSegment(s);
        }
        NoteStringArrayReadOnly next = segments.append(more);
        return new ContextPath(next);
    }

    public ContextPath append(ContextPath other) {
        if (other.isRoot()) return this;
        
        // When appending to absolute path, result is absolute
        // When appending to relative path, result is relative
        return new ContextPath(segments.concat(other.segments));
    }

    public boolean startsWith(ContextPath prefix) {
        return segments.startsWith(prefix.segments);
    }

    public boolean startsWith(String prefix) {
        return startsWith(parse(prefix));
    }

    public ContextPath subPath(int start, int end) {
        NoteStringArrayReadOnly sub = segments.subPath(start, end);

        return new ContextPath(sub);
    }

    public String getSegment(int segmentIndex) {
        return segments.getString(segmentIndex);
    }

    public List<ContextPath> ancestors() {
        List<ContextPath> result = new ArrayList<>();
        ContextPath current = this;
        while (current != null) {
            result.add(current);
            current = current.parent();
        }
        return result;
    }

    /**
     * Compute the relative path FROM the given base TO this path.
     * Returns null if this path doesn't start with the base.
     * 
     * Example:
     *   this = /a/b/c/d
     *   base = /a/b
     *   result = c/d (relative)
     *
     * @param base
     * @return relative ContextPath
     */
    public ContextPath relativeTo(ContextPath base) {
        if (base == null) return this;
        if (!this.startsWith(base)) return null;
        if (this.equals(base)) return ROOT;
        
        NoteStringArrayReadOnly relativeSeg = this.segments.subPath(
            base.segments.size(), 
            this.segments.size()
        );
        return new ContextPath(relativeSeg);  
    }


    public ContextPath resolve(String relativePath) {
        if (relativePath.startsWith("/")) return parse(relativePath);
        return append(parse(relativePath));
    }

    public boolean isAncestorOf(ContextPath other) {
        return other.startsWith(this) && !other.equals(this);
    }

    public boolean isDescendantOf(ContextPath other) {
        return this.startsWith(other) && !this.equals(other);
    }

    public boolean isSiblingOf(ContextPath other) {
        ContextPath thisParent = this.parent();
        ContextPath otherParent = other.parent();
        if (thisParent == null || otherParent == null) return false;
        return thisParent.equals(otherParent) && !this.equals(other);
    }

    public ContextPath commonAncestor(ContextPath other) {
        int min = Math.min(segments.size(), other.segments.size());
        int common = 0;
        for (int i = 0; i < min; i++) {
            if (segments.get(i).equals(other.segments.get(i))) common++;
            else break;
        }
        if (common == 0) return ROOT;
        
        
        return new ContextPath(segments.subPath(0, common));
    }

    /**
     * Can I reach targetPath from this path?
     * 
     * Uses hop-based validation:
     * 1. Find common ancestor
     * 2. Validate each hop UP to ancestor
     * 3. Validate each hop DOWN to target
     * 
     * Example:
     *   from: /system/nodes/runtime/database-node
     *   to:   /user/nodes/database-node/config.json
     *   
     *   Common ancestor: / (root)
     *   
     *   Hops UP:
     *     /system/nodes/runtime/database-node → /system/nodes/runtime ✓
     *     /system/nodes/runtime → /system/nodes ✓
     *     /system/nodes → /system ✓
     *     /system → / ✓
     *   
     *   Hops DOWN:
     *     / → /user ✓
     *     /user → /user/nodes ✓
     *     /user/nodes → /user/nodes/database-node (check ownership) ✓
     *     /user/nodes/database-node → .../config.json ✓
     * 
     * @param target Target path to reach
     * @return true if all hops allow traversal
     */
    public boolean canReach(ContextPath target) {
        if (target == null) {
            return false;
        }
        
        // Same path - always reachable
        if (this.equals(target)) {
            return true;
        }
        
        // Find common ancestor
        ContextPath ancestor = this.commonAncestor(target);
        
        // Build path UP from this to ancestor
        List<ContextPath> upPath = buildPathToAncestor(this, ancestor);
        
        // Build path DOWN from ancestor to target
        List<ContextPath> downPath = buildPathFromAncestor(ancestor, target);
        
        // Validate each hop UP
        for (int i = 0; i < upPath.size() - 1; i++) {
            ContextPath current = upPath.get(i);
            ContextPath parent = upPath.get(i + 1);
            
            if (!current.canTraverseToParent(parent, this)) {
                return false;
            }
        }
        
        // Validate each hop DOWN
        for (int i = 0; i < downPath.size() - 1; i++) {
            ContextPath current = downPath.get(i);
            ContextPath child = downPath.get(i + 1);
            
            if (!current.canTraverseToChild(child, this)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Build path from current to ancestor (going UP)
     * Returns list: [current, parent, grandparent, ..., ancestor]
     */
    private List<ContextPath> buildPathToAncestor(ContextPath current, ContextPath ancestor) {
        List<ContextPath> path = new ArrayList<>();
        
        ContextPath node = current;
        while (node != null && !node.equals(ancestor)) {
            path.add(node);
            node = node.parent();
        }
        
        // Add ancestor
        if (node != null) {
            path.add(node);
        }
        
        return path;
    }
    
    /**
     * Build path from ancestor to target (going DOWN)
     * Returns list: [ancestor, child, grandchild, ..., target]
     */
    private List<ContextPath> buildPathFromAncestor(ContextPath ancestor, ContextPath target) {
        List<ContextPath> path = new ArrayList<>();
        
        // Build from ancestor down to target
        if (ancestor.equals(target)) {
            path.add(ancestor);
            return path;
        }
        
        // Get relative path from ancestor to target
        ContextPath relative = target.relativeTo(ancestor);
        if (relative == null || relative.isRoot()) {
            path.add(ancestor);
            return path;
        }
        
        // Build each step down
        path.add(ancestor);
        ContextPath current = ancestor;
        
        for (int i = 0; i < relative.size(); i++) {
            current = current.append(relative.getSegment(i));
            path.add(current);
        }
        
        return path;
    }
    
    /**
     * Can I leave this location to go to parent?
     * 
     * Rules:
     * 1. System paths: generally can traverse up
     * 2. Node runtime: can go up within node system, but not to other nodes
     * 3. User paths: generally can traverse up
     * 
     * @param parent The parent path we want to traverse to
     * @param origin Where the traversal started (for context)
     * @return true if traversal allowed
     */
    private boolean canTraverseToParent(ContextPath parent, ContextPath origin) {
        // Root can always be reached
        if (parent.isRoot()) {
            return true;
        }
        
        // Check if we're in a node's runtime area
        if (this.startsWith(ContextPath.of("system", "nodes", "runtime"))) {
            String myNodeId = extractNodeId(this);
            String parentNodeId = extractNodeId(parent);
            
            // Can leave our node's runtime if going to general runtime area
            // or higher (not to another node's runtime)
            if (parentNodeId != null && !parentNodeId.equals(myNodeId)) {
                return false;  // Cannot traverse to other node's runtime
            }
        }
        
        // Check if we're in a node's user area
        if (this.startsWith(ContextPath.of("user", "nodes"))) {
            String myNodeId = extractNodeIdFromUser(this);
            String parentNodeId = extractNodeIdFromUser(parent);
            
            // Can leave our node's user area if going to general /user/nodes
            // or higher (not to another node's user area)
            if (parentNodeId != null && !parentNodeId.equals(myNodeId)) {
                return false;  // Cannot traverse to other node's user area
            }
        }
        
        // Default: can traverse up
        return true;
    }
    
    /**
     * Can I enter this child from current location?
     * 
     * Rules:
     * 1. Entering node runtime: only if origin owns it
     * 2. Entering node user area: only if origin owns it
     * 3. Entering packages: read-only allowed
     * 4. Other paths: generally allowed
     * 
     * @param child The child path we want to traverse to
     * @param origin Where the traversal started (for ownership checks)
     * @return true if traversal allowed
     */
    private boolean canTraverseToChild(ContextPath child, ContextPath origin) {
        // Entering another node's runtime area?
        if (child.startsWith(ContextPath.of("system", "nodes", "runtime"))) {
            String originNodeId = extractNodeId(origin);
            String targetNodeId = extractNodeId(child);
            
            // Only owner can enter
            if (targetNodeId != null) {
                if (originNodeId == null || !originNodeId.equals(targetNodeId)) {
                    return false;
                }
            }
        }
        
        // Entering another node's user area?
        if (child.startsWith(ContextPath.of("user", "nodes"))) {
            String originNodeId = extractNodeIdFromUser(origin);
            String targetNodeId = extractNodeIdFromUser(child);
            
            // Only owner can enter
            if (targetNodeId != null) {
                if (originNodeId == null || !originNodeId.equals(targetNodeId)) {
                    return false;
                }
            }
        }
        
        // Entering packages area - read-only allowed
        if (child.startsWith(ContextPath.of("system", "nodes", "packages"))) {
            // TODO: Could add write protection here
            return true;
        }
        
        // Default: can traverse down
        return true;
    }
    
    /**
     * Extract node ID from runtime path
     * /system/nodes/runtime/database-node/... → "database-node"
     */
    private String extractNodeId(ContextPath path) {
        if (!path.startsWith(ContextPath.of("system", "nodes", "runtime"))) {
            return null;
        }
        
        // Path: [system, nodes, runtime, <nodeId>, ...]
        if (path.size() > 3) {
            return path.getSegment(3);
        }
        
        return null;
    }
    
    /**
     * Extract node ID from user path
     * /user/nodes/database-node/... → "database-node"
     */
    private String extractNodeIdFromUser(ContextPath path) {
        if (!path.startsWith(ContextPath.of("user", "nodes"))) {
            return null;
        }
        
        // Path: [user, nodes, <nodeId>, ...]
        if (path.size() > 2) {
            return path.getSegment(2);
        }
        
        return null;
    }

    /** Wildcard matching (* and **) */
    public boolean matches(String pattern) {
        return matches(parse(pattern));
    }

    public boolean matches(ContextPath pattern) {
        return matchesRecursive(0, pattern, 0);
    }

    private boolean matchesRecursive(int pathIdx, ContextPath pattern, int patternIdx) {
        if (pathIdx >= segments.size() && patternIdx >= pattern.segments.size()) return true;
        if (patternIdx >= pattern.segments.size()) return false;

        String patternSeg = pattern.segments.getString(patternIdx);
        if ("**".equals(patternSeg)) {
            for (int i = pathIdx; i <= segments.size(); i++) {
                if (matchesRecursive(i, pattern, patternIdx + 1)) return true;
            }
            return false;
        }

        if (pathIdx >= segments.size()) return false;
        String pathSeg = segments.getString(pathIdx);

        if ("*".equals(patternSeg) || pathSeg.equals(patternSeg)) {
            return matchesRecursive(pathIdx + 1, pattern, patternIdx + 1);
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ContextPath other ){
            return segments.equals(other.segments);
        }else if(o instanceof NoteBytes noteBytes){
            return segments.equals(noteBytes);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    @Override
    public String toString() {
        return pathString;
    }

    public NoteStringArrayReadOnly toNoteBytes(){
        return segments;
    }

    /** Builder */
    public static class Builder {
        private final List<String> segments = new ArrayList<>();
        
        public Builder append(String seg) { 
            validateSegment(seg); 
            segments.add(seg); 
            return this; 
        }
        
        public Builder append(String... segs) { 
            for (String s : segs) append(s); 
            return this; 
        }
        
        public ContextPath build() { 
            if (segments.isEmpty()) return ROOT;
            
            NoteStringArrayReadOnly array = new NoteStringArrayReadOnly(
                segments.toArray(new String[0])
            );
            
            return new ContextPath(array);
        }
    }

    public static Builder builder() { 
        return new Builder(); 
    }
}