package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;

public interface RoutedEvent {

    ContextPath getSourcePath();

}