package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

/**
 * NoteIntegerArray stores Unicode code points as raw 4-byte integers.
 */
public class NoteIntegerArray extends NoteBytes {

    public static final NoteIntegerArray EMPTY = new NoteIntegerArray();

    public NoteIntegerArray(byte[] bytes) {
        super(bytes, NoteBytesMetaData.NOTE_INTEGER_ARRAY_TYPE);
    }

    public NoteIntegerArray() {
        this(new byte[0]);
    }

    public NoteIntegerArray(String str) {
        this(ByteDecoding.stringToCodePointBytes(str));
    }

    public NoteIntegerArray(int[] codePoints) {
        this(ByteDecoding.codePointsToBytes(codePoints));
    }

    public int[] getAsCodePoints(){
        return ByteDecoding.bytesToCodePoints(get());
    }

    public int[] getAsArray(){
        return getAsCodePoints();
    }

    public Integer[] getAsBoxedArray(){
        return ByteDecoding.bytesToBoxedIntegers(getBytes());
    }

    public List<Integer> getAsList(){
        return Arrays.asList(getAsBoxedArray());  
    }

    public int size(){
        return byteLength() == 0? 0 : byteLength() / 4;
    }

    @Override
    public String toString() {
        int[] codePoints = getAsCodePoints();
        if (codePoints.length == 0) {
            return "";
        }
        return new String(codePoints, 0, codePoints.length);
    }

    // ========== StringBuilder-like Methods ==========

    public NoteIntegerArray append(String str) {
        if (str == null || str.isEmpty()) {
            return this;
        }

        byte[] newBytes = ByteDecoding.stringToCodePointBytes(str);
        byte[] current = get();
        byte[] result = new byte[current.length + newBytes.length];

        System.arraycopy(current, 0, result, 0, current.length);
        System.arraycopy(newBytes, 0, result, current.length, newBytes.length);

        set(result);
        return this;
    }

    public NoteIntegerArray append(NoteIntegerArray other) {
        if (other == null || other.length() == 0) {
            return this;
        }

        byte[] current = get();
        byte[] otherBytes = other.get();
        byte[] result = new byte[current.length + otherBytes.length];

        System.arraycopy(current, 0, result, 0, current.length);
        System.arraycopy(otherBytes, 0, result, current.length, otherBytes.length);

        set(result);
        return this;
    }

    public NoteIntegerArray appendCodePoint(int codePoint) {
        byte[] current = get();
        byte[] result = new byte[current.length + ByteDecoding.CODE_POINT_BYTE_SIZE];

        System.arraycopy(current, 0, result, 0, current.length);
        ByteDecoding.intToBytes(codePoint, result, current.length);
        set(result);
        return this;
    }

    public NoteIntegerArray add(int i){
        return appendCodePoint(i);
    }

    public boolean insert(int index, String str) {
        if (str == null || str.isEmpty()) {
            return true;
        }

        if (index < 0 || index > length()) {
            return false;
        }

        byte[] insertBytes = ByteDecoding.stringToCodePointBytes(str);
        byte[] current = get();
        int byteIndex = index * ByteDecoding.CODE_POINT_BYTE_SIZE;

        byte[] result = new byte[current.length + insertBytes.length];

        System.arraycopy(current, 0, result, 0, byteIndex);
        System.arraycopy(insertBytes, 0, result, byteIndex, insertBytes.length);
        System.arraycopy(current, byteIndex, result, byteIndex + insertBytes.length, 
                        current.length - byteIndex);

        set(result);
        return true;
    }

    public boolean insertCodePoint(int index, int codePoint) {
        if (index < 0 || index > length()) {
            return false;
        }

        byte[] current = get();
        int byteIndex = index * ByteDecoding.CODE_POINT_BYTE_SIZE;
        byte[] result = new byte[current.length + ByteDecoding.CODE_POINT_BYTE_SIZE];

        System.arraycopy(current, 0, result, 0, byteIndex);
        byte[] cpBytes = ByteDecoding.intToBytesBigEndian(codePoint);
        System.arraycopy(cpBytes, 0, result, byteIndex, ByteDecoding.CODE_POINT_BYTE_SIZE);
        System.arraycopy(current, byteIndex, result, byteIndex + ByteDecoding.CODE_POINT_BYTE_SIZE, 
                        current.length - byteIndex);

        set(result);
        return true;
    }

    public boolean delete(int start, int end) {
        int len = length();
        if (start < 0 || start > end || end > len) {
            return false;
        }

        if (start == end) {
            return true;
        }

        byte[] current = get();
        int startByte = start * ByteDecoding.CODE_POINT_BYTE_SIZE;
        int endByte = end * ByteDecoding.CODE_POINT_BYTE_SIZE;
        int newLength = current.length - (endByte - startByte);

        byte[] result = new byte[newLength];

        System.arraycopy(current, 0, result, 0, startByte);
        System.arraycopy(current, endByte, result, startByte, current.length - endByte);

        set(result);
        return true;
    }

    public boolean deleteCodePointAt(int index) {
        return delete(index, index + 1);
    }

    public boolean replace(int start, int end, String str) {
        if (!delete(start, end)) {
            return false;
        }
        return insert(start, str);
    }

    public NoteIntegerArray reverse() {
        int[] codePoints = getAsCodePoints();
        if (codePoints.length <= 1) {
            return this;
        }

        for (int i = 0; i < codePoints.length / 2; i++) {
            int temp = codePoints[i];
            codePoints[i] = codePoints[codePoints.length - 1 - i];
            codePoints[codePoints.length - 1 - i] = temp;
        }

        set(ByteDecoding.codePointsToBytes(codePoints));
        return this;
    }

    // ========== NEW: Command Line Editing Methods ==========

    /**
     * Delete word backwards from position (Ctrl+W behavior)
     * Returns the new cursor position
     */
    public int deleteWordBackward(int cursorPos) {
        if (cursorPos <= 0) {
            return cursorPos;
        }

        int pos = cursorPos - 1;
        
        // Skip whitespace
        while (pos >= 0 && Character.isWhitespace(codePointAt(pos))) {
            pos--;
        }
        
        // Delete word
        while (pos >= 0 && !Character.isWhitespace(codePointAt(pos))) {
            pos--;
        }
        
        delete(pos + 1, cursorPos);
        return pos + 1;
    }

    /**
     * Delete word forward from position (Alt+D / Ctrl+Delete behavior)
     * Returns the new cursor position
     */
    public int deleteWordForward(int cursorPos) {
        if (cursorPos >= length()) {
            return cursorPos;
        }

        int pos = cursorPos;
        
        // Skip whitespace
        while (pos < length() && Character.isWhitespace(codePointAt(pos))) {
            pos++;
        }
        
        // Delete word
        while (pos < length() && !Character.isWhitespace(codePointAt(pos))) {
            pos++;
        }
        
        delete(cursorPos, pos);
        return cursorPos;
    }

    /**
     * Find start of word at or before cursor position
     * Used for word navigation (Ctrl+Left)
     */
    public int findWordStart(int cursorPos) {
        if (cursorPos <= 0) {
            return 0;
        }

        int pos = cursorPos - 1;
        
        // Skip whitespace
        while (pos > 0 && Character.isWhitespace(codePointAt(pos))) {
            pos--;
        }
        
        // Find word start
        while (pos > 0 && !Character.isWhitespace(codePointAt(pos - 1))) {
            pos--;
        }
        
        return pos;
    }

    /**
     * Find end of word at or after cursor position
     * Used for word navigation (Ctrl+Right)
     */
    public int findWordEnd(int cursorPos) {
        if (cursorPos >= length()) {
            return length();
        }

        int pos = cursorPos;
        
        // Skip whitespace
        while (pos < length() && Character.isWhitespace(codePointAt(pos))) {
            pos++;
        }
        
        // Find word end
        while (pos < length() && !Character.isWhitespace(codePointAt(pos))) {
            pos++;
        }
        
        return pos;
    }

    /**
     * Delete from cursor to end of line (Ctrl+K behavior)
     */
    public void deleteToEnd(int cursorPos) {
        if (cursorPos < length()) {
            delete(cursorPos, length());
        }
    }

    /**
     * Delete from cursor to start of line (Ctrl+U behavior in some shells)
     */
    public void deleteToStart(int cursorPos) {
        if (cursorPos > 0) {
            delete(0, cursorPos);
        }
    }

    /**
     * Split into tokens (for command parsing)
     * Respects quotes and escapes
     */
    public String[] tokenize() {
        return tokenize(false);
    }

    /**
     * Split into tokens with option to preserve quotes
     */
    public String[] tokenize(boolean preserveQuotes) {
        String str = toString();
        List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escape = false;
        char quoteChar = 0;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                if (!preserveQuotes || inQuotes) {
                    escape = true;
                } else {
                    current.append(c);
                }
                continue;
            }

            if ((c == '"' || c == '\'') && !inQuotes) {
                inQuotes = true;
                quoteChar = c;
                if (preserveQuotes) {
                    current.append(c);
                }
                continue;
            }

            if (inQuotes && c == quoteChar) {
                inQuotes = false;
                if (preserveQuotes) {
                    current.append(c);
                }
                quoteChar = 0;
                continue;
            }

            if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens.toArray(new String[0]);
    }

    /**
     * Count words (for status display)
     */
    public int countWords() {
        if (isEmpty()) {
            return 0;
        }

        int count = 0;
        boolean inWord = false;

        for (int i = 0; i < length(); i++) {
            int cp = codePointAt(i);
            boolean isSpace = Character.isWhitespace(cp);

            if (!isSpace && !inWord) {
                count++;
                inWord = true;
            } else if (isSpace) {
                inWord = false;
            }
        }

        return count;
    }

    /**
     * Trim whitespace from both ends
     */
    public NoteIntegerArray trim() {
        int len = length();
        if (len == 0) {
            return this;
        }

        int start = 0;
        while (start < len && Character.isWhitespace(codePointAt(start))) {
            start++;
        }

        int end = len;
        while (end > start && Character.isWhitespace(codePointAt(end - 1))) {
            end--;
        }

        if (start == 0 && end == len) {
            return this;
        }

        set(substring(start, end).get());
        return this;
    }

    /**
     * Check if string starts with prefix
     */
    public boolean startsWith(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }

        int[] prefixCps = prefix.codePoints().toArray();
        if (prefixCps.length > length()) {
            return false;
        }

        int[] thisCps = getAsCodePoints();
        for (int i = 0; i < prefixCps.length; i++) {
            if (thisCps[i] != prefixCps[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if string ends with suffix
     */
    public boolean endsWith(String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return true;
        }

        int[] suffixCps = suffix.codePoints().toArray();
        int len = length();
        if (suffixCps.length > len) {
            return false;
        }

        int[] thisCps = getAsCodePoints();
        int offset = len - suffixCps.length;
        
        for (int i = 0; i < suffixCps.length; i++) {
            if (thisCps[offset + i] != suffixCps[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Replace all occurrences of target with replacement
     */
    public NoteIntegerArray replaceAll(String target, String replacement) {
        if (target == null || target.isEmpty()) {
            return this;
        }

        String str = toString();
        String result = str.replace(target, replacement);
        
        if (!result.equals(str)) {
            set(ByteDecoding.stringToCodePointBytes(result));
        }

        return this;
    }

    /**
     * Get character width for terminal rendering
     * (useful for aligning cursor with rendered text)
     */
    public int getDisplayWidth() {
        int width = 0;
        for (int i = 0; i < length(); i++) {
            int cp = codePointAt(i);
            // East Asian Width: Fullwidth and Wide characters take 2 cells
            if (cp >= 0x1100 && 
                (cp <= 0x115F || cp == 0x2329 || cp == 0x232A ||
                 (cp >= 0x2E80 && cp <= 0xA4CF && cp != 0x303F) ||
                 (cp >= 0xAC00 && cp <= 0xD7A3) ||
                 (cp >= 0xF900 && cp <= 0xFAFF) ||
                 (cp >= 0xFE10 && cp <= 0xFE19) ||
                 (cp >= 0xFE30 && cp <= 0xFE6F) ||
                 (cp >= 0xFF00 && cp <= 0xFF60) ||
                 (cp >= 0xFFE0 && cp <= 0xFFE6) ||
                 (cp >= 0x20000 && cp <= 0x2FFFD) ||
                 (cp >= 0x30000 && cp <= 0x3FFFD))) {
                width += 2;
            } else if (cp >= 32) { // Printable
                width += 1;
            }
            // Control characters don't add width
        }
        return width;
    }

    /**
     * Get display width up to a specific code point index
     * (useful for cursor positioning)
     */
    public int getDisplayWidth(int upToIndex) {
        if (upToIndex <= 0) {
            return 0;
        }
        
        int maxIndex = Math.min(upToIndex, length());
        return substring(0, maxIndex).getDisplayWidth();
    }

    // ========== Access Methods ==========

    public int codePointAt(int index) {
        if (index < 0 || index >= length()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length());
        }

        byte[] bytes = get();
        int offset = index * ByteDecoding.CODE_POINT_BYTE_SIZE;
        return ByteDecoding.bytesToIntBigEndian(bytes, offset);
    }

    public int get(int index){
        return codePointAt(index);
    }

    public int length() {
        byte[] bytes = get();
        return bytes == null ? 0 : bytes.length / ByteDecoding.CODE_POINT_BYTE_SIZE;
    }

    public NoteIntegerArray substring(int start, int end) {
        int len = length();
        if (start < 0 || start > end || end > len) {
            throw new IndexOutOfBoundsException("start: " + start + ", end: " + end + 
                                               ", length: " + len);
        }

        byte[] bytes = get();
        int startByte = start * ByteDecoding.CODE_POINT_BYTE_SIZE;
        int lengthBytes = (end - start) * ByteDecoding.CODE_POINT_BYTE_SIZE;

        byte[] result = new byte[lengthBytes];
        System.arraycopy(bytes, startByte, result, 0, lengthBytes);

        return new NoteIntegerArray(result);
    }

    public NoteIntegerArray substring(int start) {
        return substring(start, length());
    }

    public boolean contains(String str) {
        return indexOf(str) != -1;
    }

    public int indexOf(String str) {
        if (str == null || str.isEmpty()) {
            return 0;
        }

        int[] haystack = getAsCodePoints();
        int[] needle = str.codePoints().toArray();

        if (needle.length > haystack.length) {
            return -1;
        }

        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }

        return -1;
    }

    public boolean isEmpty() {
        return length() == 0;
    }

    public void clear() {
        set(new byte[0]);
    }

    @Override
    public NoteIntegerArray copy(){
        byte[] bytes = get();
        return new NoteIntegerArray(Arrays.copyOf(bytes, bytes.length));
    }

    public NoteIntegerArray freeze(){
        return copy();
    }
   
    public int compareTo(NoteIntegerArray array){
        return Arrays.compare(get(), array.get());
    }

    // ========== JSON Methods ==========

    @Override
    public JsonElement getAsJsonElement() {
        return new JsonPrimitive(toString());
    }

    @Override
    public JsonArray getAsJsonArray() {
        JsonArray jsonArray = new JsonArray();
        int[] codePoints = getAsCodePoints();
        for (int cp : codePoints) {
            jsonArray.add(cp);
        }
        return jsonArray;
    }

    // ========== Factory Methods ==========

    public static NoteIntegerArray fromString(String str) {
        return new NoteIntegerArray(str);
    }

    public static NoteIntegerArray fromCodePoints(int[] codePoints) {
        return new NoteIntegerArray(codePoints);
    }
}