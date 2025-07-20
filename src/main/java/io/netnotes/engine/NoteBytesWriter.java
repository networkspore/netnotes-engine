package io.netnotes.engine;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class NoteBytesWriter implements AutoCloseable {
    private final DataOutputStream m_out;
    public NoteBytesWriter(OutputStream outputStream){
        m_out = new DataOutputStream(outputStream);
    }

    public int write(NoteBytes noteBytes) throws IOException{
        byte[] bytes = noteBytes.get();
        int byteLength = bytes.length;
        m_out.writeInt(byteLength);
        m_out.write(bytes);
        return 4 + byteLength;
    }

    public int write(NoteBytesPair pair) throws IOException{
        return write(pair.getKey()) + write(pair.getValue());
    }

    public int write(NoteBytesObject noteBytesObject) throws IOException{
        NoteBytesPair[] pairs = noteBytesObject.getAsArray();
        int count = 0;
        for(int i = 0; i < pairs.length ;i++){
            count += write(pairs[i]);
        }
        return count;
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


}
