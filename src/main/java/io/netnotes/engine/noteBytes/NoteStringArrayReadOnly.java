package io.netnotes.engine.noteBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.CollectionHelpers;

public class NoteStringArrayReadOnly extends NoteBytesArrayReadOnly {
    public static final NoteStringArrayReadOnly EMPTY = new NoteStringArrayReadOnly();
    private String m_delimiter = NoteStringArray.DELIMITER;


    public NoteStringArrayReadOnly(NoteBytes... noteBytes){
        super(noteBytes);
    }

    public NoteStringArrayReadOnly(String[] strings, boolean urlEncoded) {
    super(urlEncoded ? decodeStrings(strings) : stringArrayToNoteBytes(strings));
}

    public NoteStringArrayReadOnly(byte[] bytes){
        super(Arrays.copyOf(bytes, bytes.length));
    }

    public NoteStringArrayReadOnly(){
        super(new byte[0]);
    }

    public NoteStringArrayReadOnly(String... str){
        super(stringArrayToNoteBytes(str));
    }

    public NoteStringArrayReadOnly(List<String> list){
        this(list.toArray(new String[0]));
    }

    public static NoteBytes[] stringArrayToNoteBytes(String[] array){
        NoteBytes[] noteBytes = new NoteBytes[array.length];
        for(int i = 0; i < array.length ; i++){
            noteBytes[i] = new NoteBytes(array[i]);
        }
        return noteBytes;
    }

    private static NoteBytes[] decodeStrings(String[] encoded) {
        NoteBytes[] result = new NoteBytes[encoded.length];
        for (int i = 0; i < encoded.length; i++) {
            result[i] = new NoteBytes(ByteDecoding.UrlDecode(encoded[i], NoteBytesMetaData.STRING_TYPE));
        }
        return result;
    }



    public NoteStringArrayReadOnly copy(){
        return new NoteStringArrayReadOnly(getBytesInternal());
    }
    


    public void setDelimiter(String delim){
        m_delimiter = delim;
    }

    public String getDelimiter(){
        return m_delimiter;
    }

    public String getAsString(){
        String[] array = getAsStringArray();
        return NoteStringArray.stringArrayToString(array, m_delimiter);
    }

    public String getAsUrlEncodedString(){
        NoteBytes[] array = getAsArray();
        return NoteStringArray.noteBytesArrayToUrlEncodedString(array, m_delimiter);
    }

    public String getAsString(String delimiter){
        String[] array = getAsStringArray();
        return NoteStringArray.stringArrayToString(array, delimiter);
    }

    @Override
    public String toString(){
        return getAsString();
    }

    public boolean contains(String string){
        return  indexOf(new NoteBytes(string)) != -1;
    }
    

    public NoteBytesReadOnly get(int index){
        return getAt(index);
    }

    public NoteBytesReadOnly getAt(int index){
     
        byte[] bytes = getBytesInternal();
        int length = bytes.length;
        int offset = 0;
        int counter = 0;

        while(offset < length){
            byte type = bytes[offset];
            offset++;
            int size = ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += 4;
            if(counter == index){
                byte[] dst = new byte[size];
                System.arraycopy(bytes, offset, dst, 0, size);
                return NoteBytesReadOnly.of(dst, type);
            }
            offset += size;
            counter++;
        }
        return null;
        
    }


   public String[] getAsStringArray(){
        int size = size();
        String[] arr = new String[size];
        byte[] bytes = super.getBytesInternal();
        int length = bytes.length;
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

        byte[] bytes = super.getBytesInternal();
        int length = bytes.length;
        int offset = 0;
        while(offset < length){
            NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
            noteBytesBuilder.accept(noteBytes.getAsString());
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return noteBytesBuilder.build();
    }


    /**
     * Create a NoteStringArrayReadOnly from a path string, splitting on delimiter
     * Automatically trims each segment and skips empty ones
     */
    public static NoteStringArrayReadOnly fromPath(String pathString) {
        return fromPath(pathString, NoteStringArray.DELIMITER);
    }

    /**
     * Create a NoteStringArrayReadOnly from a path string with custom delimiter
     */
    public static NoteStringArrayReadOnly fromPath(String pathString, String delimiter) {
        if (pathString == null || pathString.isEmpty()) {
            return new NoteStringArrayReadOnly();
        }
        
        String[] parts = pathString.split(Pattern.quote(delimiter));
        List<String> validSegments = new ArrayList<>();
        
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                validSegments.add(trimmed);
            }
        }
        
        return new NoteStringArrayReadOnly(validSegments.toArray(new String[0]));
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
        NoteBytes first = getFirst();
        return first != null ? first.getAsString() : null;
    }

    public NoteBytesReadOnly getRoot(){
        return super.getFirst();
    }

    /**
     * Get the last segment (leaf)
     */
    public String getLeafString() {
        NoteBytesReadOnly last = getLast();
        return last != null ? last.getAsString() : null;
    }

    public NoteBytesReadOnly getLeaf(){
        return getLast();
    }

    /**
     * Check if this path starts with the given prefix
     */
    public boolean startsWith(NoteStringArrayReadOnly prefix) {
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
     * Check if this path starts with the given prefix
     */
    public boolean startsWith(NoteBytesReadOnly prefix) {
        if (this.byteLength() == 0) {
            return false;
        }
        return this.get(0).equals(prefix);
    }


    /**
     * Check if this path starts with the given prefix string
     */
    public boolean startsWith(String prefixPath) {
        return startsWith(fromPath(prefixPath, m_delimiter));
    }

    public NoteStringArrayReadOnly append(String segment) {
        return NoteStringArrayReadOnly.append(this, segment, m_delimiter);
    }

      /**
     * Create a new path with an additional segment appended
     */
     public static NoteStringArrayReadOnly append(NoteStringArrayReadOnly base, String segment, String delimiter) {
        if (segment == null || segment.length() == 0) {
            return base;
        }

        NoteStringArrayReadOnly parsed = parse(segment, delimiter);
        int baseLength = base.byteLength();
        if(baseLength > 0){
            
            byte[] bytes = new byte[baseLength + parsed.byteLength()];

            System.arraycopy(base.get(), 0, bytes, 0, baseLength);
            System.arraycopy(parsed.get(), 0, bytes, baseLength, parsed.byteLength());
            return new NoteStringArrayReadOnly(bytes);
        }
        return parsed;
    }

     public static NoteStringArrayReadOnly concat(NoteStringArrayReadOnly a, NoteStringArrayReadOnly b) {
        if (b.isEmpty()) {
            return a;
        }
      
        if(a.byteLength() > 0){
            
            byte[] bytes = new byte[a.byteLength() + b.byteLength()];

            System.arraycopy(a.get(), 0, bytes, 0, a.byteLength());
            System.arraycopy(b.get(), 0, bytes, a.byteLength(), b.byteLength());
            return new NoteStringArrayReadOnly(bytes);
        }
        return b;
    }

    @Override
    public int hashCode(){
        return Arrays.hashCode(get());
    }

    /**
     * Create a new path with multiple segments appended
     */
    public NoteStringArrayReadOnly append(String... segments) {
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
        
        NoteStringArrayReadOnly result = new NoteStringArrayReadOnly(newArray);
        result.setDelimiter(m_delimiter);
        return result;
    }

    /**
     * Get the parent path (all segments except the last)
     * Returns null if this is a root path (single segment)
     */
    public NoteStringArrayReadOnly getParent() {
        if (size() <= 1) {
            return null;
        }
        
        String[] current = getAsStringArray();
        String[] parentArray = new String[current.length - 1];
        System.arraycopy(current, 0, parentArray, 0, parentArray.length);
        
        NoteStringArrayReadOnly result = new NoteStringArrayReadOnly(parentArray);
        result.setDelimiter(m_delimiter);
        return result;
    }

    /**
     * Get a sub-path starting from the given index
     */
    public NoteStringArrayReadOnly subPath(int fromIndex) {
        if (fromIndex < 0 || fromIndex >= size()) {
            return new NoteStringArrayReadOnly();
        }
        
        String[] current = getAsStringArray();
        String[] subArray = new String[current.length - fromIndex];
        System.arraycopy(current, fromIndex, subArray, 0, subArray.length);
        
        NoteStringArrayReadOnly result = new NoteStringArrayReadOnly(subArray);
        result.setDelimiter(m_delimiter);
        return result;
    }

    /**
     * Get a sub-path from start (inclusive) to end (exclusive)
     */
    public NoteStringArrayReadOnly subPath(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > size() || fromIndex >= toIndex) {
            return new NoteStringArrayReadOnly();
        }
        
        String[] current = getAsStringArray();
        String[] subArray = new String[toIndex - fromIndex];
        System.arraycopy(current, fromIndex, subArray, 0, subArray.length);
        
        NoteStringArrayReadOnly result = new NoteStringArrayReadOnly(subArray);
        result.setDelimiter(m_delimiter);
        return result;
    }

    public NoteStringArrayReadOnly concat(NoteStringArrayReadOnly readonly){
        byte[] readOnlyBytes = readonly.get();
        return new NoteStringArrayReadOnly(CollectionHelpers.appendBytes(get(), readOnlyBytes));
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
                    
                    NoteStringArrayReadOnly remainingPath = subPath(i);
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
     * Trim all segments in place
     */
    public NoteStringArrayReadOnly trimAll() {
        String[] current = getAsStringArray();

        
        for (int i = 0; i < current.length; i++) {
            String trimmed = current[i].trim();
            if (!trimmed.equals(current[i])) {
                current[i] = trimmed;
            }
        }
        
        return new NoteStringArrayReadOnly(current);
    }

    /**
     * Check if the path is empty (no segments)
     */
    public boolean isEmpty() {
        return size() == 0;
    }
    public static NoteStringArrayReadOnly parse(String path, String delim){
        String normalized = path.trim();
        normalized = normalized.startsWith(delim) ? normalized = normalized.substring(1) : normalized;
        normalized = normalized.endsWith(delim) ? normalized = normalized.substring(0, normalized.length() - 1) : normalized;
        if (normalized.isEmpty()) return EMPTY;

        String[] parts = normalized.split(delim);
        List<String> clean = new ArrayList<>();
        for (String p : parts) {
            clean.add(p);
        }
        return new NoteStringArrayReadOnly(clean);
    }
}
