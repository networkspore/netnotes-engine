package io.netnotes.engine;

import java.io.IOException;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesImage;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesPair;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import javafx.scene.image.Image;

public class NodeInformation {

    public static final NoteBytesReadOnly NODE_ID_KEY = new NoteBytesReadOnly("nodeId");
    public static final NoteBytesReadOnly NAME_KEY = new NoteBytesReadOnly("name");
    public static final NoteBytesReadOnly SMALL_ICON_KEY = new NoteBytesReadOnly("smallIcon");
    public static final NoteBytesReadOnly ICON_KEY = new NoteBytesReadOnly("icon");
    public static final NoteBytesReadOnly DESCRIPTION_KEY = new NoteBytesReadOnly("description");
    public static final NoteBytesReadOnly KEYWORDS_KEY = new NoteBytesReadOnly("keywords");
    public static final NoteBytesReadOnly SECURITY_LEVEL_KEY = new NoteBytesReadOnly("keywords");

    private final NoteBytesReadOnly m_nodeId;
    private final NoteBytesReadOnly m_name;
    private Image m_icon = null;
    private Image m_smallIcon = null;
    private final NoteBytes m_description;
    private final NoteBytes m_securityLevel;
    private final NoteBytesArrayReadOnly m_keywordsArray;

    public NodeInformation(
            NoteBytesReadOnly nodeId,
            NoteBytesReadOnly name,
            Image icon,
            Image smallIcon,
            NoteBytes description,
            NoteBytes securityLevel,
            NoteBytesArrayReadOnly keywords) {
        this.m_nodeId = nodeId;
        this.m_name = name;
        this.m_icon = icon;
        this.m_smallIcon = smallIcon;
        this.m_description = description;
        m_securityLevel = securityLevel;
        m_keywordsArray = keywords;
    }

    public NodeInformation(int byteLength, NoteBytesReader reader, NoteBytesWriter noteBytesWriter) throws IOException {
        int length = 0;
        NoteBytesReadOnly nodeId = null;
        NoteBytesReadOnly name = null;
        NoteBytesReadOnly description = null;
        NoteBytesReadOnly securityLevel = null;
        NoteBytesArrayReadOnly keywordsArray = null;
        Image icon = null;
        Image smallIcon = null;

        while (length < byteLength) {
            NoteBytes nextKey = reader.nextNoteBytes();
            NoteBytes nextValue = reader.nextNoteBytes();
            noteBytesWriter.write(nextKey);
            noteBytesWriter.write(nextValue);

            nodeId = nextKey.equals(NODE_ID_KEY) ? new NoteBytesReadOnly(nextValue.get(), nextValue.getByteDecoding())
                    : nodeId;
            name = nextKey.equals(NAME_KEY) ? new NoteBytesReadOnly(nextValue.get(), nextValue.getByteDecoding())
                    : name;
            description = nextKey.equals(DESCRIPTION_KEY)
                    ? new NoteBytesReadOnly(nextValue.get(), nextValue.getByteDecoding())
                    : description;
            securityLevel = nextKey.equals(SECURITY_LEVEL_KEY)
                    ? new NoteBytesReadOnly(nextValue.get(), nextValue.getByteDecoding())
                    : securityLevel;
            keywordsArray = nextKey.equals(KEYWORDS_KEY) ? new NoteBytesArrayReadOnly(nextValue.get()) : keywordsArray;
            try {
                icon = nextKey.equals(ICON_KEY) ? nextValue.getAsImage() : icon;
            } catch (Exception e) {
            }
            try {
                smallIcon = nextKey.equals(SMALL_ICON_KEY) ? nextValue.getAsImage() : smallIcon;
            } catch (Exception e) {
            }

            length += nextKey.byteLength() + nextValue.byteLength() + 10;
        }

        if (nodeId == null || nodeId.byteLength() == 0) {
            throw new IOException("NodeId not defined");
        }
        if (name == null || name.byteLength() == 0) {
            throw new IOException("Name not defined");
        }

        m_nodeId = nodeId;
        m_name = name;
        m_description = description;
        m_securityLevel = securityLevel;
        m_keywordsArray = keywordsArray;
        m_icon = icon;
        m_smallIcon = smallIcon;
    }

    public NodeInformation(NoteBytesObject obj) {
        NoteBytesPair nodeIdPair = obj.get(NODE_ID_KEY);
        NoteBytesPair namePair = obj.get(NAME_KEY);
        NoteBytesPair descriptionPair = obj.get(DESCRIPTION_KEY);
        NoteBytesPair iconPair = obj.get(ICON_KEY);
        NoteBytesPair smallIconPair = obj.get(SMALL_ICON_KEY);
        NoteBytesPair keywordsPair = obj.get(KEYWORDS_KEY);
        NoteBytesPair securityLevelPair = obj.get(SECURITY_LEVEL_KEY);

        this.m_nodeId = nodeIdPair.getValueAsReadOnly();
        this.m_name = namePair.getValueAsReadOnly();
        this.m_description = descriptionPair.getValueAsReadOnly();
        this.m_securityLevel = securityLevelPair.getValueAsReadOnly();
        this.m_keywordsArray = keywordsPair.getValueAsNoteBytesArrayReadOnly();

  
        NoteBytes iconBytes = iconPair != null ? iconPair.getValue() : null;
        try {
            this.m_icon = iconBytes != null ? iconBytes.getAsImage() : null;
        } catch (IOException e) {
        }

        NoteBytes smallIconBytes = smallIconPair != null ? smallIconPair.getValue() : null;
        try {
            this.m_smallIcon = smallIconBytes != null ? smallIconBytes.getAsImage() : null;
        } catch (IOException e) {
    
        }
       

    }

    public NoteBytes getNodeId() {
        return m_nodeId;
    }

    public NoteBytesArray getKeywordsArray() {
        return m_keywordsArray;
    }

    public String getName() {
        return m_name.getAsString();
    }

    public Image getIcon() {
        return m_icon != null ? m_icon : new Image(NoteBytesImage.UNKNWON_IMG_URL);
    }

    public Image getSmallIcon() {
        return m_smallIcon != null ? m_smallIcon : new Image(NoteBytesImage.UNKNWON_IMG_URL);
    }

    public void setIcon(Image icon) {
        m_icon = icon;
    }

    public void setSmallIcon(Image smallIcon) {
        m_smallIcon = smallIcon;
    }



    public String getDescription() {
        return m_description.getAsString();
    }

    public NoteBytes getSecurityLevel() {
        return m_securityLevel;
    }

    public NoteBytesObject getNoteInformation() {
        NoteBytes iconBytes = NoteBytesImage.createNoteBytesImage(m_icon);
        NoteBytes smallIconBytes = NoteBytesImage.createNoteBytesImage(m_smallIcon);

        NoteBytesObject obj = new NoteBytesObject();
        obj.add(NODE_ID_KEY, m_nodeId);
        obj.add(NAME_KEY, m_name);
        if (iconBytes != null) {
            obj.add(ICON_KEY, iconBytes);
        }
        if (smallIconBytes != null) {
            obj.add(SMALL_ICON_KEY, smallIconBytes);
        }
        obj.add(DESCRIPTION_KEY, m_description != null ? m_description : null);
        obj.add(KEYWORDS_KEY, m_keywordsArray);
        obj.add(SECURITY_LEVEL_KEY, m_securityLevel);
        return obj;
    }
}