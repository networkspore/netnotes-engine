package io.netnotes.engine.messaging;

import java.io.IOException;

import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesMap;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesPair;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReader;


public class DiscoveryPackageV1 extends MessageHeader {
    public static final NoteBytesReadOnly HEADER_KEY = new NoteBytesReadOnly(new byte[]{ 0x44, 0x50, 0x4B, 0x47, 0x31}); // "DPKG1"

    public static final NoteBytesReadOnly CAPABILITIES_KEY = new NoteBytesReadOnly(new byte[]{0x11});
    public static final NoteBytesReadOnly PROTOCOLS_KEY = new NoteBytesReadOnly(new byte[]{0x12}); //if MessageHandlerV1 -> includes security levels

    private NoteBytesMap m_protocolsMap = new NoteBytesMap();
    private NoteBytesMap m_capabilitiesMap = new NoteBytesMap();

    public DiscoveryPackageV1(NoteBytesReader reader) throws IOException {
        super(HEADER_KEY);
        NoteBytesMetaData headerMetaData = reader.nextMetaData();

        if(headerMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            throw new IOException("Invalid header body type");
        }

        int size = headerMetaData.getLength();
        
        if(size < (NoteBytesMetaData.STANDARD_META_DATA_SIZE *2) + 1){
            throw new IOException("Header contents too small: " + size);
        }

        int bytesRemaining = size;

        while(bytesRemaining > 0){
            NoteBytesReadOnly key = reader.nextNoteBytesReadOnly();
            NoteBytesReadOnly value = reader.nextNoteBytesReadOnly();
            if(key == null || value == null){
                throw new IOException("Unexpected end of stream");
            }
            updateData(key, value);

            bytesRemaining -= (key.byteLength() + value.byteLength() + (NoteBytesMetaData.STANDARD_META_DATA_SIZE *2));
        }

        if(bytesRemaining < 0){
             throw new IOException("Corrupt header detected");
        }
    }

    private void updateData(NoteBytesReadOnly key, NoteBytesReadOnly value) throws IOException{
        
        if(key.equals(SENDER_ID_KEY)){
            setSenderId(value);
        }else if(key.equals(TIME_STAMP_KEY)){
            setTimeStamp(value);
        }else if(key.equals(CAPABILITIES_KEY)){
            m_capabilitiesMap = new NoteBytesMap(value);
        }else if(key.equals(PROTOCOLS_KEY)){
            m_protocolsMap = new NoteBytesMap(value);
        }
    }
    
    public NoteBytesMap getProtocolsMap(){
        return m_protocolsMap;
    }
    
    public NoteBytesMap getCapabilitiesMap(){
        return m_capabilitiesMap;
    }
 
    // Factory method for creating discovery packages
    public static NoteBytesObject createPackage(NoteBytesObject object){
        if(!object.contains(SENDER_ID_KEY)){
            throw new IllegalArgumentException("Requires NodeId");
        }


        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(HEADER_KEY, object)
        });
    }
    


 
    public static NoteBytesObject createStandardDiscovery(NoteBytes nodeId, NoteBytesMap capabilities, NoteBytesMap protocols) {
        NoteBytesObject discoveryData = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(SENDER_ID_KEY, nodeId),
            new NoteBytesPair(CAPABILITIES_KEY, capabilities),
            new NoteBytesPair(PROTOCOLS_KEY, protocols),
            new NoteBytesPair(TIME_STAMP_KEY, System.currentTimeMillis())
        });
        return createPackage(discoveryData);
    }

   

}
/*public class DiscoveryPackageV1 extends MessageHeader {
    public static final NoteBytesReadOnly NODE_ID_KEY = new NoteBytesReadOnly("NodeId");
    public static final NoteBytesReadOnly HEADER_KEY = new NoteBytesReadOnly(new byte[]{ 0x68, 0x79, 0x75, 0x71, 0x01}); // "DPKG1"

    private ConcurrentHashMap<NoteBytesReadOnly, NoteBytesReadOnly> m_values = new ConcurrentHashMap<>();

    public DiscoveryPackageV1(NoteBytesReader reader) throws EOFException, IOException{
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
    

    public static NoteBytesPair createPackage(NoteBytesObject object){
        if(!object.contains(NODE_ID_KEY)){
            throw new IllegalArgumentException("Requires NodeId");
        }
        return new NoteBytesPair(HEADER_KEY, object);
    }

}*/

