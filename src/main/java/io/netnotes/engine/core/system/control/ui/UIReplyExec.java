package io.netnotes.engine.core.system.control.ui;

import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

@FunctionalInterface
public interface UIReplyExec {
    void reply(RoutedPacket packet, NoteBytesReadOnly message);
}
