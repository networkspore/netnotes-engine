package io.netnotes.engine.messaging;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReader;

public class MessageHeader {

    private final NoteBytesReadOnly m_headerType;

    public static final NoteBytesReadOnly SENDER_ID_KEY = new NoteBytesReadOnly(new byte[]{0x01});
    public static final NoteBytesReadOnly TIME_STAMP_KEY = new NoteBytesReadOnly(new byte[]{0x02});

    private NoteBytesReadOnly m_senderId;


    private NoteBytesReadOnly m_timeStamp;

    public MessageHeader(NoteBytesReadOnly headerType){
        m_headerType = headerType;
    }

    public NoteBytesReadOnly getHeaderType(){
        return m_headerType;
    }

    public NoteBytesReadOnly getSenderId() {
        return m_senderId;
    }

    public void setSenderId(NoteBytesReadOnly m_senderId) {
        this.m_senderId = m_senderId;
    }

    public NoteBytesReadOnly getTimeStamp() {
        return m_timeStamp;
    }

    public void setTimeStamp(NoteBytesReadOnly m_timeStamp) {
        this.m_timeStamp = m_timeStamp;
    }


    public static MessageHeader readHeader( NoteBytesReader reader) throws EOFException, IOException{
        NoteBytes header = reader.nextNoteBytes();
        if(header.equals(SecureMessageV1.HEADER_KEY)){
            return new SecureMessageV1(reader);
        }else if(header.equals(DiscoveryPackageV1.HEADER_KEY)){
            return new DiscoveryPackageV1(reader);
        }else if(header.equals(BasicMessageHandlerV1.HEADER_KEY)){
            return new BasicMessageHandlerV1(reader);
        }

        throw new IOException("Unknown header key");
    }


    public static CompletableFuture<MessageHeader> readHeader(InputStream inputStream, Executor exec) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                NoteBytesReader reader = new NoteBytesReader(inputStream);
                return readHeader(reader);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read header", e);
            }
        }, exec);
    }


}
