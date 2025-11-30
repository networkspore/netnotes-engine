package io.netnotes.engine.io;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

/**
 * ContextPath - Hierarchical path for organizing input sources.
 * 
 * ABSOLUTE vs RELATIVE PATHS:
 * 
 * Absolute Path:
 *   - Starts from system root
 *   - First segment is a root segment (system, user, etc.)
 *   - Example: /system/nodes/registry
 *   - String form: "/system/nodes/registry"
 *   - Created with: ContextPath.of("system", "nodes", "registry")
 * 
 * Relative Path:
 *   - Relative to some base path
 *   - Does NOT start with root segment
 *   - Example: config/settings
 *   - String form: "config/settings" (no leading /)
 *   - Created with: ContextPath.relative("config", "settings")
 * 
 * Root Path:
 *   - Special case: the root itself
 *   - String form: "/"
 *   - isEmpty() returns true
 *   - isAbsolute() returns true
 */
public final class ContextPath {
    public final static String DELIMITER = "/";
    private final NoteStringArrayReadOnly segments;
    private final String pathString;
    private final boolean isAbsolute;

    public static final ContextPath ROOT = new ContextPath(NoteStringArrayReadOnly.EMPTY, true);
    
    // Known root segments (absolute paths must start with one of these)
    private static final String[] ROOT_SEGMENTS = {"system", "user"};

    private ContextPath(NoteStringArrayReadOnly segments, boolean isAbsolute) {
        this.segments = segments;
        this.isAbsolute = isAbsolute;
        this.pathString = buildPathString();
    }

    public static ContextPath fromNoteBytes(NoteBytes noteBytes){
        if(noteBytes.getType() != NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE){
            throw new IllegalArgumentException("ContextPath must be constructed from Array NoteBytes");
        }
        NoteStringArrayReadOnly segments = new NoteStringArrayReadOnly(noteBytes.get());
        
        // Determine if absolute based on first segment
        boolean absolute = segments.isEmpty() || isRootSegment(segments.getRootString());
        
        return new ContextPath(segments, absolute); 
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
            boolean absolute = path.startsWith(DELIMITER) || 
                              (segments.size() > 0 && isRootSegment(segments.getRootString()));
            return new ContextPath(segments, absolute);
        }
        
        return parse(path);  // Regular parsing for internal use
    }

    /** Parse a path string into a ContextPath */
    public static ContextPath parse(String path) {
        if (path == null || path.isEmpty() || path.equals(DELIMITER)) return ROOT;

        // Check if path starts with delimiter (absolute)
        boolean startsWithDelimiter = path.startsWith(DELIMITER);
        
        // Parse segments
        NoteStringArrayReadOnly segments = NoteStringArrayReadOnly.parse(path, DELIMITER);
        
        // Determine if absolute:
        // 1. Starts with "/" in string form, OR
        // 2. First segment is a known root segment (system, user)
        boolean absolute = startsWithDelimiter || 
                          (segments.size() > 0 && isRootSegment(segments.getRootString()));
        
        return new ContextPath(segments, absolute);
    }

    /** Create ABSOLUTE path from varargs segments */
    public static ContextPath of(String... segments) {
        if (segments == null || segments.length == 0) return ROOT;
        List<String> valid = new ArrayList<>();
        for (String s : segments) {
            if (s != null && !s.isEmpty()) {
                validateSegment(s);
                valid.add(s);
            }
        }
        NoteStringArrayReadOnly array = new NoteStringArrayReadOnly(valid.toArray(new String[0]));
        
        // Absolute if first segment is a root segment
        boolean absolute = valid.size() > 0 && isRootSegment(valid.get(0));
        
        return new ContextPath(array, absolute);
    }

    public String[] getStringSegments(){
        return segments.getAsStringArray();
    }
    
    /** Create RELATIVE path from varargs segments */
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
        return new ContextPath(array, false);  // Explicitly relative
    }
    
    /**
     * Check if a segment is a known root segment
     */
    private static boolean isRootSegment(String segment) {
        for (String root : ROOT_SEGMENTS) {
            if (root.equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /** Segment validation */
    private static void validateSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            throw new IllegalArgumentException("Segment cannot be null or empty");
        }
        if (segment.contains("/") || segment.contains("\\")) {
            throw new IllegalArgumentException("Segment cannot contain path separators: " + segment);
        }
        // No dots allowed (prevents path traversal)
        if (segment.equals(".") || segment.equals("..")) {
            throw new IllegalArgumentException("Segment cannot be '.' or '..': " + segment);
        }
    }
    
    public static final NoteBytesReadOnly forwardSlash = new NoteBytesReadOnly("/");
    public static final NoteBytesReadOnly doubleBackSlash = new NoteBytesReadOnly("\\");
    public static final NoteBytesReadOnly dot = new NoteBytesReadOnly(".");
    public static final NoteBytesReadOnly doubleDot =  new NoteBytesReadOnly("..");


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
        
        // No dots allowed (prevents path traversal)
        if (segment.equals(dot) || segment.equals(doubleDot)) {
            throw new IllegalArgumentException("Segment cannot be '.' or '..': " + segment);
        }
    }

    public boolean containsPathTraversal() {
        NoteBytesReadOnly[] bytesArray = segments.getAsArray();
        for (NoteBytesReadOnly segment : bytesArray) {
            if (segment.equals(doubleDot) || 
                segment.equals(dot) || 
                segment.containsBytes(forwardSlash) || 
                segment.containsBytes(doubleBackSlash)) {
                return true;
            }
        }
        return false;
    }

    /** Build string representation */
    private String buildPathString() {
        if (segments.isEmpty()) return "/";
        
        // Absolute paths start with "/"
        // Relative paths don't
        if (isAbsolute) {
            return "/" + segments.getAsString();
        } else {
            return segments.getAsString();
        }
    }

    // === NEW API ===
    
    /**
     * Check if this is an absolute path
     * 
     * Absolute paths:
     * - Start with "/" in string form, OR
     * - First segment is a known root segment (system, user)
     * 
     * Examples:
     *   /system/nodes/registry → true
     *   system/nodes/registry  → true (starts with "system")
     *   config/settings        → false (relative)
     *   /                      → true (root)
     */
    public boolean isAbsolute() {
        return isAbsolute;
    }
    
    /**
     * Check if this is a relative path
     * 
     * Relative paths:
     * - Do NOT start with "/" in string form
     * - First segment is NOT a root segment
     * 
     * Examples:
     *   config/settings        → true
     *   temp/upload            → true
     *   system/nodes           → false (starts with root segment)
     */
    public boolean isRelative() {
        return !isAbsolute;
    }
    
    /**
     * Convert to absolute path by prepending base path
     * 
     * Only works if this is a relative path.
     * 
     * Example:
     *   relative: config/settings
     *   base: /system/nodes/runtime/my-node
     *   result: /system/nodes/runtime/my-node/config/settings
     * 
     * @param basePath Base path to prepend (must be absolute)
     * @return Absolute path
     * @throws IllegalArgumentException if this is already absolute or base is relative
     */
    public ContextPath toAbsolute(ContextPath basePath) {
        if (isAbsolute) {
            throw new IllegalArgumentException(
                "Cannot convert absolute path to absolute: " + this);
        }
        
        if (basePath == null || !basePath.isAbsolute()) {
            throw new IllegalArgumentException(
                "Base path must be absolute: " + basePath);
        }
        
        return basePath.append(this);
    }
    
    /**
     * Convert to relative path by removing base prefix
     * 
     * Only works if this path starts with the base path.
     * 
     * Example:
     *   absolute: /system/nodes/runtime/my-node/config/settings
     *   base: /system/nodes/runtime/my-node
     *   result: config/settings (relative)
     * 
     * @param basePath Base path to remove (must be absolute)
     * @return Relative path
     * @throws IllegalArgumentException if this doesn't start with base
     */
    public ContextPath toRelative(ContextPath basePath) {
        if (!isAbsolute) {
            throw new IllegalArgumentException(
                "Cannot convert relative path to relative: " + this);
        }
        
        if (basePath == null || !basePath.isAbsolute()) {
            throw new IllegalArgumentException(
                "Base path must be absolute: " + basePath);
        }
        
        if (!this.startsWith(basePath)) {
            throw new IllegalArgumentException(
                "Path " + this + " does not start with base " + basePath);
        }
        
        if (this.equals(basePath)) {
            throw new IllegalArgumentException(
                "Cannot make path relative to itself");
        }
        
        // Create relative path from remaining segments
        NoteStringArrayReadOnly relativeSeg = segments.subPath(
            basePath.segments.size(), 
            segments.size()
        );
        
        return new ContextPath(relativeSeg, false);  // Explicitly relative
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
            segments.subPath(0, segments.size() - 1), 
            isAbsolute
        );
    }

    public String name() {
        if (isRoot()) return "";
        return getLeafString();
    }

    public ContextPath append(String segment) {
        validateSegment(segment);
        return new ContextPath(segments.append(segment), isAbsolute);
    }

    public ContextPath append(NoteBytesReadOnly segment) {
        validateSegment(segment);
        return new ContextPath(segments.append(segment), isAbsolute);
    }

    public ContextPath append(String... more) {
        for (String s : more) {
            validateSegment(s);
        }
        NoteStringArrayReadOnly next = segments.append(more);
        return new ContextPath(next, isAbsolute);
    }

    public ContextPath append(ContextPath other) {
        if (other.isRoot()) return this;
        
        // When appending to absolute path, result is absolute
        // When appending to relative path, result is relative
        return new ContextPath(segments.concat(other.segments), isAbsolute);
    }

    public boolean startsWith(ContextPath prefix) {
        return segments.startsWith(prefix.segments);
    }

    public boolean startsWith(String prefix) {
        return startsWith(parse(prefix));
    }

    public ContextPath subPath(int start, int end) {
        NoteStringArrayReadOnly sub = segments.subPath(start, end);
        
        // If original is absolute and we're taking from start (0),
        // result is absolute. Otherwise relative.
        boolean resultAbsolute = (isAbsolute && start == 0);
        
        return new ContextPath(sub, resultAbsolute);
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
        
        return new ContextPath(relativeSeg, false);  // Relative path
    }

    public ContextPath relativize(ContextPath target) {
        if (!target.startsWith(this)) return null;
        if (target.equals(this)) return ROOT;
        
        NoteStringArrayReadOnly relativeSeg = target.segments.subPath(
            segments.size(), 
            target.segments.size()
        );
        
        return new ContextPath(relativeSeg, false);  // Relative path
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
        
        // Common ancestor maintains absolute nature if both are absolute
        boolean resultAbsolute = isAbsolute && other.isAbsolute;
        
        return new ContextPath(segments.subPath(0, common), resultAbsolute);
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
        
        // Both must be absolute for hop validation
        if (!this.isAbsolute() || !target.isAbsolute()) {
            return false;
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
            return segments.equals(other.segments) && isAbsolute == other.isAbsolute;
        }else if(o instanceof NoteBytes noteBytes){
            return segments.equals(noteBytes);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * segments.hashCode() + (isAbsolute ? 1 : 0);
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
        private boolean isAbsolute = true;  // Default to absolute
        
        public Builder absolute() { 
            isAbsolute = true; 
            return this; 
        }
        
        public Builder relative() { 
            isAbsolute = false; 
            return this; 
        }
        
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
            
            // If building absolute and first segment isn't a root segment, error
            if (isAbsolute && !isRootSegment(segments.get(0))) {
                throw new IllegalArgumentException(
                    "Absolute path must start with root segment (system, user): " + 
                    segments.get(0));
            }
            
            // If building relative and first segment IS a root segment, error
            if (!isAbsolute && isRootSegment(segments.get(0))) {
                throw new IllegalArgumentException(
                    "Relative path cannot start with root segment: " + segments.get(0));
            }
            
            return new ContextPath(array, isAbsolute);
        }
    }

    public static Builder builder() { 
        return new Builder(); 
    }
}