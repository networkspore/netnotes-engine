package io.netnotes.engine.noteBytes;

import java.util.List;
import java.util.stream.Stream;

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

    public Stream<NoteBytesReadOnly> getAsReadOnlyStream(){
        
        
        byte[] bytes = get();
        Stream.Builder<NoteBytesReadOnly> noteBytesBuilder = Stream.builder();
        int length = bytes.length;
        int offset = 0;
        
        while(offset < length){
            NoteBytesReadOnly noteBytes = NoteBytesReadOnly.readNote(bytes, offset);
            noteBytesBuilder.accept(noteBytes);
            offset += (5 + noteBytes.byteLength());
        }
        return noteBytesBuilder.build();
    
    }

    public NoteBytesReadOnly[] getAsReadOnlyArray(){
        return getAsReadOnlyStream().toArray(NoteBytesReadOnly[]::new);
    }

    

    public List<NoteBytesReadOnly> getAsReadOnlyList(){
        return getAsReadOnlyStream().toList();
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
