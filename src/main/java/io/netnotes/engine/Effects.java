package io.netnotes.engine;


import javafx.scene.image.WritableImage;

public class Effects {

    private String m_name;
    private NoteBytes m_id;

    public Effects(String name) {
        m_id = NoteUUID.createLocalUUID128();
        m_name = name;
    }

    public Effects(NoteBytes id, String name) {
        m_id = id;
        m_name = name;
    }

    public void applyEffect(WritableImage img) {

    }

    public String getName() {
        return m_name;
    }

    public NoteBytes getId() {
        return m_id;
    }
}
