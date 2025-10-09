package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;



import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.ByteHashing;

public class NoteBytesArrayReadOnly extends NoteBytesArray{

 
    public NoteBytesArrayReadOnly(byte[] bytes){
        super(bytes);
    }

    public NoteBytesArrayReadOnly(NoteBytes[] noteBytes){
        this(getBytesFromArray(noteBytes));
    }

    public Stream<NoteBytesReadOnly> getAsReadOnlyStream(){
        
        
        byte[] bytes = super.get();
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
    public boolean equals(Object obj){
        if(isRuined()){ 
            return false;
        }
        if(obj == null){
            return false;
        }
        if(obj == this){
            return true;
        }
        if(obj != null && obj instanceof NoteBytes){
            NoteBytes noteBytesObj = (NoteBytes) obj;
            if(noteBytesObj.isRuined()){
                return false;
            }
            byte objType = noteBytesObj.getType();
            byte thisType = getType();
            if(objType != thisType){
                return false;
            }
            byte[] objValue = noteBytesObj.get();
            if(byteLength() != objValue.length){
                return false;
            }
            return compareBytes(objValue);
        }
        if(obj instanceof byte[]){
            return compareBytes((byte[]) obj);
        }
        return false;
    }

    @Override
    public boolean compareBytes(byte[] bytes){
        if(isRuined()){
            return false;
        }
        byte[] value = super.get();
        if(value.length != bytes.length){
            return false;
        }
        if(value.length == 0 && bytes.length == 0){
            return true;
        }
        return Arrays.equals(value, bytes);
    }

    @Override
    public int hashCode(){
        byte[] bytes = super.get();
        return bytes.length == 0 ? 0 : 
            ByteDecoding.bytesToIntBigEndian(ByteHashing.digestBytesToBytes(bytes,4));
    }


    @Override
    public NoteBytesReadOnly[] getAsArray(){
    
        int size = size();
        NoteBytesReadOnly[] arr = new NoteBytesReadOnly[size];
        byte[] bytes = super.get();
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
        byte[] data = super.get();
        return Arrays.copyOf(data, data.length);
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
