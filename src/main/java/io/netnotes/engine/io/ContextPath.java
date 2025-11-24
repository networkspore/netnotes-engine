package io.netnotes.engine.io;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

/**
 * ContextPath - Hierarchical path for organizing input sources.
 */
public final class ContextPath {
    public final static String DELIMITER = "/";
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
            return new ContextPath(new NoteStringArrayReadOnly(decoded.toArray(new String[0])));
        }
        
        return parse(path);  // Regular parsing for internal use
    }


    /** Parse a path string into a ContextPath */
    public static ContextPath parse(String path) {
        if (path == null || path.isEmpty() || path.equals(DELIMITER)) return ROOT;

        return new ContextPath(NoteStringArrayReadOnly.parse(path, DELIMITER));
    }

    /** Create from varargs segments */
    public static ContextPath of(String... segments) {
        if (segments == null || segments.length == 0) return ROOT;
        List<String> valid = new ArrayList<>();
        for (String s : segments) {
            if (s != null && !s.isEmpty()) {
                validateSegment(s);
                valid.add(s);
            }
        }
        return new ContextPath(new NoteStringArrayReadOnly(valid.toArray(new String[0])));
    }

    /** Segment validation */
    private static void validateSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            throw new IllegalArgumentException("Segment cannot be null or empty");
        }
        if (segment.contains("/") || segment.contains("\\")) {
            throw new IllegalArgumentException("Segment cannot contain path separators: " + segment);
        }

    }
    private static final byte[] forwardSlash = "/".getBytes();
    private static final byte[] doubleBackSlash = "\\".getBytes();

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
        return new ContextPath(segments.subPath(0, segments.size() - 1));
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
        NoteStringArrayReadOnly next = segments.append(more);
        return new ContextPath(next);
    }

    public ContextPath append(ContextPath other) {
        if (other.isRoot()) return this;
        return new ContextPath(segments.concat(other.segments));
    }

    public boolean startsWith(ContextPath prefix) {
        return segments.startsWith(prefix.segments);
    }

    public boolean startsWith(String prefix) {
        return startsWith(parse(prefix));
    }

    public ContextPath subPath(int start, int end) {
        return new ContextPath(segments.subPath(start, end));
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
     *   result = c/d
     *
     * @param base
     * @return relative ContextPath
     */
    public ContextPath relativeTo(ContextPath base) {
        if (base == null) return this;
        if (!this.startsWith(base)) return null;
        if (this.equals(base)) return ROOT;
        return new ContextPath(this.segments.subPath(base.segments.size(), this.segments.size()));
    }

    public ContextPath relativize(ContextPath target) {
        if (!target.startsWith(this)) return null;
        if (target.equals(this)) return ROOT;
        return new ContextPath(target.segments.subPath(segments.size(), target.segments.size()));
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
        if (!(o instanceof ContextPath)) return false;
        ContextPath other = (ContextPath) o;
        return segments.equals(other.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    @Override
    public String toString() {
        return pathString;
    }

    /** Builder */
    public static class Builder {
        private final List<String> segments = new ArrayList<>();
        public Builder append(String seg) { validateSegment(seg); segments.add(seg); return this; }
        public Builder append(String... segs) { for (String s : segs) append(s); return this; }
        public ContextPath build() { return new ContextPath(new NoteStringArrayReadOnly(segments.toArray(new String[0]))); }
    }

    public static Builder builder() { return new Builder(); }
}
