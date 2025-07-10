package io.netnotes.engine;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.List;
import java.util.stream.Stream;

public class NoteListString extends NoteString {

    public final static char[] DELIMITER = new char[]{'/'};

    private char[] m_delimiter = DELIMITER;

    public NoteListString(String str){
        this(str.toCharArray() , DELIMITER);
    }

    public NoteListString(char[] chars, char[] delimiter){
        super(chars);
        m_delimiter =  delimiter;
    }


    @Override
    public void setString(NoteString str){
        set(str.getBytes(), str.getByteDecoding());
    }

    public static Stream<NoteString>  decode(char[] chars, char[] delim, ByteDecoding byteDecoding){
        ByteBuffer byteBuffer = ByteDecoding.charsToBytes(CharBuffer.wrap(chars), byteDecoding);
        byte[] delimBytes = ByteDecoding.charsToByteArray(delim, byteDecoding);

        return decode(byteBuffer, delimBytes, byteDecoding);
        
    }

    public static Stream<NoteString> decode( ByteBuffer charBytes, byte[] delimBytes, ByteDecoding byteDecoding){
        Stream.Builder<NoteString> builder = Stream.builder();
        int delimIndex = -1;

        while((delimIndex = ByteDecoding.findSequenceInBytes(delimBytes, charBytes)) != -1){
            int start = charBytes.position();
            int stop = delimIndex;
            int bufSize = stop - start;
            if(bufSize > 0){
                builder.accept(getNoteStringFromBuffer(charBytes, bufSize, byteDecoding));
            }else{
                builder.accept(new NoteString(new byte[0], ByteDecoding.STRING_UTF8));
            }
        
            charBytes.get(delimBytes);
        }
        
        int bufSize = charBytes.limit() - charBytes.position();
        if(bufSize > 0){
            builder.accept(getNoteStringFromBuffer(charBytes, bufSize, byteDecoding));
        }

        return builder.build();
    
    }


    public static NoteString decodeBody(char[] chars, char[] headerDelim, char[] delim, char[] footerDelim, ByteDecoding byteDecoding){
        ByteBuffer byteBuffer = ByteDecoding.charsToBytes(CharBuffer.wrap(chars), byteDecoding);
        byte[] delimBytes = ByteDecoding.charsToByteArray(delim, byteDecoding);
        byte[] headerDelimBytes = ByteDecoding.charsToByteArray(headerDelim, byteDecoding);
        byte[] footerDelimBytes = ByteDecoding.charsToByteArray(footerDelim, byteDecoding);

        return decodeBody(byteBuffer,headerDelimBytes,delimBytes, footerDelimBytes, byteDecoding);
        
    }


    public static Stream<NoteString> decode(char[] chars, char[] headerDelim, char[] delim, char[] footerDelim, ByteDecoding byteDecoding){
        ByteBuffer byteBuffer = ByteDecoding.charsToBytes(CharBuffer.wrap(chars), byteDecoding);
        byte[] delimBytes = ByteDecoding.charsToByteArray(delim, byteDecoding);
        byte[] headerDelimBytes = ByteDecoding.charsToByteArray(headerDelim, byteDecoding);
        byte[] footerDelimBytes = ByteDecoding.charsToByteArray(footerDelim, byteDecoding);

        return decode(byteBuffer,headerDelimBytes,delimBytes, footerDelimBytes, byteDecoding);
        
    }

    public static Stream<NoteString> decode( ByteBuffer charBytes, byte[] headerDelimBytes, byte[] delimBytes, byte[] footerDelimBytes, ByteDecoding byteDecoding){
        int headerIndex = ByteDecoding.findSequenceInBytes(headerDelimBytes, charBytes);
        int headerLength = headerIndex - charBytes.position();
    
      

        //header
        Stream.Builder<NoteString> noteStream = Stream.builder();
        noteStream.accept(getNoteStringFromBuffer(charBytes, headerLength, byteDecoding));
        if(headerIndex != -1){
            charBytes.get(headerDelimBytes);
        }

        int footerIndex = ByteDecoding.findSequenceInBytes(footerDelimBytes, charBytes);
        footerIndex = footerIndex == -1 ? charBytes.limit() : footerIndex;
        int bodyLength = charBytes.position() - footerIndex;
        //body
        getNoteStringList(noteStream, delimBytes, charBytes, bodyLength, byteDecoding);

        if(footerIndex != -1){
            charBytes.get(footerDelimBytes);
        }
        //footer
        int footerSize =  charBytes.limit() - charBytes.position();
        if(footerSize > 0){
            noteStream.accept(getNoteStringFromBuffer(charBytes, footerSize, byteDecoding));
        }
      
        return noteStream.build();
    }

    public static NoteString decodeBody( ByteBuffer charBytes, byte[] headerDelimBytes, byte[] delimBytes, byte[] footerDelimBytes, ByteDecoding byteDecoding){
        int headerIndex = ByteDecoding.findSequenceInBytes(headerDelimBytes, charBytes);
        int headerLength = headerIndex - charBytes.position();
    
       
       getNoteStringFromBuffer(charBytes, headerLength, byteDecoding);
        if(headerIndex != -1){
            charBytes.get(headerDelimBytes);
        }

        int footerIndex = ByteDecoding.findSequenceInBytes(footerDelimBytes, charBytes);
        footerIndex = footerIndex == -1 ? charBytes.limit() : footerIndex;

        int bodyLength = charBytes.position() - footerIndex;

        return getNoteStringFromBuffer(charBytes, bodyLength, byteDecoding);


    }

    


    public static void getNoteStringList(Stream.Builder<NoteString> builder, byte[] delimBytes,  ByteBuffer byteBuffer, int length, ByteDecoding byteDecoding){

        length = Math.min( byteBuffer.limit() - byteBuffer.position(), length);

        int delimLength = delimBytes.length;

        int count = Math.min(length, byteBuffer.limit() - byteBuffer.position());
        int delimPos = byteBuffer.position();
       
        while(count > 0 && (delimPos = ByteDecoding.findSequenceInBytes(delimBytes, byteBuffer, byteBuffer.position(), count)) != -1){
            int start = byteBuffer.position();
            int stop = delimPos;
            int bufSize = stop - start;
            if(bufSize > 0){
                builder.accept(getNoteStringFromBuffer(byteBuffer, bufSize, byteDecoding));
            }

            byteBuffer.get(delimBytes);

            count = count - (bufSize + delimLength);
        }
        
        if(count > 0){
            builder.accept(getNoteStringFromBuffer(byteBuffer, count, byteDecoding));
        }

        return;
    }

    public static void copyNoteStringList(Stream.Builder<NoteString> builder, byte[] delimBytes, ByteBuffer byteBuffer, int offset, int length, ByteDecoding byteDecoding){

        length = Math.min( byteBuffer.limit() - byteBuffer.position(), length);

        int delimLength = delimBytes.length;

        int count = Math.min(length, byteBuffer.limit() - offset);
        int delimPos = byteBuffer.position();
       
        while(count > 0 && (delimPos = ByteDecoding.findSequenceInBytes(delimBytes, byteBuffer, offset, count)) != -1){
            int stop = delimPos;
            int bufSize = stop - offset;
            
            if(bufSize > 0){
                builder.accept(copyNoteStringFromBuffer(byteBuffer, offset, bufSize, byteDecoding));
            }
            int copied = bufSize + delimLength;
            offset += copied;
            count -= copied;
        }
        
        if(count > 0){
            builder.accept(copyNoteStringFromBuffer(byteBuffer, offset, count, byteDecoding));
        }

        return;
    }


    public char[] getDelimiter(){
        return m_delimiter;
    }
    
    public NoteString[] getAsArray(){
        return getAsStream().toArray(NoteString[]::new);
    }

     public List<NoteString> getAsList(){
        return getAsStream().toList();
    }

    public Stream<NoteString> getAsStream(){
        return decode(super.decodeCharArray(),  m_delimiter, getByteDecoding());
    }



    public NoteString getFirst(){
        return getAsList().get(0);
    }

     public NoteString getLast(){
        List<NoteString> list = getAsList();

        int size = list.size();

        return size > 0 ? getAsList().get(size -1) : null;
    }


}
