package io.netnotes.engine.noteBytes;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;
public class NoteBytesReader implements AutoCloseable{
    private final InputStream m_in;
    
    public NoteBytesReader(InputStream is){
        m_in = is;
    }

    public NoteBytes nextNoteBytes() throws EOFException, IOException{
        int type = m_in.read();
        if(type != -1){
            byte[] fourBytes = new byte[4];
            m_in.read(fourBytes);
            ByteDecoding byteDecoding = ByteDecoding.getDecodingFromType((byte)type);
            int len = ByteDecoding.bytesToInt(fourBytes, byteDecoding);
            byte[] data = readByteAmount(len);
            return new NoteBytes(data, byteDecoding);
        }
        return null;
    }

    public NoteBytesReadOnly nextNoteBytesReadOnly() throws EOFException, IOException{
        int type = m_in.read();
        if(type != -1){
            byte[] fourBytes = new byte[4];
            m_in.read(fourBytes);
            ByteDecoding byteDecoding = ByteDecoding.getDecodingFromType((byte)type);
            int len = ByteDecoding.bytesToInt(fourBytes, byteDecoding);
            byte[] data = readByteAmount(len);
            return new NoteBytesReadOnly(data, byteDecoding);
        }
        return null;
    }


    public NoteBytesMetaData nextMetaData() throws EOFException, IOException{
        int type = m_in.read();
        if(type != -1){
            byte[] fourBytes = new byte[4];
            int read = m_in.read(fourBytes);
            
            return read != -1 ? new NoteBytesMetaData((byte) type, fourBytes) : new NoteBytesMetaData((byte) type, 0);
        }
        return null;
    }

    public byte[] readByteAmount(int size) throws IOException{
        try(ByteArrayOutputStream byteOutput = new ByteArrayOutputStream(size)){
            int bufferSize = size < StreamUtils.BUFFER_SIZE ? size : StreamUtils.BUFFER_SIZE;
            byte[] buffer = new byte[bufferSize];
            int length = 0;
            int remaining = size;

            while(remaining > 0 && ((length = m_in.read(buffer, 0, remaining < bufferSize ? remaining : bufferSize)) != -1)){
                byteOutput.write(buffer, 0, length);
                remaining -= length;
            }
            if(length == -1){
                throw new IOException("Reached pre-mature end of stream: " + length + " expected: " + size);
            }
            return byteOutput.toByteArray();
        }
    }

    
   public int skipData(byte[] buffer, int count, int length) throws EOFException, IOException{
        
        int read = m_in.read(buffer, 0, Math.min(buffer.length, length - count));
        if(read == -1){
            throw new EOFException();
        }
        count += read;
                
        return count;
    }


    public int read(byte[] buffer, int off, int len) throws IOException{
        return m_in.read(buffer, off, len);
    }


    public int skipData(int length) throws EOFException, IOException{
        
        int count = 0;

        byte[] buffer = new byte[ StreamUtils.BUFFER_SIZE];
        while(count < length){
            int read = m_in.read(buffer, 0, Math.min(buffer.length, length - count));
            if(read == -1){
                throw new EOFException();
            }
            count += read;
        }
                    
        return count;
    }


    public InputStream getInputStream(){
        return m_in;
    }


    @Override
    public void close() throws IOException {
        m_in.close();
    }

   
}

