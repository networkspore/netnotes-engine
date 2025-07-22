package io.netnotes.engine.noteBytes;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import io.netnotes.engine.ByteDecoding;

public class NoteBytesReader implements AutoCloseable{
    private final InputStream m_in;
    
    public NoteBytesReader(InputStream is){
        m_in = is;
    }

    public NoteBytes nextNoteBytes() throws EOFException, IOException{
        return nextNoteBytes(ByteDecoding.RAW_BYTES);
    }

    public NoteBytes nextNoteBytes(ByteDecoding byteDecoding) throws EOFException, IOException{
        int b = m_in.read();
        if(b != -1){
            byte[] threeBytes = new byte[3];
            m_in.read(threeBytes);
            int len = ByteDecoding.bytesToInt( new byte[]{(byte) b, threeBytes[0], threeBytes[1], threeBytes[2]}, byteDecoding);

            byte[] data = new byte[len];
            m_in.read(data);
            return new NoteBytes(data, byteDecoding);
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        m_in.close();
    }
}

