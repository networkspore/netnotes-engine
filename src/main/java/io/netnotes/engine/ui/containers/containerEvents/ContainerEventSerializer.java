package io.netnotes.engine.ui.containers.containerEvents;

import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.noteBytes.NoteBytesObject;

public class ContainerEventSerializer {
    
	public static NoteBytesObject createCloseEvent() {
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(Keys.EVENT, EventBytes.EVENT_CONTAINER_CLOSED);
        return nbo;
	}

	
	public static NoteBytesObject createFocusEvent() {
		NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(Keys.EVENT, EventBytes.EVENT_CONTAINER_FOCUS_GAINED);
        return nbo;
	}

	
	public static NoteBytesObject createFocusLostEvent() {
		NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(Keys.EVENT, EventBytes.EVENT_CONTAINER_FOCUS_LOST);
        return nbo;
	}

	
	public static NoteBytesObject createHiddenEvent() {
		NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(Keys.EVENT, EventBytes.EVENT_CONTAINER_HIDDEN);
        return nbo;
	}

	
	public static NoteBytesObject createMaximizeEvent() {
		NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(Keys.EVENT, EventBytes.EVENT_CONTAINER_MAXIMIZE);
        return nbo;
	}

	
	public static NoteBytesObject createRestoredEvent() {
		NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(Keys.EVENT, EventBytes.EVENT_CONTAINER_RESTORE);
        return nbo;
	}

	
	public static NoteBytesObject createShownEvent() {
		NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(Keys.EVENT, EventBytes.EVENT_CONTAINER_SHOWN);
        return nbo;
	}
}
