package io.netnotes.engine;

public class NoteLong extends NoteBytes{
    public NoteLong(long l){
        super(ByteDecoding.longToBytesBigEndian(l));
    }

    public NoteLong create(Long l){
        return new NoteLong(l);
    }
}
