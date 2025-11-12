package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

interface InputEvent {

    NoteBytesReadOnly getSourceId();

}