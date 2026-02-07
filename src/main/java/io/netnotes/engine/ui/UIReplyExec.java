package io.netnotes.engine.ui;

import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.noteBytes.NoteBytesReadOnly;

@FunctionalInterface
public interface UIReplyExec {
    void reply(RoutedPacket packet, NoteBytesReadOnly message);
}
