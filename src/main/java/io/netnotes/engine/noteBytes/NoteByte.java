package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteByte extends NoteBytes {

    public static final NoteByte ZERO = new NoteByte((byte) 0);
    public static final NoteByte ONE = new NoteByte((byte) 1);

    public NoteByte(byte value){
        super(new byte[]{ value },  NoteBytesMetaData.BYTE_TYPE);
    }

}
