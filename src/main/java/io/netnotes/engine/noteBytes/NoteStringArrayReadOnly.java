package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteStringArrayReadOnly extends NoteBytesArrayReadOnly {

    private String m_delimiter = NoteStringArray.DELIMITER;

    public NoteStringArrayReadOnly(NoteBytes... noteBytes){
        super(noteBytes);
    }

    public NoteStringArrayReadOnly(byte[] bytes){
        super(Arrays.copyOf(bytes, bytes.length));
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

    public static NoteStringArrayReadOnly fromUrlString(String path){
        return new NoteStringArrayReadOnly(urlStringToArray(path));
    }

    public static String noteBytesArrayToString(NoteBytes[] array, String delim) {
        String[] str = new String[array.length];
        for (int i = 0; i < array.length; i++) {
            str[i] = ByteDecoding.UrlEncode(array[i].getAsString());
        }
        return String.join(delim, str);
    }

    

    public static NoteBytes[] urlStringToArray(String path) {
        return urlStringToArray(path, NoteBytesMetaData.STRING_TYPE, NoteStringArray.DELIMITER);
    }

    public static NoteBytes[] urlStringToArray(String path, String delim) {
        return urlStringToArray(path, NoteBytesMetaData.STRING_TYPE, delim);
    }

    public static NoteBytes[] urlStringToArray(String path, byte type, String delim) {
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



    public String getAsString(){
        NoteBytes[] array = getAsArray();
        return noteBytesArrayToString(array, m_delimiter);
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
        return super.indexOf(new NoteBytes(item));
    }

   public String[] getAsStringArray(){
        int size = size();
        String[] arr = new String[size];
        byte[] bytes = super.internalGet();
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

        byte[] bytes = super.internalGet();
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
