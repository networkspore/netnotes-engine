package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;

public class NoteStringArray extends NoteBytesArray {

    public final static String DELIMITER = ",";

    private String m_delimiter = new String(DELIMITER);

    public NoteStringArray(){
        super();
    }

    public NoteStringArray(String... str){
        super(stringArrayToNoteBytes(str));
    }

    public NoteStringArray(String list, String delim){
        super(stringArrayToNoteBytes(list.split(delim)));
        m_delimiter = delim;
    }

    public NoteStringArray(NoteBytes... noteBytes){
        super(noteBytes);
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

    public void setDelimiter(String delim){
        m_delimiter = delim;
    }

    public String getDelimiter(){
        return m_delimiter;
    }

    public String getAsString(){
        NoteBytes[] array = getAsArray();
        return noteBytesArrayToString(array, m_delimiter);
    }

    public String getAsString(String delimiter){
        NoteBytes[] array = getAsArray();
        return noteBytesArrayToString(array, delimiter);
    }

    public NoteBytes remove(String item){
        return remove(new NoteBytes(item, getByteDecoding()));
    }

    public void add(String str){
        add(new NoteBytes(str));
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
