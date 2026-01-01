package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.LoggingHelpers.Log;

public class NoteBytesArray extends NoteBytes{

    private int m_length = 0;
    private static final int INITAL_SIZE = 32;

    public NoteBytesArray(){
        super(new byte[INITAL_SIZE], NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE);
    }

    public NoteBytesArray(byte[] bytes){
        super(bytes == null ||  bytes.length == 0 ? new byte[INITAL_SIZE] : bytes  , NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE);
        if(bytes == null){
            m_length = 0;
            return;
        }
        m_length = bytes.length;
        ensureCapacity(INITAL_SIZE);
    }

    public NoteBytesArray(NoteBytes... noteBytes){
        this(getBytesFromArray(noteBytes));
    }

    public NoteBytesArray(Stream<NoteBytes> stream){
        this(getBytesFromStream(stream));
    }

  
    public static byte[]  getBytesFromStream(Stream<NoteBytes> stream){
        NoteBytes[] noteBytesArray = stream.toArray(NoteBytes[]::new);
        return getBytesFromArray(noteBytesArray);
    }
    
    public static byte[] getBytesFromArray(NoteBytes[] noteBytesArray) {
        if (noteBytesArray == null || noteBytesArray.length == 0) {
            return new byte[0];
        }

        int totalLength = getByteLength(noteBytesArray);


        
        byte[] bytes = new byte[totalLength];
        int offset = 0;
        for (NoteBytes nb : noteBytesArray) {
            offset = NoteBytes.writeNote(nb, bytes, offset);
        }
        Log.logMsg("NoteBytesArray: " + Arrays.toString(bytes));

        return bytes;
    }

  


    public static int getByteLength(NoteBytes[] noteBytesArray){
        int size = 0;
        for(int i = 0; i < noteBytesArray.length; i ++){
            NoteBytes noteBytes = noteBytesArray[i];
            size += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return size;
    }

    public static NoteBytesArray create(NoteBytes[] noteBytesArray){
        return new NoteBytesArray(getBytesFromArray(noteBytesArray));
    }

    public Stream<NoteBytes> getAsStream(){
        return Arrays.stream(getAsArray());       
    }

    public NoteBytes[] getAsArray(){
        int size = size();
        NoteBytes[] arr = new NoteBytes[size];
        byte[] bytes = getBytesInternal();
        int length = m_length;
        int offset = 0;
        int i = 0;
        while(offset < length){
            NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
            arr[i] = noteBytes;
            i++;
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return arr;
    }

    public void set(NoteBytes[] array){
     
        int byteLength = 0;
        int arrayLength = array.length;
        for(int i = 0; i < arrayLength ; i++){
            byteLength +=(5 + array[i].byteLength());
        }
   
        ensureCapacity(byteLength);

        byte[] bytes = getBytesInternal();
        int offset = 0;
        for(int i = 0; i < arrayLength ; i++){
            NoteBytes src = array[i];
            offset += NoteBytes.writeNote(src, bytes, offset);
        }
        m_length = byteLength;
    }

    protected void setInternalLength(int length){
        m_length = length;
    }



    

    public List<NoteBytes> getAsList(){
        return Arrays.asList(getAsArray());        
    }

    public NoteBytes getAt(int index){
     
        byte[] bytes = getBytesInternal();
        int length = m_length;
        int offset = 0;
        int counter = 0;

        while(offset < length){
            byte type = bytes[offset];
            offset++;
            int size = ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += 4;
            if(counter == index){
                byte[] dst = new byte[size];
                System.arraycopy(bytes, offset, dst, 0, size);
                return NoteBytes.of(dst, type);
            }
            offset += size;
            counter++;
        }
        return null;
        
    }


    @Override
    public boolean equalsBytes(byte[] bytes){
        if(isRuined()){
            return false;
        }
        byte[] value = getBytesInternal();
        if(m_length != bytes.length){
            return false;
        }
        if(m_length == 0 && bytes.length == 0){
            return true;
        }
        return Arrays.equals(value, 0, m_length, bytes, 0, bytes.length);
    }



    public boolean contains(NoteBytes noteBytes){
        return indexOf(noteBytes) != -1;
    }

    public boolean arrayStartsWith(NoteBytes noteBytes){        
        byte[] src = getBytesInternal();
        byte[] dst = noteBytes.get();

        int dstLength = dst.length;

        if(m_length > dstLength){
            return false;
        }
        if(m_length == dstLength){
            return noteBytes.equals(this);
        }
        
        return Arrays.equals(src, 0, m_length, dst, 0, dstLength);
    }
    
    public int indexOf(NoteBytes noteBytes){
        byte[] a = getBytesInternal();
        byte[] b = noteBytes.get(); 
        byte bType = noteBytes.getType();
        int offset = 0;
        int counter = 0;
        while(offset < m_length){
            byte aType = a[offset];
            offset++;
            int size = ByteDecoding.bytesToIntBigEndian(a, offset);
            offset += 4;

            if(aType == bType &&  Arrays.equals(a, offset, size, b, 0, b.length)){
                return counter;
            }
            offset += size;
            counter++;
        }
        return -1;
    }
   

    public void add(NoteBytes noteBytes) {
        if (noteBytes == null) return;
        int needed = 5 + noteBytes.byteLength();
        ensureCapacity(m_length + needed);
        m_length = NoteBytes.writeNote(noteBytes, getBytesInternal(), m_length);
    }

    protected void ensureCapacity(int minCapacity) {
        byte[] buffer = getBytesInternal();
        if (buffer.length >= minCapacity) return;

        int newCapacity = Math.max(buffer.length * 2, minCapacity);
        byte[] expanded = Arrays.copyOf(buffer, newCapacity);
        super.set(expanded, getType());
    }
 

    public boolean add(int index, NoteBytes noteBytes) {
        byte[] bytes = getBytesInternal();
        byte[] src = noteBytes.get();
        int srcLen = src.length;
        int noteTotalLen = 5 + srcLen; // 1-byte type + 4-byte length prefix
        int type = noteBytes.getType();

        // Validate index position
        if (index < 0 || index > size()) {
            return false;
        }

        // Find byte offset where to insert
        int insertOffset = findOffsetForIndex(index);
        int newLength = m_length + noteTotalLen;

        ensureCapacity(newLength);

        // shift right existing bytes to make space
        int tailLen = m_length - insertOffset;
        if (tailLen > 0) {
            System.arraycopy(bytes, insertOffset, bytes, insertOffset + noteTotalLen, tailLen);
        }

        // write metadata
        int offset = insertOffset;
        bytes[offset++] = (byte) type;
        bytes[offset++] = (byte) (srcLen >>> 24);
        bytes[offset++] = (byte) (srcLen >>> 16);
        bytes[offset++] = (byte) (srcLen >>> 8);
        bytes[offset++] = (byte) (srcLen);

        // copy payload
        System.arraycopy(src, 0, bytes, offset, srcLen);

        // update logical length
        m_length = newLength;

        return true;
    }

    public int findOffsetForIndex(int index) {
        int offset = 0;
        int count = 0;
        byte[] bytes = getBytesInternal();

        while (offset < m_length && count < index) {
            if (offset + 5 > m_length) break; // invalid/truncated note
            int srcLen = ByteDecoding.bytesToIntBigEndian(bytes, offset + 1);
            offset += 5 + srcLen;
            count++;
        }

        return offset; // if index == count, this is insert position
    }

    public NoteBytes set(int noteBytesIndex, NoteBytes noteBytes) {
        byte[] buffer = getBytesInternal();
        int offset = 0;
        int count = 0;

        // locate the target note
        while (offset + 5 <= m_length && count < noteBytesIndex) {
            int len = ByteDecoding.bytesToIntBigEndian(buffer, offset + 1);
            offset += 5 + len;
            count++;
        }

        if (count != noteBytesIndex || offset + 5 > m_length) {
            throw new IndexOutOfBoundsException("Invalid index: " + noteBytesIndex);
        }

        // read existing note
        byte oldType = buffer[offset];
        int oldLen = ByteDecoding.bytesToIntBigEndian(buffer, offset + 1);
        NoteBytes oldNote = new NoteBytes(Arrays.copyOfRange(buffer, offset + 5, offset + 5 + oldLen), oldType);

        byte[] newData = noteBytes.get();
        int newLen = newData.length;
        int delta = newLen - oldLen;
        int newTotalLength = m_length + delta;

        // expand buffer if necessary
        ensureCapacity(newTotalLength);

        // shift tail if new note length differs
        int tailOffset = offset + 5 + oldLen;
        int tailLength = m_length - tailOffset;
        if (delta != 0 && tailLength > 0) {
            System.arraycopy(buffer, tailOffset, buffer, tailOffset + delta, tailLength);
        }

        // write new note metadata
        buffer[offset] = noteBytes.getType();
        buffer[offset + 1] = (byte) (newLen >>> 24);
        buffer[offset + 2] = (byte) (newLen >>> 16);
        buffer[offset + 3] = (byte) (newLen >>> 8);
        buffer[offset + 4] = (byte) (newLen);

        // write new note data
        System.arraycopy(newData, 0, buffer, offset + 5, newLen);

        // update logical length
        m_length = newTotalLength;

        return oldNote;
    }
    
    public NoteBytes remove(NoteBytes noteBytes) {
        byte[] bytes = getBytesInternal();
        
        byte[] removeBytes = noteBytes.get(); 
        int noteBytesLength = removeBytes.length;

        int length = m_length;
        if (bytes == null || length == 0) {
            return null;
        }
        if(length < noteBytesLength){
            return null;
        }
        int searchType = noteBytes.getType();
        int offset = 0;

        while (offset < length) {
             if (offset + 5 > length) break;

            int offsetStart = offset;
            byte type = bytes[offsetStart];
            offset ++;

            int srcLength = ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += 4;
             if (offset + srcLength > length) break;
            //equals(byte[] a, int aFromIndex, int aToIndex, byte[] b, int bFromIndex, int bToIndex) {

            if (searchType == type && Arrays.equals(bytes, offset,  offset + srcLength, removeBytes, 0, noteBytesLength)) {
                int removedAmount = 5 + srcLength;
                int remainingLength = length - (offsetStart + removedAmount);
                
                if (remainingLength > 0) {
                    System.arraycopy(bytes, offset + srcLength, bytes, offsetStart, remainingLength);
                }

                m_length = Math.max(0, length - removedAmount);
            
                return noteBytes;
            } 

            offset += srcLength;
        }

        return null;
    }

    public NoteBytes remove(int index) {
        byte[] bytes = getBytesInternal();
        
        int length = m_length;
        if (bytes == null || length == 0) {
            return null;
        }
        int offset = 0;
        int i = 0;
        while (offset < length) {
             if (offset + 5 > length) break;

            int offsetStart = offset;
            byte type = bytes[offsetStart];
            offset ++;

            int srcLength = ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += 4;
            if (offset + srcLength > length) break;
            //equals(byte[] a, int aFromIndex, int aToIndex, byte[] b, int bFromIndex, int bToIndex) {

            if (i == index) {
                byte[] removedBytes = new byte[srcLength];
                System.arraycopy(bytes, offset, removedBytes, 0, srcLength);

                NoteBytes noteBytes = new NoteBytes(removedBytes, type);

                int removedAmount = 5 + srcLength;
                int remainingLength = length - (offsetStart + removedAmount);
                
                if (remainingLength > 0) {
                    System.arraycopy(bytes, offset + srcLength, bytes, offsetStart, remainingLength);
                }

                m_length = Math.max(0, length - removedAmount);
            
                return noteBytes;
            } 

            offset += srcLength;
        }

        return null;
    }

    @Override
    public int hashCode(){
        return Arrays.hashCode(get());
    }

    public NoteBytes get(int index){
        return getAt(index);
    }

    public int size(){
        byte[] bytes = getBytesInternal();
        if(bytes == null){
            return -1;
        }else if(bytes.length == 0){
            return 0;
        }else{
            int length = m_length;
            int offset = 0;
            int counter = 0;
           
            while(offset < length){
                offset++;
                int size = ByteDecoding.bytesToIntBigEndian(bytes, offset);
                offset += 4 + size;
                counter++;
            }
            return counter;
        }
    }

    public void clear(){
        m_length = 0;
    }

      @Override
     public JsonElement getAsJsonElement(){
        return getAsJsonArray();
    }
    
    @Override
    public JsonArray getAsJsonArray(){
        byte[] bytes = get();
        if(bytes != null){
            int length = m_length;
            if(length == 0){
                return new JsonArray();
            }
            JsonArray jsonArray = new JsonArray();
            
            int offset = 0;
    
            while(offset < length){
                NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
                byte type = noteBytes.getType();
                
                if(type == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE){
                    jsonArray.add(noteBytes.getAsJsonArray());
                } else if(type == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
                    jsonArray.add(noteBytes.getAsJsonObject());
                } else {
                    jsonArray.add(createPrimitiveForType(noteBytes));
                }
                
                offset += 5 + noteBytes.byteLength(); // 1 byte type + 4 bytes length + content
            }
           
            return jsonArray;
        }
        return null;
    }

    @Override
    public JsonObject getAsJsonObject() {
        byte[] bytes = get();
        if(bytes != null){
            int length = m_length;
            if(length == 0){
                return new JsonObject();
            }
            JsonObject jsonObject = new JsonObject();
            
            int offset = 0;
            int i = 0;
            while(offset < length){
                NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
                byte type = noteBytes.getType();
                
                if(type == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE){
                    jsonObject.add(i + "", noteBytes.getAsJsonArray());
                } else if(type == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
                    jsonObject.add(i + "", noteBytes.getAsJsonObject());
                } else {
                    jsonObject.add(i + "", createPrimitiveForType(noteBytes) );
                }
                
                offset += 5 + noteBytes.byteLength(); // 1 byte type + 4 bytes length + content
                i++;
            }
            return jsonObject;
        }
        return null;
    }


    @Override
    public NoteBytesArray copy(){
        return new NoteBytesArray(get());
    }

    @Override 
    public String toString(){
        return getAsJsonArray().toString();
    }

    @Override
    public byte[] get() {
        byte[] src = getBytesInternal();
        return Arrays.copyOf(src, m_length);
    }

    @Override
    public byte[] getBytes(){
        return get();
    }


    @Override
    public void set(byte[] bytes, byte type) {
        ensureCapacity(bytes.length);
        m_length = bytes.length;
        byte[] internalBytes = getBytesInternal();
        System.arraycopy(bytes, 0, internalBytes, 0, m_length);
    }

    @Override
    public int byteLength() {
        return m_length;
    }


    
   
}
