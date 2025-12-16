package io.netnotes.engine.io.process;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public class ProcessKeys {
    public static final NoteBytesReadOnly CORRELATION_ID = new NoteBytesReadOnly("correlationId");
    public static final NoteBytesReadOnly REPLY_TO = new NoteBytesReadOnly( "replyTo");
}
