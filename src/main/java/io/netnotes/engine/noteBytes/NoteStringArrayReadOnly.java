package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class NoteStringArrayReadOnly extends NoteBytesArrayReadOnly {

    public final static String DEFAULT_DELIMITER = "/";

    public NoteStringArrayReadOnly(NoteBytes noteBytes){
        super(noteBytes.get(), noteBytes.getByteDecoding());
    }

    public NoteStringArrayReadOnly(String... str){
        super(stringArrayToNoteBytes(str));
    }

    public static NoteBytes[] stringArrayToNoteBytes(String[] array){
        NoteBytes[] noteBytes = new NoteBytes[array.length];
        for(int i = 0; i < array.length ; i++){
            noteBytes[i] = new NoteBytes(array[i]);
        }
        return noteBytes;
    }

    public static String noteBytesArrayToString(NoteBytes[] array, String delim){
        String[] str = new String[array.length];
        for(int i = 0; i < array.length ; i++){
            str[i] = array[i].toString();
        }

        return String.join(delim, str);
    }

    @Override
    public NoteString[] getAsArray(){
        int size = size();
        NoteString[] arr = new NoteString[size];
        byte[] bytes = get();
        int length = bytes.length;
        int offset = 0;
        int i = 0;
        while(offset < length){
            NoteString noteBytes = readNoteString(bytes, offset);
            arr[i] = noteBytes;
            i++;
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return arr;
    }

    public static NoteString readNoteString(byte[] src, int srcOffset){
        final int metaDataSize = NoteBytesMetaData.STANDARD_META_DATA_SIZE;
        final byte stringType = NoteBytesMetaData.STRING_TYPE;

        if (src.length < srcOffset + metaDataSize) {
            throw new IndexOutOfBoundsException("insufficient source length for metadata");
        }

        // 1. Read type
        byte type = src[srcOffset];

        if(type != stringType){
            throw new IllegalArgumentException("String type required");
        }

        // 2. Read length (4 bytes big-endian)
        int length = ((src[srcOffset + 1] & 0xFF) << 24) |
                    ((src[srcOffset + 2] & 0xFF) << 16) |
                    ((src[srcOffset + 3] & 0xFF) << 8)  |
                    (src[srcOffset + 4] & 0xFF);

        if (src.length < srcOffset + metaDataSize + length) {
            throw new IndexOutOfBoundsException("insufficient source length for data");
        }

        // 3. Copy payload
        byte[] data = new byte[length];
        System.arraycopy(src, srcOffset + metaDataSize, data, 0, length);

        return new NoteString(data);
    }


    public String getAsString(){
        NoteBytes[] array = getAsArray();
        return noteBytesArrayToString(array, DEFAULT_DELIMITER);
    }

    public String getAsString(String delimiter){
        NoteBytes[] array = getAsArray();
        return noteBytesArrayToString(array, delimiter);
    }

    @Override
    public String toString(){
        return getAsString();
    }

    public boolean contains(String string){
        return indexOf(string) != -1;
    }

    public int indexOf(String item){
        return indexOf(new NoteBytes(item));
    }

    public String[] getAsStringArray(){
        return getAsStringStream().toArray(String[]::new);
    }

    public List<String> getAsStringList(){
         return getAsStringStream().toList();
    }

    public Stream<String> getAsStringStream(){
        return getAsStringStream(get(), getByteDecoding());
    }

    public static Stream<String> getAsStringStream(byte[] bytes, ByteDecoding byteDecoding){
        Stream.Builder<String> noteBytesBuilder = Stream.builder();
        int length = bytes.length;
        int offset = 0;
        while(offset < length){
            byte type = bytes[offset++];
            int size = byteDecoding.isLittleEndian() ? ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += 4;
            NoteBytes noteBytes = new NoteBytes(Arrays.copyOfRange(bytes, offset, offset + size), ByteDecoding.of(type));
            noteBytesBuilder.accept(noteBytes.getAsString());
            offset += size;
        }
        return noteBytesBuilder.build();
    }

}
