package io.netnotes.engine.io.input.events;

import io.netnotes.engine.utils.exec.ExecutorConsumer;

public interface IRoutedEventSource {
    void addEventConsumer(String id, ExecutorConsumer<RoutedEvent> event);
    ExecutorConsumer<RoutedEvent> removeEventConsumer(String id);
    ExecutorConsumer<RoutedEvent> getEventConsumer(String id);
}
