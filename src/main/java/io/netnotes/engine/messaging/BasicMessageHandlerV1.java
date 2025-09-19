package io.netnotes.engine.messaging;

import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesPair;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReader;

public class BasicMessageHandlerV1 extends MessageHeader {
    
    public static final NoteBytesReadOnly HEADER_KEY = new NoteBytesReadOnly(new byte[]{ 0x42, 0x52, 0x48, 0x31}); // "BRH1"

    public static final NoteBytesReadOnly TYPE_KEY = new NoteBytesReadOnly(new byte[]{ 0x11});
    public static final NoteBytesReadOnly DATA_KEY = new NoteBytesReadOnly(new byte[]{ 0x12}); 

    private ConcurrentHashMap<NoteBytesReadOnly, NoteBytesReadOnly> m_values = new ConcurrentHashMap<>();
    
    public BasicMessageHandlerV1(NoteBytesReader reader) throws EOFException, IOException {
        super(HEADER_KEY);
        NoteBytesMetaData headerMetaData = reader.nextMetaData();
        if(headerMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            throw new IOException("Invalid header body type");
        }
        int size = headerMetaData.getLength();
        if(size < 6){
            throw new IOException("Header contents too small: " + size);
        }
        int bytesRemaining = size;
        while(bytesRemaining > 0){
            NoteBytesReadOnly key = reader.nextNoteBytesReadOnly();
            NoteBytesReadOnly value = reader.nextNoteBytesReadOnly();
            if(key == null || value == null){
                throw new IOException("Unexpected end of stream");
            }
            m_values.putIfAbsent(key, value);
            bytesRemaining -= (key.byteLength() + value.byteLength() + 10);
        }
        if(bytesRemaining < 0){
            throw new IOException("Corrupt header detected");
        }
    }
    
      
    public NoteBytesReadOnly getValue(NoteBytesReadOnly key){
        return m_values.get(key);
    }
    

    public NoteBytesReadOnly getTimestamp() {
        return getValue(TIME_STAMP_KEY);
    }

     public NoteBytesReadOnly getData() {
        return getValue(DATA_KEY);
    }


     public NoteBytesReadOnly getType() {
        return getValue(TYPE_KEY);
    }

    public static NoteBytesObject createHeader(NoteBytesReadOnly senderId, String type, NoteBytes data){
        NoteBytesObject result = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(SENDER_ID_KEY, senderId),
            new NoteBytesPair(TYPE_KEY, type),
            new NoteBytesPair(DATA_KEY, data),
            new NoteBytesPair(TIME_STAMP_KEY, new NoteBytes(System.currentTimeMillis()))
        });

        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(HEADER_KEY, result)
        });
    }

  
}


