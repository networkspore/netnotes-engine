package io.netnotes.engine;


import java.lang.Number;

public interface NoteMsgInterface  {

    String getId();
    void sendMessage(int code, long timestamp, NoteBytes networkId, String msg);

}
