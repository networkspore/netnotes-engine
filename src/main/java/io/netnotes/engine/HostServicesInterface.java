package io.netnotes.engine;

public interface HostServicesInterface {
    void showDocument(String url);

    String getCodeBase();

    String getDocumentBase();

    String resolveURI(String base, String rel);
}
