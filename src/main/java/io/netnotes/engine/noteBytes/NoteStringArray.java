package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteStringArray extends NoteBytesArray {

    public final static String DELIMITER = "/";

    private String m_delimiter = new String(DELIMITER);

    public NoteStringArray(){
        super();
    }

    public NoteStringArray(String... str){
        super(stringArrayToNoteBytes(str));
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

    @Override
    public String getAsString(){
        NoteBytes[] array = getAsArray();
        return noteBytesArrayToString(array, m_delimiter);
    }

    public String getAsString(String delimiter){
        NoteBytes[] array = getAsArray();
        return noteBytesArrayToString(array, delimiter);
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

    public int indexOf(String item){
        return indexOf(new NoteBytes(item));
    }

    public String[] getAsStringArray(){
        int size = size();
        String[] arr = new String[size];
        byte[] bytes = get();
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

        byte[] bytes = get();
        int length = bytes.length;
        int offset = 0;
        while(offset < length){
            NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
            noteBytesBuilder.accept(noteBytes.getAsString());
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return noteBytesBuilder.build();
    }

}
