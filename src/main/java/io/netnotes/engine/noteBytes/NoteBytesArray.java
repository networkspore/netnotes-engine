package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import io.netnotes.engine.ByteDecoding;
import io.netnotes.engine.Utils;

public class NoteBytesArray extends NoteBytes{

    public NoteBytesArray(){
        super(new byte[0]);
    }

    public NoteBytesArray(byte[] bytes){
        super(bytes);
    }

    public NoteBytesArray(NoteBytes[] noteBytes){
        super(getBytesFromArray(noteBytes));
    }

    public static byte[] getBytesFromArray(NoteBytes[] noteBytesArray){
        int length = noteBytesArray != null && noteBytesArray.length > 0 ? noteBytesArray.length : 0;
        if(length > 0){
            byte[] dstBytes = new byte[getByteLength(noteBytesArray)];
            int dstOffset = 0;
            for(int i = 0; i < noteBytesArray.length; i ++){
                NoteBytes noteBytes = noteBytesArray[i];
                byte[] intBytes = ByteDecoding.intToBytesBigEndian(noteBytes.byteLength());
                byte[] buffer = noteBytes.get();                
                dstOffset = Utils.arrayCopy(intBytes, 0, dstBytes, dstOffset,      4);
                dstOffset = Utils.arrayCopy(buffer,   0, dstBytes, dstOffset, buffer.length);
            }
        }
        return new byte[0];
    }

    public static int getByteLength(NoteBytes[] noteBytesArray){
        int size = 0;
        for(int i = 0; i < noteBytesArray.length; i ++){
            NoteBytes noteBytes = noteBytesArray[i];
            size += (4 + noteBytes.byteLength());
        }
        return size;
    }

    public static NoteBytesArray create(NoteBytes[] noteBytesArray){
        return new NoteBytesArray(getBytesFromArray(noteBytesArray));
    }

    public Stream<NoteBytes> getAsStream(){
       return getAsStream(get(), getByteDecoding());
    }

    public static Stream<NoteBytes> getAsStream(byte[] bytes, ByteDecoding byteDecoding){
        Stream.Builder<NoteBytes> noteBytesBuilder = Stream.builder();
        int length = bytes.length;
        int offset = 0;
        while(offset < length){
            NoteBytes noteBytes = NoteBytes.readNote(bytes, offset, byteDecoding);
            noteBytesBuilder.accept(noteBytes);
            offset += (4 + noteBytes.byteLength());
        }
        return noteBytesBuilder.build();
    }

    public NoteBytes[] getAsArray(){
        return getAsStream().toArray(NoteBytes[]::new);
    }

    public List<NoteBytes> getAsList(){
        return getAsStream().toList();
    }

    public NoteBytes getAt(int index){
        byte[] bytes = get();
        int length = bytes.length;
        int offset = 0;
        int counter = 0;
        boolean isLittleEndian = getByteDecoding().isLittleEndian();
        while(offset < length){
            int size = isLittleEndian ?  ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += 4;
            if(counter == index){
                byte[] dst = new byte[size];
                System.arraycopy(bytes, offset, dst, 0, size);
                return new NoteBytes(dst);
            }
            offset += size;
            counter++;
        }
        return null;
    }

    public int indexOf(NoteBytes noteBytes){
        byte[] bytes = get();
        int length = bytes.length;
        int offset = 0;
        int counter = 0;
        boolean isLittleEndian = getByteDecoding().isLittleEndian();
        while(offset < length){
            int size = isLittleEndian ?  ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += 4;
            byte[] buffer = new byte[size];
            System.arraycopy(bytes, offset, buffer, 0, size);
            if(Arrays.equals(noteBytes.get(), buffer)){
                return counter;
            }
            offset += size;
            counter++;
        }
        return -1;
    }

    public void add(String str){
        add(new NoteBytes(str));
    }

    public void add(NoteBytes noteBytes){
        byte[] bytes = get();
        int length = bytes.length;
        byte[] src = noteBytes.get();
        int srcLength = src.length;
        byte[] intBytes =  getByteDecoding().isLittleEndian() ?  ByteDecoding.intToBytesLittleEndian(srcLength) : ByteDecoding.intToBytesBigEndian(srcLength);
        byte[] dst = Arrays.copyOf(bytes, length + 4 + srcLength);
        int dstOffset = Utils.arrayCopy(intBytes, 0, dst, length, 4);
        System.arraycopy(src, 0, dst, dstOffset, srcLength);
        set(dst);
    }

    public static int copyNoteBytesToArray(byte[] dst, int offset, NoteBytes noteBytes, boolean isLittleEndian){
        byte[] src = noteBytes.get();
        int srcLength = src.length;
        byte[] noteBytesSize = isLittleEndian ? ByteDecoding.intToBytesLittleEndian(srcLength) : ByteDecoding.intToBytesBigEndian(srcLength);
        int dstOffset = offset;
        dstOffset = Utils.arrayCopy(noteBytesSize, 0, dst, dstOffset, 4);
        dstOffset = Utils.arrayCopy(src, 0, dst, dstOffset, srcLength);
        return dstOffset;
    }   

    public void add(int index, NoteBytes noteBytes){
        byte[] src = get();
        int length = src.length;
        byte[] dst = new byte[length + noteBytes.byteLength() + 4];
        int srcOffset = 0;
        int dstOffset = 0;
        int indexPos = 0;
        boolean isLittleEndian = getByteDecoding().isLittleEndian();
        while(srcOffset < length){
            dstOffset = indexPos == index ? copyNoteBytesToArray(dst, dstOffset, noteBytes, isLittleEndian) : dstOffset;
            int size  = isLittleEndian ?  ByteDecoding.bytesToIntLittleEndian(src, srcOffset) : ByteDecoding.bytesToIntBigEndian(src, srcOffset);
            int totalSize =  4 + size;
            dstOffset = Utils.arrayCopy(src, srcOffset, dst, dstOffset, totalSize);
            srcOffset += totalSize;
            indexPos++;
        }
        if(indexPos == index){
            copyNoteBytesToArray(dst, srcOffset, noteBytes, isLittleEndian);   
        }
        if(index <= indexPos){
            set(dst);
        }
    }

    public NoteBytes remove(int noteBytesIndex){
        byte[] bytes = get();
        int length = bytes.length;
        if(bytes != null && length > 0){
            NoteBytes noteBytes = getAt(noteBytesIndex);
            if(noteBytes != null){
                int removeLength = noteBytes.byteLength();
                byte[] dstBytes = new byte[length - 4 - removeLength];
                boolean isLittleEndian = getByteDecoding().isLittleEndian();
                int offset = 0;
                int index = 0;
                int dstOffset = 0;
                while(offset < length){
                    int size = isLittleEndian ? ByteDecoding.bytesToIntLittleEndian(bytes, offset) :  ByteDecoding.bytesToIntBigEndian(bytes, offset);
                    boolean isKey = index != noteBytesIndex;
                    dstOffset = !isKey ? Utils.arrayCopy(bytes, offset, dstBytes, dstOffset, size + 4) : dstOffset;
                    offset += (4 + size);
                    index ++;
                }
                set(dstBytes);
                return noteBytes;
            }
        }
        return null;
    }

    public int size(){
        byte[] bytes = get();
        int length = bytes.length;
        int offset = 0;
        int counter = 0;
        boolean isLittleEndian = getByteDecoding().isLittleEndian();
        while(offset < length){
            int size = isLittleEndian  ?  ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset+= size + 4;
            counter++;
        }
        return counter;
    }
}
