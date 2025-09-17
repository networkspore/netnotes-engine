package io.netnotes.engine.noteBytes;


public class NoteBytesArrayReadOnly extends NoteBytesArray{

 
    public NoteBytesArrayReadOnly(byte[] bytes){
        super(bytes, ByteDecoding.NOTE_BYTES_ARRAY);
    }

    public NoteBytesArrayReadOnly(NoteBytes[] noteBytes){
        this(getBytesFromArray(noteBytes));
    }

    public NoteBytesArrayReadOnly(byte[] bytes, ByteDecoding byteDecoding){
        super(bytes,byteDecoding);
    }
    
    @Override
    public void add(NoteBytes noteBytes){
    }

    @Override
    public boolean add(int index, NoteBytes noteBytes) {
        return false;
    }

    @Override
    public NoteBytes remove(NoteBytes noteBytes) {
        return null;
    }

    @Override
    public NoteBytes remove(int noteBytesIndex) {
        return null;
    }


    @Override
    public void set(byte[] disabled){

    }

    @Override
    public void set(byte[] disabled, ByteDecoding disabledByteDecoding){

    }

    @Override
    public void setByteDecoding(ByteDecoding disabled){

    }

    @Override
    public void clear(){

    }

    @Override
    public void destroy(){
        ruin();
    }

    @Override
    public void ruin(){
        super.ruin();
    }
}
