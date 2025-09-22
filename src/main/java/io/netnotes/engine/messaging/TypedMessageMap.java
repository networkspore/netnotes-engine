package io.netnotes.engine.messaging;

import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class TypedMessageMap extends MessageHeader {
    
    public static final NoteBytesReadOnly HEADER_KEY = new NoteBytesReadOnly("TMM1");

    public static final NoteBytesReadOnly TYPE_KEY = new NoteBytesReadOnly(new byte[]{ 0x11});
    public static final NoteBytesReadOnly DATA_KEY = new NoteBytesReadOnly(new byte[]{ 0x12}); 

    private final NoteBytesMap m_values;
    
    public TypedMessageMap(NoteBytesReader reader) throws EOFException, IOException {
        super(HEADER_KEY);
        NoteBytesMetaData headerMetaData = reader.nextMetaData();
        if(headerMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            throw new IOException("Invalid header body type");
        }
        int size = headerMetaData.getLength();
        if(size < ((NoteBytesMetaData.STANDARD_META_DATA_SIZE *2) + 1)){
            throw new IOException("Header contents too small: " + size);
        }
        int bytesRemaining = size;

        m_values = new NoteBytesMap(reader, bytesRemaining);
        
        if(bytesRemaining < 0){
            throw new IOException("Corrupt header detected");
        }
    }
    
      
    public NoteBytesMap values(){
        return m_values;
    }

    public NoteBytes getTimestamp() {
        return m_values.get(TIME_STAMP_KEY);
    }

     public NoteBytes getData() {
        return m_values.get(DATA_KEY);
    }


     public NoteBytes getType() {
        return m_values.get(TYPE_KEY);
    }

    public static NoteBytesObject createHeader(NoteBytes senderId, String type, NoteBytes data){


        NoteBytesMap map = new NoteBytesMap(new NoteBytesPair[]{
            new NoteBytesPair(SENDER_ID_KEY, senderId),
            new NoteBytesPair(TYPE_KEY, type),
            new NoteBytesPair(DATA_KEY, data),
            new NoteBytesPair(TIME_STAMP_KEY, System.currentTimeMillis())
        });

        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(HEADER_KEY, map.getNoteBytesObject())
        });
    }

  
}


