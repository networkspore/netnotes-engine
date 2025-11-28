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
            byte[] fourBytes = new byte[4];
            int read = m_in.read(fourBytes);
            
            return read != -1 ? new NoteBytesMetaData((byte) type, fourBytes) : new NoteBytesMetaData((byte) type, 0);
        }
        return null;
    }

    public byte[] readNextBytes(int size) throws IOException{
        int bufferSize = size < StreamUtils.BUFFER_SIZE ? size : StreamUtils.BUFFER_SIZE;
        byte[] buf = new byte[size];

        if(size <= StreamUtils.BUFFER_SIZE ){
            int readLength = m_in.read(buf, 0, size);
           
            if(readLength != size){
                throw new EOFException("Reached pre-mature end of stream expected: " + size);
            }
            return buf;
        }else{
            byte[] buffer = new byte[bufferSize];

            int readLength = 0;
            int remaining = size;
            int writeOffset = 0;
            while(remaining > 0 && ((readLength = m_in.read(buffer, 0, remaining < bufferSize ? remaining : bufferSize)) != -1)){
                System.arraycopy(buffer, 0, buf, writeOffset, readLength);
                writeOffset += readLength;
                remaining -= readLength;
            }
            if(remaining > 0){
                throw new EOFException("Reached pre-mature end of stream expected: " + size);
            }
            return buf;
        }
    }

    /****
     * 
     * @param buf buffer to write to
     * @param offset offset of buffer to write to
     * @param size bytes to write
     * @return offset after writing bytes to buffer
     * @throws IOException
     */
    public int readNextBytes(byte[] buf, int offset, int size) throws IOException{
        if(size <= StreamUtils.BUFFER_SIZE ){

            int readLength = m_in.read(buf, 0, size);
           
            if(readLength != size){
                throw new EOFException("Reached pre-mature end of stream expected: " + size);
            }
            return offset += readLength;
        }else{
            int bufferSize = StreamUtils.BUFFER_SIZE;
            byte[] buffer = new byte[bufferSize];

            int readLength = 0;
            int remaining = size;
            int writeOffset = offset;
            while(remaining > 0 && ((readLength = m_in.read(buffer, 0, remaining < bufferSize ? remaining : bufferSize)) != -1)){
                System.arraycopy(buffer, 0, buf, writeOffset, readLength);
                writeOffset += readLength;
                remaining -= readLength;
            }
            if(remaining > 0){
                throw new EOFException("Reached pre-mature end of stream expected: " + size);
            }
            return writeOffset;
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

    /**
     * 
     * @param buffer
     * @param off
     * @param len
     * @return return bytes read
     * @throws IOException
     */

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

