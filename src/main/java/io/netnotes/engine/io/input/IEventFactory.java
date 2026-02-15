package io.netnotes.engine.io.input;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.noteBytes.NoteBytes;

public interface IEventFactory {
    RoutedEvent from( ContextPath sourcePath, NoteBytes packet);
}
