package io.netnotes.engine.noteBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteStringArray extends NoteBytesArray {

    public final static String DELIMITER = "/";

    private String m_delimiter = new String(DELIMITER);

    public NoteStringArray(){
        super();
    }

    public NoteStringArray(byte[] bytes){
        super();
        set(bytes);
    }

    public NoteStringArray(String... str){
        this(stringArrayToNoteBytes(str));
        set(str);
    }

    public NoteStringArray(NoteBytes... noteBytes){
        super();
    }

    @Override
    public NoteStringArray copy(){
        return new NoteStringArray(get());
    }

   public static NoteBytes[] stringArrayToNoteBytes(String[] array){
        NoteBytes[] noteBytes = new NoteBytes[array.length];
        for(int i = 0; i < array.length ; i++){
            noteBytes[i] = new NoteBytes(array[i]);
        }
        return noteBytes;
    }

    public void set(String[] array){
        NoteBytes[] intermediary = new NoteBytes[array.length];
        int byteLength = 0;
        int arrayLength = array.length;
        for(int i = 0; i < arrayLength ; i++){
            intermediary[i] = new NoteBytes(array[i]);
            byteLength +=(5 + intermediary[i].byteLength());
        }
   
        ensureCapacity(byteLength);

        byte[] bytes = getBytesInternal();
        int offset = 0;
        for(int i = 0; i < arrayLength ; i++){
            NoteBytes src = intermediary[i];
            offset += NoteBytes.writeNote(src, bytes, offset);
        }
        setInternalLength(byteLength);
    }


    public static String noteBytesArrayToUrl(NoteBytes[] array, String delim) {
        String[] str = new String[array.length];
        for (int i = 0; i < array.length; i++) {
            str[i] = ByteDecoding.UrlEncode(array[i].getAsString());
        }
        return String.join(delim, str);
    }

    public static NoteBytes[] stringToArray(String path) {
        return stringToArray(path, NoteBytesMetaData.STRING_TYPE, DELIMITER);
    }

    public static NoteBytes[] stringToArray(String path, String delim) {
        return stringToArray(path, NoteBytesMetaData.STRING_TYPE, delim);
    }

    public static NoteBytes[] stringToArray(String path, byte type, String delim) {
        String[] parts = path.split(Pattern.quote(delim), -1);
        NoteBytes[] result = new NoteBytes[parts.length];
        
        for (int i = 0; i < parts.length; i++) {
            result[i] = new NoteBytes(ByteDecoding.UrlDecode(parts[i], type));
        }
        return result;
    }

    public void setDelimiter(String delim){
        m_delimiter = delim;
    }

    public String getDelimiter(){
        return m_delimiter;
    }

    @Override
    public String getAsString(){
        NoteBytes[] array = getAsArray();
        return noteBytesArrayToUrl(array, m_delimiter);
    }

    public String getAsString(String delimiter){
        NoteBytes[] array = getAsArray();
        return noteBytesArrayToUrl(array, delimiter);
    }

    public NoteBytes remove(String item){
        return remove(new NoteBytes(item));
    }

    public void add(String str){
        add(new NoteBytes(str));
    }

    public boolean contains(String string){
        return indexOf(string) != -1;
    }

    public int indexOf(String str){
        return indexOf(new NoteBytes(str));
    }

    public String[] getAsStringArray(){
        int size = size();
        String[] arr = new String[size];
        byte[] bytes = getBytesInternal();
        int length = byteLength();
        int offset = 0;
        int i = 0;
        while(offset < length){
            NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
            arr[i] = noteBytes.getAsString();
            i++;
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return arr;
    }

    public List<String> getAsStringList(){
         return Arrays.asList(getAsStringArray());
    }


    public Stream<String> getAsStringStream(){
        Stream.Builder<String> noteBytesBuilder = Stream.builder();

        byte[] bytes = getBytesInternal();
        int length = byteLength();
        int offset = 0;
        while(offset < length){
            NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
            noteBytesBuilder.accept(noteBytes.getAsString());
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return noteBytesBuilder.build();
    }

    /**
     * Add a string with automatic trimming
     */
    public void addTrimmed(String str) {
        if (str != null) {
            String trimmed = str.trim();
            if (!trimmed.isEmpty()) {
                add(trimmed);
            }
        }
    }

    /**
     * Add multiple strings with automatic trimming
     */
    public void addTrimmed(String... strings) {
        for (String str : strings) {
            addTrimmed(str);
        }
    }

    /**
     * Create a NoteStringArray from a path string, splitting on delimiter
     * Automatically trims each segment and skips empty ones
     */
    public static NoteStringArray fromPath(String pathString) {
        return fromPath(pathString, DELIMITER);
    }

    /**
     * Create a NoteStringArray from a path string with custom delimiter
     */
    public static NoteStringArray fromPath(String pathString, String delimiter) {
        if (pathString == null || pathString.isEmpty()) {
            return new NoteStringArray();
        }
        
        String[] parts = pathString.split(Pattern.quote(delimiter));
        List<String> validSegments = new ArrayList<>();
        
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                validSegments.add(trimmed);
            }
        }
        
        return new NoteStringArray(validSegments.toArray(new String[0]));
    }

    /**
     * Get a specific segment by index
     */
    public String getString(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size " + size());
        }
        return super.get(index).getAsString();
    }

    /**
     * Get the first segment (root)
     */
    public String getRootString() {
        return size() > 0 ? getString(0) : null;
    }

    /**
     * Get the last segment (leaf)
     */
    public String getLeafString() {
        return size() > 0 ? getString(size() - 1) : null;
    }

    /**
     * Check if this path starts with the given prefix
     */
    public boolean startsWith(NoteStringArray prefix) {
        if (prefix.size() > this.size()) {
            return false;
        }
        
        for (int i = 0; i < prefix.size(); i++) {
            if (!this.get(i).equals(prefix.get(i))) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Check if this path starts with the given prefix string
     */
    public boolean startsWith(String prefixPath) {
        return startsWith(fromPath(prefixPath, m_delimiter));
    }

    /**
     * Create a new path with an additional segment appended
     */
    public NoteStringArray append(String segment) {
        if (segment == null || segment.trim().isEmpty()) {
            return this;
        }
        
        String[] current = getAsStringArray();
        String[] newArray = new String[current.length + 1];
        System.arraycopy(current, 0, newArray, 0, current.length);
        newArray[current.length] = segment.trim();
        
        NoteStringArray result = new NoteStringArray(newArray);
        result.setDelimiter(m_delimiter);
        return result;
    }

    /**
     * Create a new path with multiple segments appended
     */
    public NoteStringArray append(String... segments) {
        if (segments == null || segments.length == 0) {
            return this;
        }
        
        String[] current = getAsStringArray();
        List<String> valid = new java.util.ArrayList<>();
        for (String seg : segments) {
            if (seg != null) {
                String trimmed = seg.trim();
                if (!trimmed.isEmpty()) {
                    valid.add(trimmed);
                }
            }
        }
        
        if (valid.isEmpty()) {
            return this;
        }
        
        String[] newArray = new String[current.length + valid.size()];
        System.arraycopy(current, 0, newArray, 0, current.length);
        for (int i = 0; i < valid.size(); i++) {
            newArray[current.length + i] = valid.get(i);
        }
        
        NoteStringArray result = new NoteStringArray(newArray);
        result.setDelimiter(m_delimiter);
        return result;
    }

    /**
     * Get the parent path (all segments except the last)
     * Returns null if this is a root path (single segment)
     */
    public NoteStringArray getParent() {
        if (size() <= 1) {
            return null;
        }
        
        String[] current = getAsStringArray();
        String[] parentArray = new String[current.length - 1];
        System.arraycopy(current, 0, parentArray, 0, parentArray.length);
        
        NoteStringArray result = new NoteStringArray(parentArray);
        result.setDelimiter(m_delimiter);
        return result;
    }

    /**
     * Get a sub-path starting from the given index
     */
    public NoteStringArray subPath(int fromIndex) {
        if (fromIndex < 0 || fromIndex >= size()) {
            return new NoteStringArray();
        }
        
        String[] current = getAsStringArray();
        String[] subArray = new String[current.length - fromIndex];
        System.arraycopy(current, fromIndex, subArray, 0, subArray.length);
        
        NoteStringArray result = new NoteStringArray(subArray);
        result.setDelimiter(m_delimiter);
        return result;
    }

    /**
     * Get a sub-path from start (inclusive) to end (exclusive)
     */
    public NoteStringArray subPath(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > size() || fromIndex >= toIndex) {
            return new NoteStringArray();
        }
        
        String[] current = getAsStringArray();
        String[] subArray = new String[toIndex - fromIndex];
        System.arraycopy(current, fromIndex, subArray, 0, subArray.length);
        
        NoteStringArray result = new NoteStringArray(subArray);
        result.setDelimiter(m_delimiter);
        return result;
    }

    /**
     * Get the depth of this path (number of segments)
     */
    public int depth() {
        return size();
    }

    /**
     * Check if path matches a pattern with wildcards
     * * matches any single segment
     * ** matches any number of segments
     */
    public boolean matches(String pattern) {
        String[] patternSegments = pattern.split(Pattern.quote(m_delimiter));
        
        int pathIdx = 0;
        int patternIdx = 0;
        
        while (pathIdx < size() && patternIdx < patternSegments.length) {
            String patternSeg = patternSegments[patternIdx].trim();
            
            if (patternSeg.equals("**")) {
                // Greedy match - try to match rest of pattern with rest of path
                if (patternIdx == patternSegments.length - 1) {
                    return true; // ** at end matches everything
                }
                
                // Try to match remaining pattern at each position
                for (int i = pathIdx; i < size(); i++) {
                    String[] remainingPattern = java.util.Arrays.copyOfRange(
                        patternSegments, patternIdx + 1, patternSegments.length);
                    String remainingPatternStr = String.join(m_delimiter, remainingPattern);
                    
                    NoteStringArray remainingPath = subPath(i);
                    if (remainingPath.matches(remainingPatternStr)) {
                        return true;
                    }
                }
                return false;
            } else if (patternSeg.equals("*") || patternSeg.equals(getString(pathIdx))) {
                pathIdx++;
                patternIdx++;
            } else {
                return false;
            }
        }
        
        return pathIdx == size() && patternIdx == patternSegments.length;
    }

    /**
     * Join segments with a specific delimiter (overriding the default)
     */
    public String toPathString(String delimiter) {
        return getAsString(delimiter);
    }

    /**
     * Join segments with the default delimiter as a path string
     */
    public String toPathString() {
        return getAsString();
    }

    /**
     * Trim all segments in place
     */
    public void trimAll() {
        String[] current = getAsStringArray();
        boolean needsUpdate = false;
        
        for (int i = 0; i < current.length; i++) {
            String trimmed = current[i].trim();
            if (!trimmed.equals(current[i])) {
                current[i] = trimmed;
                needsUpdate = true;
            }
        }
        
        if (needsUpdate) {
            // Rebuild from trimmed strings
            clear();
            for (String segment : current) {
                if (!segment.isEmpty()) {
                    add(segment);
                }
            }
        }
    }

    /**
     * Check if the path is empty (no segments)
     */
    public boolean isEmpty() {
        return size() == 0;
    }

}
