package io.netnotes.engine;

import java.math.BigDecimal;

public class NoteBigDecimal extends NoteBytes {
    public NoteBigDecimal(BigDecimal bigDecimal){
        super(ByteDecoding.bigDecimalToScaleAndBigInteger(bigDecimal));
    }

}
