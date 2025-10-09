package io.netnotes.engine.noteBytes.processing;

/*
 * Secure version of ByteDecoding with strict validation.
 * Throws exceptions on invalid input - prioritizes correctness over speed.
 * Use ByteDecoding for performance-critical scenarios with trusted data.
 */
public class ByteDecodingSecure extends ByteDecoding {


    // ===== VALIDATED INTEGER CONVERSIONS =====

    public static byte[] intToBytesBigEndianSecure(int value) {
        // No validation needed for output
        return ByteDecoding.intToBytesBigEndian(value);
    }

    public static byte[] intToBytesLittleEndianSecure(int value) {
        // No validation needed for output
        return ByteDecoding.intToBytesLittleEndian(value);
    }

    public static int bytesToIntBigEndianSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (bytes.length != Integer.BYTES) {
            throw new IllegalArgumentException("Expected exactly " + Integer.BYTES + " bytes for int conversion, got " + bytes.length);
        }
        return ByteDecoding.bytesToIntBigEndian(bytes);
    }

    public static int bytesToIntBigEndianSecure(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset + Integer.BYTES > bytes.length) {
            throw new IllegalArgumentException("Not enough bytes: need " + Integer.BYTES + " bytes at offset " + offset + ", array length is " + bytes.length);
        }
        return ByteDecoding.bytesToIntBigEndian(bytes, offset);
    }

    public static int bytesToIntLittleEndianSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (bytes.length != Integer.BYTES) {
            throw new IllegalArgumentException("Expected exactly " + Integer.BYTES + " bytes for int conversion, got " + bytes.length);
        }
        return ByteDecoding.bytesToIntLittleEndian(bytes);
    }

    public static int bytesToIntLittleEndianSecure(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset + Integer.BYTES > bytes.length) {
            throw new IllegalArgumentException("Not enough bytes: need " + Integer.BYTES + " bytes at offset " + offset + ", array length is " + bytes.length);
        }
        return ByteDecoding.bytesToIntLittleEndian(bytes, offset);
    }

    // ===== VALIDATED LONG CONVERSIONS =====

    public static byte[] longToBytesBigEndianSecure(long value) {
        return ByteDecoding.longToBytesBigEndian(value);
    }

    public static byte[] longToBytesLittleEndianSecure(long value) {
        return ByteDecoding.longToBytesLittleEndian(value);
    }

    public static long bytesToLongBigEndianSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (bytes.length != Long.BYTES) {
            throw new IllegalArgumentException("Expected exactly " + Long.BYTES + " bytes for long conversion, got " + bytes.length);
        }
        return ByteDecoding.bytesToLongBigEndian(bytes);
    }

    public static long bytesToLongBigEndianSecure(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset + Long.BYTES > bytes.length) {
            throw new IllegalArgumentException("Not enough bytes: need " + Long.BYTES + " bytes at offset " + offset + ", array length is " + bytes.length);
        }
        return ByteDecoding.bytesToLongBigEndian(bytes, offset);
    }

    public static long bytesToLongLittleEndianSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (bytes.length != Long.BYTES) {
            throw new IllegalArgumentException("Expected exactly " + Long.BYTES + " bytes for long conversion, got " + bytes.length);
        }
        return ByteDecoding.bytesToLongLittleEndian(bytes);
    }

    public static long bytesToLongLittleEndianSecure(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset + Long.BYTES > bytes.length) {
            throw new IllegalArgumentException("Not enough bytes: need " + Long.BYTES + " bytes at offset " + offset + ", array length is " + bytes.length);
        }
        return ByteDecoding.bytesToLongLittleEndian(bytes, offset);
    }

    // ===== VALIDATED SHORT CONVERSIONS =====

    public static byte[] shortToBytesBigEndianSecure(short value) {
        return ByteDecoding.shortToBytesBigEndian(value);
    }

    public static byte[] shortToBytesLittleEndianSecure(short value) {
        return ByteDecoding.shortToBytesLittleEndian(value);
    }

    public static short bytesToShortBigEndianSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (bytes.length != Short.BYTES) {
            throw new IllegalArgumentException("Expected exactly " + Short.BYTES + " bytes for short conversion, got " + bytes.length);
        }
        return ByteDecoding.bytesToShortBigEndian(bytes);
    }

    public static short bytesToShortBigEndianSecure(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset + Short.BYTES > bytes.length) {
            throw new IllegalArgumentException("Not enough bytes: need " + Short.BYTES + " bytes at offset " + offset + ", array length is " + bytes.length);
        }
        return ByteDecoding.bytesToShortBigEndian(bytes, offset);
    }

    public static short bytesToShortLittleEndianSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (bytes.length != Short.BYTES) {
            throw new IllegalArgumentException("Expected exactly " + Short.BYTES + " bytes for short conversion, got " + bytes.length);
        }
        return ByteDecoding.bytesToShortLittleEndian(bytes);
    }

    public static short bytesToShortLittleEndianSecure(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset + Short.BYTES > bytes.length) {
            throw new IllegalArgumentException("Not enough bytes: need " + Short.BYTES + " bytes at offset " + offset + ", array length is " + bytes.length);
        }
        return ByteDecoding.bytesToShortLittleEndian(bytes, offset);
    }

    // ===== VALIDATED DOUBLE CONVERSIONS =====

    public static byte[] doubleToBytesBigEndianSecure(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Cannot convert NaN to bytes");
        }
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("Cannot convert infinite value to bytes");
        }
        return ByteDecoding.doubleToBytesBigEndian(value);
    }

    public static byte[] doubleToBytesLittleEndianSecure(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Cannot convert NaN to bytes");
        }
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("Cannot convert infinite value to bytes");
        }
        return ByteDecoding.doubleToBytesLittleEndian(value);
    }

    public static double bytesToDoubleBigEndianSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (bytes.length != Double.BYTES) {
            throw new IllegalArgumentException("Expected exactly " + Double.BYTES + " bytes for double conversion, got " + bytes.length);
        }
        double result = ByteDecoding.bytesToDoubleBigEndian(bytes);
        if (Double.isNaN(result)) {
            throw new IllegalStateException("Byte array contains invalid double value (NaN)");
        }
        if (Double.isInfinite(result)) {
            throw new IllegalStateException("Byte array contains invalid double value (Infinite)");
        }
        return result;
    }

    public static double bytesToDoubleBigEndianSecure(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset + Double.BYTES > bytes.length) {
            throw new IllegalArgumentException("Not enough bytes: need " + Double.BYTES + " bytes at offset " + offset + ", array length is " + bytes.length);
        }
        double result = ByteDecoding.bytesToDoubleBigEndian(bytes, offset);
        if (Double.isNaN(result)) {
            throw new IllegalStateException("Byte array contains invalid double value (NaN)");
        }
        if (Double.isInfinite(result)) {
            throw new IllegalStateException("Byte array contains invalid double value (Infinite)");
        }
        return result;
    }

    public static double bytesToDoubleLittleEndianSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (bytes.length != Double.BYTES) {
            throw new IllegalArgumentException("Expected exactly " + Double.BYTES + " bytes for double conversion, got " + bytes.length);
        }
        double result = ByteDecoding.bytesToDoubleLittleEndian(bytes);
        if (Double.isNaN(result)) {
            throw new IllegalStateException("Byte array contains invalid double value (NaN)");
        }
        if (Double.isInfinite(result)) {
            throw new IllegalStateException("Byte array contains invalid double value (Infinite)");
        }
        return result;
    }

    public static double bytesToDoubleLittleEndianSecure(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset + Double.BYTES > bytes.length) {
            throw new IllegalArgumentException("Not enough bytes: need " + Double.BYTES + " bytes at offset " + offset + ", array length is " + bytes.length);
        }
        double result = ByteDecoding.bytesToDoubleLittleEndian(bytes, offset);
        if (Double.isNaN(result)) {
            throw new IllegalStateException("Byte array contains invalid double value (NaN)");
        }
        if (Double.isInfinite(result)) {
            throw new IllegalStateException("Byte array contains invalid double value (Infinite)");
        }
        return result;
    }

    // ===== VALIDATED FLOAT CONVERSIONS =====

    public static byte[] floatToBytesBigEndianSecure(float value) {
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException("Cannot convert NaN to bytes");
        }
        if (Float.isInfinite(value)) {
            throw new IllegalArgumentException("Cannot convert infinite value to bytes");
        }
        return ByteDecoding.floatToBytesBigEndian(value);
    }

    public static byte[] floatToBytesLittleEndianSecure(float value) {
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException("Cannot convert NaN to bytes");
        }
        if (Float.isInfinite(value)) {
            throw new IllegalArgumentException("Cannot convert infinite value to bytes");
        }
        return ByteDecoding.floatToBytesLittleEndian(value);
    }

    public static float bytesToFloatBigEndianSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (bytes.length != Float.BYTES) {
            throw new IllegalArgumentException("Expected exactly " + Float.BYTES + " bytes for float conversion, got " + bytes.length);
        }
        float result = ByteDecoding.bytesToFloatBigEndian(bytes);
        if (Float.isNaN(result)) {
            throw new IllegalStateException("Byte array contains invalid float value (NaN)");
        }
        if (Float.isInfinite(result)) {
            throw new IllegalStateException("Byte array contains invalid float value (Infinite)");
        }
        return result;
    }

    public static float bytesToFloatBigEndianSecure(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset + Float.BYTES > bytes.length) {
            throw new IllegalArgumentException("Not enough bytes: need " + Float.BYTES + " bytes at offset " + offset + ", array length is " + bytes.length);
        }
        float result = ByteDecoding.bytesToFloatBigEndian(bytes, offset);
        if (Float.isNaN(result)) {
            throw new IllegalStateException("Byte array contains invalid float value (NaN)");
        }
        if (Float.isInfinite(result)) {
            throw new IllegalStateException("Byte array contains invalid float value (Infinite)");
        }
        return result;
    }

    public static float bytesToFloatLittleEndianSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (bytes.length != Float.BYTES) {
            throw new IllegalArgumentException("Expected exactly " + Float.BYTES + " bytes for float conversion, got " + bytes.length);
        }
        float result = ByteDecoding.bytesToFloatLittleEndian(bytes);
        if (Float.isNaN(result)) {
            throw new IllegalStateException("Byte array contains invalid float value (NaN)");
        }
        if (Float.isInfinite(result)) {
            throw new IllegalStateException("Byte array contains invalid float value (Infinite)");
        }
        return result;
    }

    public static float bytesToFloatLittleEndianSecure(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset + Float.BYTES > bytes.length) {
            throw new IllegalArgumentException("Not enough bytes: need " + Float.BYTES + " bytes at offset " + offset + ", array length is " + bytes.length);
        }
        float result = ByteDecoding.bytesToFloatLittleEndian(bytes, offset);
        if (Float.isNaN(result)) {
            throw new IllegalStateException("Byte array contains invalid float value (NaN)");
        }
        if (Float.isInfinite(result)) {
            throw new IllegalStateException("Byte array contains invalid float value (Infinite)");
        }
        return result;
    }

    // ===== VALIDATED BOOLEAN CONVERSIONS =====

    public static byte[] booleanToBytesSecure(boolean value) {
        return ByteDecoding.booleanToBytes(value);
    }

    public static boolean bytesToBooleanSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Byte array cannot be empty for boolean conversion");
        }
        if (bytes[0] != 0 && bytes[0] != 1) {
            throw new IllegalArgumentException("Invalid boolean byte value: " + bytes[0] + " (must be 0 or 1)");
        }
        return ByteDecoding.bytesToBoolean(bytes);
    }

    public static boolean bytesToBooleanSecure(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset >= bytes.length) {
            throw new IllegalArgumentException("Offset " + offset + " is beyond array length " + bytes.length);
        }
        if (bytes[offset] != 0 && bytes[offset] != 1) {
            throw new IllegalArgumentException("Invalid boolean byte value at offset " + offset + ": " + bytes[offset] + " (must be 0 or 1)");
        }
        return ByteDecoding.bytesToBoolean(bytes, offset);
    }


    // ===== VALIDATED ARRAY METHODS =====

    public static byte[] unboxBytesSecure(Byte[] byteObjects) {
        if (byteObjects == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        for (int i = 0; i < byteObjects.length; i++) {
            if (byteObjects[i] == null) {
                throw new IllegalArgumentException("Byte object at index " + i + " cannot be null");
            }
        }
        return ByteDecoding.unboxBytes(byteObjects);
    }

    public static Byte[] boxBytesSecure(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        return ByteDecoding.boxBytes(bytes);
    }

    // ===== VALIDATION UTILITY METHODS =====

    public static void validateByteArray(byte[] bytes, String parameterName) {
        if (bytes == null) {
            throw new IllegalArgumentException(parameterName + " cannot be null");
        }
    }

    public static void validateByteArray(byte[] bytes, int expectedLength, String parameterName) {
        validateByteArray(bytes, parameterName);
        if (bytes.length != expectedLength) {
            throw new IllegalArgumentException(parameterName + " must be exactly " + expectedLength + " bytes, got " + bytes.length);
        }
    }

    public static void validateOffset(byte[] bytes, int offset, int requiredBytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (offset + requiredBytes > bytes.length) {
            throw new IllegalArgumentException("Not enough bytes: need " + requiredBytes + " bytes at offset " + offset + ", array length is " + bytes.length);
        }
    }

    public static void validateFloatingPoint(double value, String parameterName) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException(parameterName + " cannot be NaN");
        }
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException(parameterName + " cannot be infinite");
        }
    }

    public static void validateFloatingPoint(float value, String parameterName) {
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException(parameterName + " cannot be NaN");
        }
        if (Float.isInfinite(value)) {
            throw new IllegalArgumentException(parameterName + " cannot be infinite");
        }
    }
}