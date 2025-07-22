package io.netnotes.engine.noteBytes;

public class NoteListString extends NoteBytesArray {

    public final static String DELIMITER = ",";

    private String m_delimiter = new String(DELIMITER);


    public NoteListString(String... str){
        super(stringArrayToNoteBytes(str));
    }

    public NoteListString(String list, char[] delimiter){
        super(stringArrayToNoteBytes(list.split(new String(delimiter))));
        m_delimiter = new String(delimiter);
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

}
