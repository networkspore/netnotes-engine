package io.netnotes.engine;

import com.google.gson.JsonObject;

public class NoteJsonObject extends NoteString {
    public NoteJsonObject(JsonObject json){
        super(json.toString());
    }
}
