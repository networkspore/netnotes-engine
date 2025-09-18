package io.netnotes.engine.messaging;

import java.io.EOFException;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReader;
import io.netnotes.engine.utils.Utils;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public class MessageHeader {

    private final NoteBytesReadOnly m_headerType;


    
    public static final NoteBytesReadOnly SENDER_ID_KEY = new NoteBytesReadOnly(new byte[]{0x01});
    public static final NoteBytesReadOnly TIME_STAMP_KEY = new NoteBytesReadOnly(new byte[]{0x02});
    public static final NoteBytesReadOnly MESSAGE_ID_KEY = new NoteBytesReadOnly(new byte[]{0x01});

    public MessageHeader(NoteBytesReadOnly headerType){
        m_headerType = headerType;
    }

    public NoteBytesReadOnly getHeaderType(){
        return m_headerType;
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

        throw new IOException("Only MessageHandlerV1 currently supported");
    }



    public static Future<?> readHeader(
        PipedInputStream inputStream,
        ExecutorService execService,
        EventHandler<WorkerStateEvent> onSucceeded,
        EventHandler<WorkerStateEvent> onFailed
    ){
           Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {

                NoteBytesReader reader = new NoteBytesReader(inputStream);

                return readHeader(reader);
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    
}
