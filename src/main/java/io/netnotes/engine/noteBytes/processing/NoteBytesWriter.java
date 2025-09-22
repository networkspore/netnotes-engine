package io.netnotes.engine.noteBytes.processing;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.collections.NoteBytesPairEphemeral;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class NoteBytesWriter implements AutoCloseable {
    private final DataOutputStream m_out;

    public NoteBytesWriter(OutputStream outputStream){
        m_out = new DataOutputStream(outputStream);
    }


    public int write(NoteBytes noteBytes) throws IOException{
        byte[] bytes = noteBytes.get();
        int byteLength = bytes.length;
        m_out.write(noteBytes.getByteDecoding().getType());
        m_out.writeInt(byteLength);
        m_out.write(bytes);
        return NoteBytesMetaData.STANDARD_META_DATA_SIZE + byteLength;
    }

    public int write(NoteBytesMetaData metaData) throws IOException{
        m_out.write(metaData.getType());
        m_out.writeInt(metaData.getLength());
        return 5; // 1 byte type + 4 bytes length
    }

    public int write(byte[] data) throws IOException{
        m_out.write(data);
        return data.length;
    }

     public int write(byte[] data, int offset, int length) throws IOException{
        m_out.write(data, offset, length);
        return data.length;
    }

    public int write(NoteBytesPair pair) throws IOException{
        return write(pair.getKey()) + write(pair.getValue());
    }
    public int write(NoteBytesPairEphemeral pair) throws IOException{
        return write(pair.getKey()) + write(pair.getValue());
    }

    public int write(NoteBytes key, NoteBytes value) throws IOException{
        return write(key) + write(value);
    }


    public int write(NoteBytesArray noteBytesArray) throws IOException{
        NoteBytes[] noteBytes = noteBytesArray.getAsArray();
        int count = 0;
        for(int i = 0; i < noteBytes.length ;i++){
            count += write(noteBytes[i]);
        }
        return count;
    }

    @Override
    public void close() throws IOException {
        m_out.close();
    }


    public void flush() throws IOException{
        m_out.flush();
    }



}
