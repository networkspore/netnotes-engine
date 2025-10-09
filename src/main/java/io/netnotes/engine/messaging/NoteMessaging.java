package io.netnotes.engine.messaging;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

public class NoteMessaging {

    public static final byte KEY_AND_VALUE = 0x12;
    public static final byte VALUE_AND_KEY = 0x21;
    public static final byte KEY_NO_VALUE = 0x10;
    public static final byte VALUE_NO_KEY = 0x00;

    public static final long POLLING_TIME = 7000;

    public static class Command{
        public static final String CMD = "cmd";
        public static final String CMD_NOT_PRESENT = "cmd_not_present";
        public static final String HELLO   = "HELLO";   // identity bootstrap
        public static final String ACCEPT  = "ACCEPT";  // trust ack
        public static final String PING    = "PING";
        public static final String PONG    = "PONG";
    }
    /* =========================
     * General constants
     * ========================= */
    public static class General {
        public static final String DEFAULT = "Default";
        public static final String SUCCESS = "Success";
        public static final String ERROR = "Error";
        public static final String INFO = "Info";
        public static final String WARNING = "Warning";
        public static final String FAILED = "Failed";
        public static final String CANCEL = "Cancel";
        public static final String READY = "Ready";
        public static final String VERIFIED = "Verified";
        public static final String BLOCKED = "Blocked";
        public static final String INCOMPLETE = "Incomplete";
        public static final String BROADCAST_RESULT = "Broadcast"; 
        public static final String BROADCAST = "Broadcast Result";
        public static final String STATUS = "Status";
        public static final String PROCESSING = "Processing";
    }

    public static class Headings {
        public static final NoteBytesReadOnly UUID_128 = new NoteBytesReadOnly("uuid_128");
        public static final NoteBytesReadOnly UUID_256 = new NoteBytesReadOnly("uuid_256");
        public static final NoteBytesReadOnly TIME_STAMP = new NoteBytesReadOnly("timeStamp");
        public static final NoteBytesReadOnly VERSION_KEY = new NoteBytesReadOnly("version");
    }


    /* =========================
     * Status lifecycle constants
     * ========================= */
    public static class Status {
        public static final String STARTING = "Status_Starting";
        public static final String STARTED = "Status_Started";
        public static final String STOPPING = "Status_Stopping";
        public static final String STOPPED = "Status_Stopped";
        public static final String SHUTTING_DOWN = "StatusShutting Down";
        public static final String SHUTDOWN = "Status_Shutdown";

        public static final String MINIMIZED = "Status_Minimized";
        public static final String UPDATED = "Status_Updated";
        public static final String UNAVAILABLE = "Status_Unavailable";
        public static final String AVAILABLE = "Status_Available";
        public static final String READY = "Status_Ready";
        public static final String TIMED_OUT = "Status_Timed Out";
        public static final String DISABLED = "Status_Disabled";
        public static final String UNKNOWN = "Status_Unknown";
    }

    /* =========================
     * Event / List update constants
     * ========================= */
    public static class Event {
        public static final String MSG_SEND_NOTE = "Event_Send_note";

        public static final String LIST_CHANGED = "Event_List_changed";
        public static final String LIST_CHECKED = "Event_List_checked";
        public static final String LIST_UPDATED = "Event_List_updated";
        public static final String LIST_ITEM_ADDED = "Event_List_item_added";
        public static final String LIST_ITEM_REMOVED = "Event_List_item_removed";
        public static final String LIST_DEFAULT_CHANGED = "Event_Default_item_changed";
    }

    /* =========================
     * Error constants
     * ========================= */
    public static class Error {
        // General
        public static final String UNKNOWN = "Error_Unknown";
        public static final String TIMEOUT = "Error_Timeout";
        public static final String INTERRUPTED = "Error_Interrupted";
        public static final String IO = "Error_IO failed";
        public static final String CANCELED = "Error_Canceled";
        public static final String CLOSING = "Error_Closing";

        //IO
        public static final String IO_DELETION = "Disk_error_onDelete";

        // Availability / lifecycle
        public static final String NOT_STARTED = "Error_Not_Started";
        public static final String NOT_READY = "Error_Not_Ready";
        public static final String NOT_AVAILABLE = "Error_Not_Available";
        public static final String NOT_STOPPED = "Error_Not_Stopped";
        public static final String NOT_SHUTDOWN = "Error_Not_Shutdown";
        public static final String NOT_SHUTTING_DOWN = "Error_Not_Shutting_Down";
        public static final String NOT_UPDATED = "Error_Not_Updated";

        // Validation
        public static final String INVALID = "Error_Invalid";
        public static final String NOT_EXISTS = "Error_Not_Exist";
        public static final String NOT_FOUND = "Error_Not_Found";
        public static final String OUT_OF_RANGE = "Error_Out_Of_Range";

        // Permissions / security
        public static final String PERMISSION_DENIED = "Error_Permission_Denied";
        public static final String UNAUTHORIZED = "Error_Unauthorized";
        public static final String FORBIDDEN = "Error_Forbidden";

        // Protocol / control
        public static final String CONTROL_NOT_AVAILABLE = "Error_Control_Not_Available";
        public static final String MALFORMED_REQUEST = "Error_Malformed_Request";
        public static final String UNSUPPORTED_OPERATION = "Error_Unsupported_Operation";
    
        public static final String AUTH_REQUIRED = "Auth_Required";
        public static final String BAD_SIGNATURE = "Bad_Signature";
        public static final String KEY_UNKNOWN   = "Key_Unknown";
        public static final String REPLAY        = "Replay_Detected";
        public static final String STALE_MESSAGE = "Stale_Message";
        public static final String DECRYPT_FAIL  = "Decrypt_Failed";
        public static final String NOT_PERMITTED = "Not_Permitted";
    }


    public static class Logging{
        public static final String FULL = "Full";
        public static final String CRITICAL = "Critical";
        public static final String NONE = "None";
    }


    
    /* =========================
     * Search / sorting
     * ========================= */
    public static class Search {
        public static final String SORT_ASC = "asc";
        public static final String SORT_DSC = "dsc";
    }


    public static String checkAsc(String value){
       return (value != null && value.toLowerCase().equals(Search.SORT_ASC.toLowerCase())) ? Search.SORT_ASC : Search.SORT_DSC;
    }

    public static String getNoteBytesObjectName(String defaultString, NoteBytesObject json){
        String name = json == null ? defaultString : NoteMessaging.getNoteBytesObjectName(json);
        return name != null ? name : defaultString;
        
    }

    public static String getNoteBytesObjectName(NoteBytesObject obj){
        if(obj != null){
            NoteBytesPair nameElement = obj.get("name");
            if(nameElement != null && nameElement.getValue() != null && nameElement.getValue().byteLength() > 0){
                return nameElement.getValue().getAsString();
            }
        }
        return null;
    }

    public static NoteBytes geNoteBytesObjectId(NoteBytesObject obj){
        if(obj != null){
            NoteBytesPair idElement = obj.get("id");
            if(idElement != null && idElement.getValue() != null && idElement.getValue().byteLength() > 0){
                return idElement.getValue();
            }
        }
        return null;
    }

    public static NoteBytes getValueByMemberName(NoteBytesObject obj, String memberName){
        if(obj != null){
            NoteBytesPair idElement = obj.get(memberName);
            if(idElement != null && idElement.getValue() != null){
                return idElement.getValue();
            }
        }
        return null;
    }

    public static NoteBytesObject getNoteBytesObjectByName(String memberName, NoteBytes id, NoteBytesArray noteBytesArray){
        if(noteBytesArray != null){
            NoteBytes[] array = noteBytesArray.getAsArray();
            for(NoteBytes noteBytes : array){
                NoteBytesObject nbo = noteBytes.getAsNoteBytesObject();
                NoteBytes nodeObjectId = NoteMessaging.getValueByMemberName(nbo, "networkId");
                if(nodeObjectId != null && nodeObjectId.equals(id)){
                    return nbo;
                }
            }
        }
        return null;
    }



    public static NoteBytesObject getNoteBytesObjectById(NoteBytes id, NoteBytesArray nbArray){
        if(nbArray != null){
            NoteBytes[] noteBytesarray = nbArray.getAsArray();
            for(NoteBytes noteBytes : noteBytesarray){
                NoteBytesObject nbo = noteBytes.getAsNoteBytesObject();
                NoteBytes nodeObjectId = NoteMessaging.geNoteBytesObjectId(nbo);
                if(nodeObjectId != null && nodeObjectId.equals(id)){
                    return nbo;
                }
            }
        }
        return null;
    }

     
    public static NoteBytesObject getNoteBytesObject(String name, String property){
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(name, property);
        return nbo;
    }


    public static NoteBytesObject getNoteBytesObject(String name, int property){
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(name, property);
        return nbo;
    }

    public static NoteBytesObject getCmdObject(String subject) {
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(Command.CMD, subject);
        nbo.add("timeStamp", System.currentTimeMillis());
        return nbo;
    }

 

    public static NoteBytesObject getCmdObject(String cmd, NoteBytes locationId){        
        NoteBytesObject note = NoteMessaging.getCmdObject(cmd);
        note.add("locationId", locationId);
        return note;
    }

    public static NoteBytesObject getCmdObject(String cmd, NoteBytes networkId, NoteBytes locationId){        
        NoteBytesObject note = NoteMessaging.getCmdObject(cmd);
        note.add("locationId", locationId);
        note.add("networkId", networkId);
        return note;
    }


 

    public static NoteBytes getNameFromNetworkObject(NoteBytesObject bytesObject){
      
        NoteBytesPair nameElement = bytesObject != null ? bytesObject.get("name") : null;
        
        return nameElement != null ? nameElement.getValue() : new NoteBytes( "(Unknown)");
    
    }

    public static boolean isNameInBytesArray(NoteBytesArray nbArray, String name){
        NoteBytes nameNoteBytes = new NoteBytes(name);
        NoteBytes[] array = nbArray.getAsArray();

        for(NoteBytes noteBytes : array){
  
            NoteBytesPair pair = noteBytes.getAsNoteBytesObject().get("name");
            
            if(pair != null && pair.getValue() != null){
                if(nameNoteBytes.equals(pair.getValue())){
                    return true;
                }
            }
        }
        return false;
    }



    //Standard messages

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
