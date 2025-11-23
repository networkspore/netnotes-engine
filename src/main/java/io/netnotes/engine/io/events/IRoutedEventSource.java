package io.netnotes.engine.io.events;


public interface IRoutedEventSource {
    void addEventConsumer(String id, ExecutorConsumer<RoutedEvent> event);
    ExecutorConsumer<RoutedEvent> removeEventConsumer(String id);
    ExecutorConsumer<RoutedEvent> getEventConsumer(String id);
}
