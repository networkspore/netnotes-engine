package io.netnotes.engine.messaging;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;

public class StandardMessages {
    
    NoteBytesObject getNoteDataObject(NoteBytes type, NoteBytes code, NoteBytes receiverId, NoteBytes senderId, NoteBytesObject data){
        NoteBytesObject obj = getNoteDataObject(type, code, System.currentTimeMillis(), receiverId, senderId);
        obj.add("data", data);
        return obj;
    }

    NoteBytesObject getNoteDataObject(NoteBytes type, NoteBytes code, NoteBytes receiverId, NoteBytes senderId, NoteBytes... data){
        NoteBytesObject obj = getNoteDataObject(type, code, System.currentTimeMillis(), receiverId, senderId);
        obj.add("data", data);
        return obj;
    }

    NoteBytesObject getNoteDataObject(NoteBytes type, NoteBytes code, NoteBytes receiverId, NoteBytes senderId, NoteBytesArray data){
        NoteBytesObject obj = getNoteDataObject(type, code, System.currentTimeMillis(), receiverId, senderId);
        obj.add("data", data);
        return obj;
    }

     NoteBytesObject getNoteDataObject(NoteBytes type, NoteBytes code, NoteBytes receiverId, NoteBytes senderId, String... data){
        NoteBytesObject obj = getNoteDataObject(type, code, System.currentTimeMillis(), receiverId, senderId);
        obj.add("data", data);
        return obj;
    }

    
    public static NoteBytesObject getNoteDataObject(NoteBytes type, NoteBytes code, long timeStamp, NoteBytes receiverId, NoteBytes senderId){
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add("timeStamp", timeStamp);
        nbo.add("receiverId", receiverId);
        nbo.add("senderId", senderId);
        nbo.add("code", code);
        return nbo;
    }

}
