package io.netnotes.engine.messaging;

import java.io.EOFException;
import java.io.IOException;

import io.netnotes.engine.messaging.header.MessageHeader;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObjectEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesMapEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesPairEphemeral;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;

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

    public static NoteBytesObjectEphemeral createHeader(NoteBytes senderId, NoteBytesReadOnly type, NoteBytes data){


       try( NoteBytesMapEphemeral map = new NoteBytesMapEphemeral(new NoteBytesPairEphemeral[]{
            new NoteBytesPairEphemeral(SENDER_ID_KEY, senderId),
            new NoteBytesPairEphemeral(TYPE_KEY, type),
            new NoteBytesPairEphemeral(DATA_KEY, data),
            new NoteBytesPairEphemeral(TIME_STAMP_KEY, System.currentTimeMillis())
        });){
            return new NoteBytesObjectEphemeral(new NoteBytesPairEphemeral[]{
                new NoteBytesPairEphemeral(HEADER_KEY, map.getNoteBytesObjectEphemeral())
            });
        }
    }

    public static NoteBytesObjectEphemeral createHeader(NoteBytes senderId, String type, NoteBytes data){


        try(NoteBytesMapEphemeral map = new NoteBytesMapEphemeral(new NoteBytesPairEphemeral[]{
            new NoteBytesPairEphemeral(SENDER_ID_KEY, senderId),
            new NoteBytesPairEphemeral(TYPE_KEY, type),
            new NoteBytesPairEphemeral(DATA_KEY, data),
            new NoteBytesPairEphemeral(TIME_STAMP_KEY, System.currentTimeMillis())
        });){
            return new NoteBytesObjectEphemeral(new NoteBytesPairEphemeral[]{
                new NoteBytesPairEphemeral(HEADER_KEY, map.getNoteBytesObjectEphemeral())
            });
        }
    }

  
}


