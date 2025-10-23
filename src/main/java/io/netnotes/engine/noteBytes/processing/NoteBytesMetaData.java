package io.netnotes.engine.noteBytes.processing;

public class NoteBytesMetaData {
    public final static int STANDARD_META_DATA_SIZE = 5;

    public final static byte RAW_BYTES_TYPE = NoteBytesMetaData.NO_FLAG;
    public final static byte LONG_TYPE = (byte) 2;
    public final static byte DOUBLE_TYPE = (byte) 3;
    public final static byte INTEGER_TYPE = (byte) 4;
    public final static byte STRING_UTF16_TYPE = (byte) 5;
    public final static byte STRING_TYPE = (byte) 6;
    public final static byte UTF_8_TYPE = (byte) 6;
    public final static byte BOOLEAN_TYPE = (byte) 7;
    public final static byte SHORT_TYPE = (byte) 8;
    public final static byte FLOAT_TYPE = (byte) 9;
    public final static byte NOTE_BYTES_ARRAY_TYPE = (byte) 10;
    public final static byte NOTE_BYTES_OBJECT_TYPE = (byte) 11;
    public final static byte NOTE_INTEGER_ARRAY_TYPE = (byte) 12;
    public final static byte BIG_INTEGER_TYPE = (byte) 13;
    public final static byte BIG_DECIMAL_TYPE = (byte) 14;
    public final static byte STRING_ISO_8859_1_TYPE = (byte) 15;
    public final static byte STRING_US_ASCII_TYPE = (byte) 16;  
    public final static byte LONG_LE_TYPE = (byte) 17;
    public final static byte DOUBLE_LE_TYPE = (byte) 18;
    public final static byte INTEGER_LE_TYPE = (byte) 19;
    public final static byte STRING_UTF16_LE_TYPE = (byte) 20;
    public final static byte SHORT_LE_TYPE = (byte) 21;
    public final static byte FLOAT_LE_TYPE = (byte) 22;
    public final static byte IMAGE_TYPE = (byte) 23;
    public final static byte VIDEO_TYPE = (byte) 24;
    public final static byte SERIALIZABLE_OBJECT_TYPE = (byte) 25;
    public final static byte NOTE_BYTES_TREE_TYPE = (byte) 26;
    

    private byte m_type;
    private int m_len;

    public static final byte NO_FLAG = (byte)0;

    public NoteBytesMetaData(byte type, int len) {
        this.m_type = type;
        this.m_len = len;
    }

    public NoteBytesMetaData(byte type, byte[] byteLen) {
        this.m_type = type;
        setLength(byteLen);
    }

    public byte getType() {
        return m_type;
    }

    public int getLength() {
        return m_len;
    }

    public void setLength(int len) {
        this.m_len = len;
    }

    public void setLength(byte[] bytes) {
        m_len = ByteDecoding.bytesToIntBigEndian(bytes);
    }

    public void setType(byte type) {
        this.m_type = type;
    }

    public static int write(byte type, int len, byte[] dst, int offset){
        dst[offset] = type;
        dst[offset + 1] = (byte) (len >>> 24);
        dst[offset + 2] = (byte) (len >>> 16);
        dst[offset + 3] = (byte) (len >>> 8);
        dst[offset + 4] = (byte) (len);
        return offset + STANDARD_META_DATA_SIZE;
    }
}