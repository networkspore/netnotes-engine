package io.netnotes.engine.core;

public interface HostServicesInterface {
    void showDocument(String url);

    String getCodeBase();

    String getDocumentBase();

    String resolveURI(String base, String rel);
}
