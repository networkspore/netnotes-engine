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






    public static class ProtocolMesssages {
            
        
        public static final NoteBytesReadOnly UPDATED_ENCRYPTION    = new NoteBytesReadOnly("updatedEncryption");
        public static final NoteBytesReadOnly DISABLE_ENCRYPTION    = new NoteBytesReadOnly("disable_encryption");
        public static final NoteBytesReadOnly ENABLE_ENCRYPTION     = new NoteBytesReadOnly("enable_encryption");   // Initialization vector
        public static final NoteBytesReadOnly SUCCESS               = new NoteBytesReadOnly("success");
        public static final NoteBytesReadOnly INFO                  = new NoteBytesReadOnly("info");
      
        public static final NoteBytesReadOnly UNKNOWN               = new NoteBytesReadOnly("unknown");
        public static final NoteBytesReadOnly VERIFIED              = new NoteBytesReadOnly("verified");
        public static final NoteBytesReadOnly BLOCKED               = new NoteBytesReadOnly("blocked");
        public static final NoteBytesReadOnly INCOMPLETE            = new NoteBytesReadOnly("incomplete");
        public static final NoteBytesReadOnly BROADCAST_RESULT      = new NoteBytesReadOnly("broadcast_result"); 
        public static final NoteBytesReadOnly BROADCAST             = new NoteBytesReadOnly("broadcast");
        public static final NoteBytesReadOnly PROCESSING            = new NoteBytesReadOnly("processing");

        public static final NoteBytesReadOnly ERROR                 = new NoteBytesReadOnly("error");
        public static final NoteBytesReadOnly DISCONNECTED          = new NoteBytesReadOnly("disconnected");
        public static final NoteBytesReadOnly CONNECTED             = new NoteBytesReadOnly("connected");
        public static final NoteBytesReadOnly PONG                  = new NoteBytesReadOnly("pong");
        public static final NoteBytesReadOnly PING                  = new NoteBytesReadOnly("ping");
        public static final NoteBytesReadOnly ACCEPT                = new NoteBytesReadOnly("accept");
        public static final NoteBytesReadOnly HELLO                 = new NoteBytesReadOnly("hello");
        public static final NoteBytesReadOnly READY                 = new NoteBytesReadOnly("ready");
        public static final NoteBytesReadOnly SHUTDOWN              = new NoteBytesReadOnly("shutdown");
        public static final NoteBytesReadOnly FAILED                = new NoteBytesReadOnly("failed");
        public static final NoteBytesReadOnly CANCEL                = new NoteBytesReadOnly("cancel");
        public static final NoteBytesReadOnly PROGRESS              = new NoteBytesReadOnly("progress");

        public static final NoteBytesReadOnly STARTING              = new NoteBytesReadOnly( "starting");
        public static final NoteBytesReadOnly STARTED               = new NoteBytesReadOnly( "started");
        public static final NoteBytesReadOnly STOPPING              = new NoteBytesReadOnly( "stopping");
        public static final NoteBytesReadOnly STOPPED               = new NoteBytesReadOnly( "stopped");
        public static final NoteBytesReadOnly SHUTTING_DOWN         = new NoteBytesReadOnly( "shuttingDown");
        public static final NoteBytesReadOnly MINIMIZED             = new NoteBytesReadOnly( "minimized");
        public static final NoteBytesReadOnly UPDATED               = new NoteBytesReadOnly( "updated");
        public static final NoteBytesReadOnly UNAVAILABLE           = new NoteBytesReadOnly( "unavailable");
        public static final NoteBytesReadOnly AVAILABLE             = new NoteBytesReadOnly( "available");
        public static final NoteBytesReadOnly TIMED_OUT             = new NoteBytesReadOnly( "timedOut");
        public static final NoteBytesReadOnly DISABLED              = new NoteBytesReadOnly( "disabled");

        public static final NoteBytesReadOnly CAPABILITIES          = new NoteBytesReadOnly( "capabilities");
        public static final NoteBytesReadOnly REGISTER              = new NoteBytesReadOnly("register");
        public static final NoteBytesReadOnly GET_AVAILABLE         = new NoteBytesReadOnly("getAvailable");
    }

    public static class Keys {
        public static final NoteBytesReadOnly UUID_128          = new NoteBytesReadOnly("uuid128");
        public static final NoteBytesReadOnly UUID_256          = new NoteBytesReadOnly("uuid256");
        public static final NoteBytesReadOnly TIME_STAMP        = new NoteBytesReadOnly("timeStamp");
        public static final NoteBytesReadOnly VERSION_KEY       = new NoteBytesReadOnly("version");
        public static final NoteBytesReadOnly SOURCE_ID_KEY     = new NoteBytesReadOnly("srcId");
        public static final NoteBytesReadOnly TYPE_KEY          = new NoteBytesReadOnly("type");
        public static final NoteBytesReadOnly SEQUENCE_KEY      = new NoteBytesReadOnly("seqId");
        public static final NoteBytesReadOnly STATE_FLAGS_KEY   = new NoteBytesReadOnly("stFlags");
        public static final NoteBytesReadOnly PAYLOAD_KEY       = new NoteBytesReadOnly("payload");
        public static final NoteBytesReadOnly NAME_KEY          = new NoteBytesReadOnly("name");

        public static final NoteBytesReadOnly CMD_KEY           = new NoteBytesReadOnly("cmd");
        public final static NoteBytesReadOnly RESULT_KEY        = new NoteBytesReadOnly("result");
        public final static NoteBytesReadOnly SCOPE_KEY         = new NoteBytesReadOnly("scope");
        public final static NoteBytesReadOnly EXCEPTION_KEY     = new NoteBytesReadOnly("exception");
        public static final NoteBytesReadOnly ERROR_KEY         = new NoteBytesReadOnly("error"); 
        public static final NoteBytesReadOnly MSG_KEY           = new NoteBytesReadOnly("msg"); 
        public static final NoteBytesReadOnly STATUS_KEY        = new NoteBytesReadOnly("status");  
        public static final NoteBytesReadOnly WARNING_KEY       = new NoteBytesReadOnly("warning");
        public static final NoteBytesReadOnly LOCATION_ID       = new NoteBytesReadOnly("locationId");
        public static final NoteBytesReadOnly SENDER_ID_KEY     = new NoteBytesReadOnly("senderId");
        public static final NoteBytesReadOnly RECEIVER_ID_KEY   = new NoteBytesReadOnly("receiverId");
        public static final NoteBytesReadOnly CODE_KEY          = new NoteBytesReadOnly("code");
        public static final NoteBytesReadOnly PID_KEY           = new NoteBytesReadOnly("pid");
        public static final NoteBytesReadOnly DEVICE_KEY        = new NoteBytesReadOnly("device");
        public static final NoteBytesReadOnly DEVICES_KEY       = new NoteBytesReadOnly("devices");

        public final static NoteBytesReadOnly TOTAL_KEY = new NoteBytesReadOnly("total");
        public final static NoteBytesReadOnly COMPLETED_KEY = new NoteBytesReadOnly("completed");

        //encryption
        public static final NoteBytesReadOnly ENCRYPTION_KEY = new NoteBytesReadOnly("encryption");  // Encrypted flag
        public static final NoteBytesReadOnly CIPHER_KEY = new NoteBytesReadOnly("cipher");  // Ciphertext
        public static final NoteBytesReadOnly PHASE_KEY = new NoteBytesReadOnly("phase");  // Phase
        public static final NoteBytesReadOnly PUB_KEY = new NoteBytesReadOnly("pubKey");  // Public key
        public static final NoteBytesReadOnly AES_IV_KEY = new NoteBytesReadOnly("aesIV");   // Initialization vector


        
    }

    /* =========================
     * Error constants
     * ========================= */
    public static class Error {
        // General
        public static final String UNKNOWN = "Unknown Error";
        public static final String TIMEOUT = "Timeout";
        public static final String INTERRUPTED = "Interrupted";
        public static final String IO = "IO Error";
        public static final String CANCELED = "Canceled";
        public static final String NOT_STARTED = "Not Started";
        public static final String NOT_READY = "Not Ready";
        public static final String NOT_AVAILABLE = "Not Available";
        public static final String NOT_STOPPED = "Not Stopped";
        public static final String NOT_SHUTDOWN = "Not Shutdown";
        public static final String NOT_SHUTTING_DOWN = "Not Shutting Down";
        public static final String NOT_UPDATED = "Not Updated";

        //IO
        public static final String IO_DELETION = "Disk_error_onDelete";

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
        nbo.add(Keys.CMD_KEY, subject);
        nbo.add(Keys.TIME_STAMP, System.currentTimeMillis());
        return nbo;
    }

 

    public static NoteBytesObject getCmdObject(String cmd, NoteBytes locationId){        
        NoteBytesObject note = NoteMessaging.getCmdObject(cmd);
        note.add(Keys.LOCATION_ID, locationId);
        return note;
    }

    public static NoteBytesObject getCmdObject(String cmd, NoteBytes networkId, NoteBytes locationId){        
        NoteBytesObject note = NoteMessaging.getCmdObject(cmd);
        note.add("locationId", locationId);
        note.add("networkId", networkId);
        return note;
    }


 

    public static NoteBytes getNameFromNetworkObject(NoteBytesObject bytesObject){
      
        NoteBytesPair nameElement = bytesObject != null ? bytesObject.get(Keys.NAME_KEY) : null;
        
        return nameElement != null ? nameElement.getValue() : new NoteBytes( ProtocolMesssages.UNKNOWN);
    
    }

    public static boolean isNameInBytesArray(NoteBytesArray nbArray, String name){
        NoteBytes nameNoteBytes = new NoteBytes(name);
        NoteBytes[] array = nbArray.getAsArray();

        for(NoteBytes noteBytes : array){
  
            NoteBytesPair pair = noteBytes.getAsNoteBytesObject().get(Keys.NAME_KEY);
            
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
        nbo.add(Keys.TIME_STAMP, timeStamp);
        nbo.add(Keys.RECEIVER_ID_KEY, receiverId);
        nbo.add(Keys.SENDER_ID_KEY, senderId);
        nbo.add(Keys.CODE_KEY, code);
        return nbo;
    }
    
}
