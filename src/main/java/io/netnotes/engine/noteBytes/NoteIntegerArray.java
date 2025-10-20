package io.netnotes.engine.noteBytes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

/**
 * NoteCodePoints stores Unicode code points as raw 4-byte integers without metadata overhead.
 * Functions similar to StringBuilder for efficient string manipulation at the code point level.
 */
public class NoteIntegerArray extends NoteBytes {



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



    /**
     * Converts to String
     */
    @Override
    public String toString() {
        int[] codePoints = getAsCodePoints();
        if (codePoints.length == 0) {
            return "";
        }
        return new String(codePoints, 0, codePoints.length);
    }

    // ========== StringBuilder-like Methods ==========

    /**
     * Appends a string to the end
     */
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

    /**
     * Appends another NoteCodePoints
     */
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

    /**
     * Appends a single code point
     */
    public NoteIntegerArray appendCodePoint(int codePoint) {
        byte[] current = get();
        byte[] result = new byte[current.length + ByteDecoding.CODE_POINT_BYTE_SIZE];

        System.arraycopy(current, 0, result, 0, current.length);
        byte[] cpBytes = ByteDecoding.intToBytesBigEndian(codePoint);
        System.arraycopy(cpBytes, 0, result, current.length, ByteDecoding.CODE_POINT_BYTE_SIZE);

        set(result);
        return this;
    }

    /**
     * Inserts a string at the specified index
     */
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

    /**
     * Inserts a code point at the specified index
     */
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

    /**
     * Deletes code points from start (inclusive) to end (exclusive)
     */
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

    /**
     * Deletes a single code point at the specified index
     */
    public boolean deleteCodePointAt(int index) {
        return delete(index, index + 1);
    }

    /**
     * Replaces code points from start to end with the given string
     */
    public boolean replace(int start, int end, String str) {
        if (!delete(start, end)) {
            return false;
        }
        return insert(start, str);
    }

    /**
     * Reverses the code points
     */
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

    // ========== Access Methods ==========

    /**
     * Gets the code point at the specified index
     */
    public int codePointAt(int index) {
        if (index < 0 || index >= length()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length());
        }

        byte[] bytes = get();
        int offset = index * ByteDecoding.CODE_POINT_BYTE_SIZE;
        return ByteDecoding.bytesToIntBigEndian(bytes, offset);
    }

    /**
     * Returns the number of code points stored
     */
    public int length() {
        byte[] bytes = get();
        return bytes == null ? 0 : bytes.length / ByteDecoding.CODE_POINT_BYTE_SIZE;
    }

    /**
     * Returns a substring as a new NoteCodePoints
     */
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

    /**
     * Returns a substring starting from index to end
     */
    public NoteIntegerArray substring(int start) {
        return substring(start, length());
    }

    /**
     * Checks if this contains the given string
     */
    public boolean contains(String str) {
        return indexOf(str) != -1;
    }

    /**
     * Returns the index of the first occurrence of the string, or -1 if not found
     */
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

    /**
     * Checks if empty
     */
    public boolean isEmpty() {
        return length() == 0;
    }

    /**
     * Clears all content
     */
    public void clear() {
        set(new byte[0]);
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

    /**
     * Creates a NoteCodePoints from a string
     */
    public static NoteIntegerArray fromString(String str) {
        return new NoteIntegerArray(str);
    }

    /**
     * Creates a NoteCodePoints from code points array
     */
    public static NoteIntegerArray fromCodePoints(int[] codePoints) {
        return new NoteIntegerArray(codePoints);
    }
}