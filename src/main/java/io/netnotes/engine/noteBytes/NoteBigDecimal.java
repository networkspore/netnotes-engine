package io.netnotes.engine.noteBytes;

import java.math.BigDecimal;

import io.netnotes.engine.ByteDecoding;

public class NoteBigDecimal extends NoteBytes {
    public NoteBigDecimal(BigDecimal bigDecimal){
        super(ByteDecoding.bigDecimalToScaleAndBigInteger(bigDecimal));
    }

}
