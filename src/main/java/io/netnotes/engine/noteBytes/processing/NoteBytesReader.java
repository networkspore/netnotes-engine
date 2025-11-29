package io.netnotes.engine.noteBytes.processing;


import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.utils.streams.StreamUtils;

public class NoteBytesReader implements AutoCloseable{
    private final InputStream m_in;
    
    public NoteBytesReader(InputStream is){
        m_in = is;
    }

    public NoteBytes nextNoteBytes() throws IOException{
        int type = m_in.read();
        if(type != -1){
            byte[] fourBytes = new byte[4];
            m_in.read(fourBytes);
            int len = ByteDecoding.bytesToIntBigEndian(fourBytes);
            byte[] data = readNextBytes(len);
     
            return NoteBytes.of(data, (byte)type);
        }
        return null;
    }

    public NoteBytesEphemeral nextNoteBytesEphemeral() throws IOException{
        int type = m_in.read();
        if(type != -1){
            byte[] fourBytes = new byte[4];
            m_in.read(fourBytes);
            
            int len = ByteDecoding.bytesToIntBigEndian(fourBytes);
            byte[] data = readNextBytes(len);
     
            return new NoteBytesEphemeral(data, (byte) type);
        }
        return null;
    }


    public NoteBytesReadOnly nextNoteBytesReadOnly() throws IOException{
        int type = m_in.read();
        if(type != -1){
            byte[] fourBytes = new byte[4];
            m_in.read(fourBytes);
            int len = ByteDecoding.bytesToIntBigEndian(fourBytes);
            byte[] data = readNextBytes(len);
            return new NoteBytesReadOnly(data, (byte) type);
        }
        return null;
    }




    public NoteBytesMetaData nextMetaData() throws IOException{
        int type = m_in.read();
        if(type != -1){
            byte[] fourBytes = readNextBytes(4);
            
            return new NoteBytesMetaData((byte) type, fourBytes);
        }
        return null;
    }

    public byte[] readNextBytes(int size) throws IOException{

        int bufferSize = size < StreamUtils.BUFFER_SIZE ? size : StreamUtils.BUFFER_SIZE;
        byte[] buffer = new byte[bufferSize];
        
        return readNextBytes(bufferSize, buffer);
    }

    public byte[] readNextBytes(int size, byte[] buffer) throws IOException{
        int bufferSize = size < buffer.length ? size : buffer.length;
        int length = 0;
        int remaining = size;
        byte[] byteOutput = new byte[size];
        int offset = 0;
        while(remaining > 0 && ((length = m_in.read(buffer, 0, remaining < bufferSize ? remaining : bufferSize)) != -1)){
            System.arraycopy(buffer, 0, byteOutput, offset, length);
            offset += length;
            remaining -= length;
        }
        if(remaining > 0){
            throw new EOFException("Reached pre-mature end of stream expected: " + size);
        }
        return byteOutput;
        
    }

    /****
     * 
     * @param buf buffer to write to
     * @param offset offset of buffer to write to
     * @param length bytes to write
     * @return offset after writing bytes to buffer
     * @throws IOException
     */

    public int readNextBytes(byte[] buf, int offset, int length) throws IOException{
    
        int bufferSize = length < StreamUtils.BUFFER_SIZE ? length : StreamUtils.BUFFER_SIZE;
        byte[] buffer = new byte[bufferSize];
        int readLength = 0;
        int remaining = length;
        int writeOffset = offset;
        while(remaining > 0 && ((readLength = m_in.read(buffer, 0, remaining < bufferSize ? remaining : bufferSize)) != -1)){
            System.arraycopy(buffer, 0, buf, writeOffset, readLength);
            writeOffset += readLength;
            remaining -= readLength;
        }
        if(remaining > 0){
            throw new EOFException("Reached pre-mature end of stream expected: " + length);
        }
        return writeOffset;
        
    }



    
   /*public int skipData(byte[] buffer, int count, int length) throws EOFException, IOException{
        
        int read = m_in.read(buffer, 0, Math.min(buffer.length, length - count));
        if(read == -1){
            throw new EOFException();
        }
        count += read;
                
        return count;
    }*/


    public int read(byte[] buffer, int off, int len) throws IOException{
        return m_in.read(buffer, off, len);
    }

    public int read(byte[] buffer) throws IOException{
        return m_in.read(buffer);
    }


    public int skipData(int size) throws EOFException, IOException{
        
        int count = 0;

        int bufferSize = size < StreamUtils.BUFFER_SIZE ? size : StreamUtils.BUFFER_SIZE;
        byte[] buffer = new byte[bufferSize];
        int length = 0;
        int remaining = size;
        while(remaining > 0 && ((length = m_in.read(buffer, 0, remaining < bufferSize ? remaining : bufferSize)) != -1)){
            remaining -= length;
            count += length;
        }
        if(remaining > 0){
            throw new IOException("Reached pre-mature end of stream expected: " + size);
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

