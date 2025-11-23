package io.netnotes.engine.io.events;

import io.netnotes.engine.io.ContextPath;

public interface RoutedEvent {

    ContextPath getSourcePath();

}