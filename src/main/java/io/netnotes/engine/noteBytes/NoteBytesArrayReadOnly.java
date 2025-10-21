package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteBytesArrayReadOnly extends NoteBytesArray{

    public NoteBytesArrayReadOnly(byte[] bytes){
        super(Arrays.copyOf(bytes, bytes.length));
    }

    public NoteBytesArrayReadOnly(NoteBytes[] noteBytes){
        this(getBytesFromArray(noteBytes));
    }

    public Stream<NoteBytesReadOnly> getAsReadOnlyStream(){
        
        byte[] bytes = super.internalGet();
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


    @Override
    public NoteBytesReadOnly[] getAsArray(){
    
        int size = size();
        NoteBytesReadOnly[] arr = new NoteBytesReadOnly[size];
        byte[] bytes = internalGet();
        int length = bytes.length;
        int offset = 0;
        int i = 0;
        while(offset < length){
            NoteBytesReadOnly noteBytes = NoteBytesReadOnly.readNote(bytes, offset);
            arr[i] = noteBytes;
            i++;
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return arr;
    }

    @Override
    public byte[] get(){
        byte[] data = super.internalGet();
        return Arrays.copyOf(data, data.length);
    }

    public List<NoteBytesReadOnly> getAsReadOnlyList(){
        return getAsReadOnlyStream().toList();
    }



    @Override
    public NoteBytes set(int index, NoteBytes noteBytes){
        return null;
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
    public void set(byte[] disabled, byte type){

    }

    @Override
    public void setType(byte type){

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
