package io.netnotes.engine;


import io.netnotes.engine.noteBytes.NoteUUID;
import javafx.scene.image.WritableImage;

public class Effects {

    private String m_name;
    private String m_id;

    public Effects(String name) {
        m_id = NoteUUID.createSafeUUID128();
        m_name = name;
    }

    public Effects(String id, String name) {
        m_id = id;
        m_name = name;
    }

    public void applyEffect(WritableImage img) {

    }

    public String getName() {
        return m_name;
    }

    public String getId() {
        return m_id;
    }
}
