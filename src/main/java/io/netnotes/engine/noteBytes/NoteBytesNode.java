package io.netnotes.engine.noteBytes;

public class NoteBytesNode {
    private NoteBytes m_data;
    private NoteBytesNode setLeft;
    private NoteBytesNode m_right;
    
    public NoteBytesNode(NoteBytes data) {
        this.m_data = data;
        setLeft = null;
        m_right = null;
    }

    public NoteBytes getData() {
        return m_data;
    }

    public void setData(NoteBytes data) {
        this.m_data = data;
    }

    public NoteBytesNode getLeft() {
        return setLeft;
    }

     public void setLeft(NoteBytesNode left) {
        this.setLeft = left;
    }

    public NoteBytesNode getRight() {
        return m_right;
    }

    public void setRight(NoteBytesNode right) {
        this.m_right = right;
    }


}