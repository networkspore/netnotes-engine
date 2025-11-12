package io.netnotes.engine.io;

/**
 * Input source with paired event parser.
 * Each source knows how to parse its own binary event format.
 */
public interface InputSource {
    
    /**
     * Start emitting events to the output stream
     */
    void start() throws Exception;
    
    /**
     * Stop emitting events
     */
    void stop();
    
    InputSourceCapabilities getCapabilities();
    
}