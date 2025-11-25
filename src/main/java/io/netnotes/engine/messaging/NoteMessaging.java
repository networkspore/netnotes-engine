package io.netnotes.engine.messaging;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArray;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

public class NoteMessaging {

    public static final byte KEY_AND_VALUE = 0x12;
    public static final byte VALUE_AND_KEY = 0x21;
    public static final byte KEY_NO_VALUE = 0x10;
    public static final byte VALUE_NO_KEY = 0x00;

    public static final long POLLING_TIME = 7000;






    public static class ProtocolMesssages {

        // Lifecycle
        public static final NoteBytesReadOnly HELLO         = new NoteBytesReadOnly("hello");
        public static final NoteBytesReadOnly READY         = new NoteBytesReadOnly("ready");
        public static final NoteBytesReadOnly ACCEPT        = new NoteBytesReadOnly("accept");
        public static final NoteBytesReadOnly PING          = new NoteBytesReadOnly("ping");
        public static final NoteBytesReadOnly PONG          = new NoteBytesReadOnly("pong");
        public static final NoteBytesReadOnly SHUTDOWN      = new NoteBytesReadOnly("shutdown");
        public static final NoteBytesReadOnly DISCONNECTED  = new NoteBytesReadOnly("disconnected");

        // Discovery
        public static final NoteBytesReadOnly REQUEST_DISCOVERY = new NoteBytesReadOnly("request_discovery");
        public static final NoteBytesReadOnly ITEM_LIST         = new NoteBytesReadOnly("item_list");
        public static final NoteBytesReadOnly GET_ITEM_INFO     = new NoteBytesReadOnly("get_item_info");
        public static final NoteBytesReadOnly ITEM_INFO         = new NoteBytesReadOnly("item_info");
        public static final NoteBytesReadOnly GET_CAPABILITIES  = new NoteBytesReadOnly("get_capabilities");
       

         // Claim
        public static final NoteBytesReadOnly CLAIM_ITEM            = new NoteBytesReadOnly("claim_item");
        public static final NoteBytesReadOnly ITEM_CLAIMED          = new NoteBytesReadOnly("item_claimed");
        public static final NoteBytesReadOnly RELEASE_ITEM          = new NoteBytesReadOnly("release_item");
        public static final NoteBytesReadOnly ITEM_RELEASED         = new NoteBytesReadOnly("item_released");
        public static final NoteBytesReadOnly ITEM_TYPE             = new NoteBytesReadOnly("item_type");

        // Control
        public static final NoteBytesReadOnly START_STREAM          = new NoteBytesReadOnly("start_stream");
        public static final NoteBytesReadOnly STOP_STREAM           = new NoteBytesReadOnly("stop_stream");
        public static final NoteBytesReadOnly PAUSE_ITEM            = new NoteBytesReadOnly("pause_item");
        public static final NoteBytesReadOnly RESUME_ITEM           = new NoteBytesReadOnly("resume_item");
        public static final NoteBytesReadOnly RESUME                = new NoteBytesReadOnly("resume");
        public static final NoteBytesReadOnly RESTART               = new NoteBytesReadOnly("restart");
        public static final NoteBytesReadOnly UPDATE_CONFIG         = new NoteBytesReadOnly("update_config");

        public static final NoteBytesReadOnly ITEM_CAPABILITIES     = new NoteBytesReadOnly( "item_capabilities");
        public static final NoteBytesReadOnly REGISTER_ITEM         = new NoteBytesReadOnly("register_item");
        

        // Configuration
        public static final NoteBytesReadOnly SET_MODE              = new NoteBytesReadOnly("set_mode");
        public static final NoteBytesReadOnly SET_FILTER            = new NoteBytesReadOnly("set_filter");
        public static final NoteBytesReadOnly ENABLE_FEATURE        = new NoteBytesReadOnly("enable_feature");
        public static final NoteBytesReadOnly DISABLE_FEATURE       = new NoteBytesReadOnly("disable_feature");
        
        

        public static final NoteBytesReadOnly DISCONNECT            = new NoteBytesReadOnly("disconnect");
        
        // Encryption Lifecycle
        public static final NoteBytesReadOnly ENABLE_ENCRYPTION     = new NoteBytesReadOnly("enable_encryption");
        public static final NoteBytesReadOnly DISABLE_ENCRYPTION    = new NoteBytesReadOnly("disable_encryption");
        public static final NoteBytesReadOnly ENCRYPTION_READY      = new NoteBytesReadOnly("encryption_ready");
        public static final NoteBytesReadOnly UPDATED_ENCRYPTION    = new NoteBytesReadOnly("updated_encryption");

        // Status Messages
        public static final NoteBytesReadOnly ERROR                 = new NoteBytesReadOnly("error");
        public static final NoteBytesReadOnly SUCCESS               = new NoteBytesReadOnly("success");
        public static final NoteBytesReadOnly FAILED                = new NoteBytesReadOnly("failed");
        public static final NoteBytesReadOnly PROGRESS              = new NoteBytesReadOnly("progress");
        public static final NoteBytesReadOnly INFO                  = new NoteBytesReadOnly("info");
        public static final NoteBytesReadOnly STATUS                = new NoteBytesReadOnly("status");

        // State Changes
        public static final NoteBytesReadOnly STARTED               = new NoteBytesReadOnly("started");
        public static final NoteBytesReadOnly STOPPED               = new NoteBytesReadOnly("stopped");
        public static final NoteBytesReadOnly UPDATED               = new NoteBytesReadOnly("updated");
        public static final NoteBytesReadOnly AVAILABLE_MSG         = new NoteBytesReadOnly("available");
        public static final NoteBytesReadOnly UNAVAILABLE           = new NoteBytesReadOnly("unavailable");
        public static final NoteBytesReadOnly TIMED_OUT             = new NoteBytesReadOnly("timed_out");
        public static final NoteBytesReadOnly STARTING              = new NoteBytesReadOnly( "starting");
        public static final NoteBytesReadOnly STOPPING              = new NoteBytesReadOnly( "stopping");
        public static final NoteBytesReadOnly SHUTTING_DOWN         = new NoteBytesReadOnly( "shuttingDown");
        public static final NoteBytesReadOnly MINIMIZED             = new NoteBytesReadOnly( "minimized");
        public static final NoteBytesReadOnly AVAILABLE             = new NoteBytesReadOnly( "available");
        public static final NoteBytesReadOnly DISABLED              = new NoteBytesReadOnly( "disabled");
      
        public static final NoteBytesReadOnly UNKNOWN               = new NoteBytesReadOnly("unknown");
        public static final NoteBytesReadOnly VERIFIED              = new NoteBytesReadOnly("verified");
        public static final NoteBytesReadOnly BLOCKED               = new NoteBytesReadOnly("blocked");
        public static final NoteBytesReadOnly INCOMPLETE            = new NoteBytesReadOnly("incomplete");
        public static final NoteBytesReadOnly BROADCAST_RESULT      = new NoteBytesReadOnly("broadcast_result"); 
        public static final NoteBytesReadOnly BROADCAST             = new NoteBytesReadOnly("broadcast");
        public static final NoteBytesReadOnly PROCESSING            = new NoteBytesReadOnly("processing");

        public static final NoteBytesReadOnly CONNECTED             = new NoteBytesReadOnly("connected");
        public static final NoteBytesReadOnly CANCEL                = new NoteBytesReadOnly("cancel");

        


    }

    @FunctionalInterface
    public interface MessageExecutor {
        void execute(NoteBytesMap message);
    }

    public interface RoutedMessageExecutor {
        CompletableFuture<Void> execute(NoteBytesMap message, RoutedPacket packet);
    }

    public static class Keys {
        public static final NoteBytesReadOnly ID            = new NoteBytesReadOnly("id");
        public static final NoteBytesReadOnly UUID_128      = new NoteBytesReadOnly("uuid_128");
        // Identity & Routing
        public static final NoteBytesReadOnly TYPE          = new NoteBytesReadOnly("type");
        public static final NoteBytesReadOnly SEQUENCE      = new NoteBytesReadOnly("seq_id");
        public static final NoteBytesReadOnly SOURCE_ID     = new NoteBytesReadOnly("source_id");
        public static final NoteBytesReadOnly DEVICE_ID     = new NoteBytesReadOnly("device_id");
        public static final NoteBytesReadOnly SESSION_ID    = new NoteBytesReadOnly("session_id");
        public static final NoteBytesReadOnly PID           = new NoteBytesReadOnly("pid");
        public static final NoteBytesReadOnly RECEIVER_ID   = new NoteBytesReadOnly("receiver_id");
        public static final NoteBytesReadOnly SENDER_ID     = new NoteBytesReadOnly("sender_id");
        public static final NoteBytesReadOnly CODE_KEY      = new NoteBytesReadOnly("code");
        public static final NoteBytesReadOnly CURRENT_MODE  = new NoteBytesReadOnly("current_mode");
        public static final NoteBytesReadOnly STATE_TYPE    = new NoteBytesReadOnly("state_type");
        public static final NoteBytesReadOnly ALIVE         = new NoteBytesReadOnly("alive");
        public static final NoteBytesReadOnly SERVICE       = new NoteBytesReadOnly("service");
        // Metadata
        public static final NoteBytesReadOnly NAME          = new NoteBytesReadOnly("name");
        public static final NoteBytesReadOnly TIMESTAMP     = new NoteBytesReadOnly("time_stamp");
        public static final NoteBytesReadOnly VERSION       = new NoteBytesReadOnly("version");
        public static final NoteBytesReadOnly TITLE         = new NoteBytesReadOnly("title");
        public static final NoteBytesReadOnly CONTENT       = new NoteBytesReadOnly("content");
        public static final NoteBytesReadOnly ALLOW_COPY    = new NoteBytesReadOnly("allow_copy");
        // Payload
        public static final NoteBytesReadOnly PAYLOAD       = new NoteBytesReadOnly("payload");
        public static final NoteBytesReadOnly STATE_FLAGS   = new NoteBytesReadOnly("state_flags");
        public static final NoteBytesReadOnly CMD           = new NoteBytesReadOnly("cmd");
        public static final NoteBytesReadOnly STRING_LIST   = new NoteBytesReadOnly("string_list");

        // Status & Results
        public static final NoteBytesReadOnly INFO          = new NoteBytesReadOnly("info");
        public static final NoteBytesReadOnly STATUS        = new NoteBytesReadOnly("status");
        public static final NoteBytesReadOnly ERROR_CODE    = new NoteBytesReadOnly("error");
        public static final NoteBytesReadOnly MSG           = new NoteBytesReadOnly("msg");
        public static final NoteBytesReadOnly RESULT        = new NoteBytesReadOnly("result");
        public static final NoteBytesReadOnly WARNING       = new NoteBytesReadOnly("warning");
        public static final NoteBytesReadOnly EXCEPTION     = new NoteBytesReadOnly("exception");
        public static final NoteBytesReadOnly AVAILABLE     = new NoteBytesReadOnly("available");
        public static final NoteBytesReadOnly SELECTED_INDEX = new NoteBytesReadOnly("selected_index");

        // Items (Generic resource term)
        public static final NoteBytesReadOnly ITEM          = new NoteBytesReadOnly("item");
        public static final NoteBytesReadOnly ITEMS         = new NoteBytesReadOnly("items");
        
        public static final NoteBytesReadOnly ITEM_TYPE             = new NoteBytesReadOnly("item_type");
        public static final NoteBytesReadOnly ITEM_COUNT            = new NoteBytesReadOnly("item_count");
        public static final NoteBytesReadOnly ITEM_PATH             = new NoteBytesReadOnly("item_path");
        public static final NoteBytesReadOnly ITEM_CLASS            = new NoteBytesReadOnly("item_class");
        public static final NoteBytesReadOnly ITEM_SUBCLASS         = new NoteBytesReadOnly("item_subclass");
        public static final NoteBytesReadOnly ITEM_PROTOCOL         = new NoteBytesReadOnly("item_protocol");
        public static final NoteBytesReadOnly ITEM_ADDRESS          = new NoteBytesReadOnly("item_address");
        public static final NoteBytesReadOnly ITEM_NAME             = new NoteBytesReadOnly("item_name");
        public static final NoteBytesReadOnly ITEM_DESCRIPTION      = new NoteBytesReadOnly("item_description");
        public static final NoteBytesReadOnly ITEM_PROTECTED        = new NoteBytesReadOnly("item_protected");

        public static final NoteBytesReadOnly MENU_ITEMS            = new NoteBytesReadOnly("menu_items");
        public static final NoteBytesReadOnly HAS_BACK              = new NoteBytesReadOnly("has_back");
        public static final NoteBytesReadOnly PATH                  = new NoteBytesReadOnly("path");

        // Password
        public static final NoteBytesReadOnly PROMPT                = new NoteBytesReadOnly("prompt");
        public static final NoteBytesReadOnly ATTEMPTS_REMAINING    = new NoteBytesReadOnly("attempts_remaining");
        public static final NoteBytesReadOnly ERROR_MESSAGE         = new NoteBytesReadOnly("error_message");
        public static final NoteBytesReadOnly PASSWORD              = new NoteBytesReadOnly("password");
        
        // Progress
        public static final NoteBytesReadOnly PROGRESS_PERCENT      = new NoteBytesReadOnly("progress_percent");
        public static final NoteBytesReadOnly PROGRESS_MESSAGE      = new NoteBytesReadOnly("progress_message");

       
        public static final NoteBytesReadOnly VENDOR_ID         = new NoteBytesReadOnly("vendor_id");
        public static final NoteBytesReadOnly PRODUCT_ID        = new NoteBytesReadOnly("product_id");
        public static final NoteBytesReadOnly BUS_NUMBER        = new NoteBytesReadOnly("bus_number");
        public static final NoteBytesReadOnly MANUFACTURER      = new NoteBytesReadOnly("manufacturer");
        public static final NoteBytesReadOnly PRODUCT           = new NoteBytesReadOnly("product");
        public static final NoteBytesReadOnly SERIAL_NUMBER     = new NoteBytesReadOnly("serial_number");
        public static final NoteBytesReadOnly KERNEL_DRIVER_ATTACHED    = new NoteBytesReadOnly("kernel_driver_attached");
        public static final NoteBytesReadOnly DEVICE_CLASS      = new NoteBytesReadOnly("device_class");
        public static final NoteBytesReadOnly DEVICE_SUBCLASS   = new NoteBytesReadOnly("device_subclass");
        public static final NoteBytesReadOnly DEVICE_PROTOCOL   = new NoteBytesReadOnly("device_protocol");
        public static final NoteBytesReadOnly DEVICE_ADDRESS    = new NoteBytesReadOnly("device_address");
        
       
        // Capabilities
        public static final NoteBytesReadOnly MODE                      = new NoteBytesReadOnly("mode");
        public static final NoteBytesReadOnly AVAILABLE_CAPABILITIES    = new NoteBytesReadOnly("available_capabilities");        public static final NoteBytesReadOnly CLAIMED_ITEMS             = new NoteBytesReadOnly("claimedItems");
        public static final NoteBytesReadOnly ENABLED_CAPABILITIES      = new NoteBytesReadOnly("enabled_capabilities");
        public static final NoteBytesReadOnly CAPABILITY_NAMES          = new NoteBytesReadOnly("capability_names");
        public static final NoteBytesReadOnly AVAILABLE_MODES           = new NoteBytesReadOnly("available_modes");

    
       // public static final NoteBytesReadOnly CAPABILITIES      = new NoteBytesReadOnly("capabilities");
        
        public static final NoteBytesReadOnly DEFAULT_MODE      = new NoteBytesReadOnly("default_mode");
        public static final NoteBytesReadOnly CONSTRAINTS       = new NoteBytesReadOnly("constraints");
        public static final NoteBytesReadOnly CHILDREN          = new NoteBytesReadOnly("children");
        
        // Encryption
        public static final NoteBytesReadOnly ENCRYPTION    = new NoteBytesReadOnly("encryption");
        public static final NoteBytesReadOnly CIPHER        = new NoteBytesReadOnly("cipher");
        public static final NoteBytesReadOnly PHASE         = new NoteBytesReadOnly("phase");
        public static final NoteBytesReadOnly PUBLIC_KEY    = new NoteBytesReadOnly("pub_key");
        public static final NoteBytesReadOnly AES_IV        = new NoteBytesReadOnly("aes_iv");
        
        // Flow Control
        public static final NoteBytesReadOnly PROCESSED_COUNT = new NoteBytesReadOnly("processed_count");
        public static final NoteBytesReadOnly TOTAL           = new NoteBytesReadOnly("total");
        public static final NoteBytesReadOnly COMPLETED       = new NoteBytesReadOnly("completed");

        public static final NoteBytesReadOnly SCOPE         = new NoteBytesReadOnly("scope");
        public static final NoteBytesReadOnly STATE         = new NoteBytesReadOnly("state");
    }

     public static class ItemTypes {
        public static final NoteBytesReadOnly KEYBOARD  = new NoteBytesReadOnly("keyboard");
        public static final NoteBytesReadOnly MOUSE     = new NoteBytesReadOnly("mouse");
        public static final NoteBytesReadOnly GAMEPAD   = new NoteBytesReadOnly("gamepad");
        public static final NoteBytesReadOnly TOUCHPAD  = new NoteBytesReadOnly("touchpad");
        public static final NoteBytesReadOnly UNKNOWN   = new NoteBytesReadOnly("unknown");
        
        // Future use
        public static final NoteBytesReadOnly WINDOW    = new NoteBytesReadOnly("window");
        public static final NoteBytesReadOnly SCENE     = new NoteBytesReadOnly("scene");
        public static final NoteBytesReadOnly STAGE     = new NoteBytesReadOnly("stage");
        public static final NoteBytesReadOnly PEER      = new NoteBytesReadOnly("peer");
        public static final NoteBytesReadOnly ENDPOINT  = new NoteBytesReadOnly("endpoint");

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

    public static class Modes {
        public static final NoteBytesReadOnly RAW           = new NoteBytesReadOnly("raw");
        public static final NoteBytesReadOnly PARSED        = new NoteBytesReadOnly("parsed");
        public static final NoteBytesReadOnly PASSTHROUGH   = new NoteBytesReadOnly("passthrough");
        public static final NoteBytesReadOnly FILTERED      = new NoteBytesReadOnly("filtered");
    }


    public static class Logging{
        public static final String FULL = "Full";
        public static final String CRITICAL = "Critical";
        public static final String NONE = "None";
    }


    // Error Codes
    public static class ErrorCodes {
        // General errors (0-9)
        public static final int UNKNOWN             = 0;
        public static final int PARSE_ERROR         = 1;
        public static final int INVALID_MESSAGE     = 2;
        public static final int TIMEOUT             = 3;
        public static final int INTERRUPTED         = 4;
        
        // Resource errors (10-19)
        public static final int ITEM_NOT_FOUND      = 10;
        public static final int ITEM_NOT_AVAILABLE  = 11;
        public static final int MODE_INCOMPATIBLE   = 12;
        public static final int MODE_NOT_SUPPORTED  = 13;
        public static final int FEATURE_NOT_SUPPORTED = 14;
        public static final int CLAIM_FAILED        = 15;
        
        // Permission errors (20-29)
        public static final int PERMISSION_DENIED   = 20;
        public static final int UNAUTHORIZED        = 21;
        public static final int PID_MISMATCH        = 22;
        public static final int ALREADY_CLAIMED     = 23;
        
        // State errors (30-39)
        public static final int INVALID_STATE       = 30;
        public static final int NOT_CLAIMED         = 31;
        public static final int NOT_STREAMING       = 32;
        public static final int ALREADY_STREAMING   = 33;
        
        // Protocol errors (40-49)
        public static final int PROTOCOL_ERROR      = 40;
        public static final int VERSION_MISMATCH    = 41;
        public static final int HANDSHAKE_FAILED    = 42;
        
        // Encryption errors (50-59)
        public static final int ENCRYPTION_FAILED   = 50;
        public static final int DECRYPTION_FAILED   = 51;
        public static final int KEY_EXCHANGE_FAILED = 52;
        
        private static final Map<Integer, String> ERROR_MESSAGES = new HashMap<>();
        
        static {
            ERROR_MESSAGES.put(UNKNOWN, "Unknown error");
            ERROR_MESSAGES.put(PARSE_ERROR, "Parse error");
            ERROR_MESSAGES.put(INVALID_MESSAGE, "Invalid message");
            ERROR_MESSAGES.put(ITEM_NOT_FOUND, "Item not found");
            ERROR_MESSAGES.put(ITEM_NOT_AVAILABLE, "Item not available");
            ERROR_MESSAGES.put(MODE_INCOMPATIBLE, "Mode not compatible");
            ERROR_MESSAGES.put(MODE_NOT_SUPPORTED, "Mode not supported");
            ERROR_MESSAGES.put(PERMISSION_DENIED, "Permission denied");
            ERROR_MESSAGES.put(UNAUTHORIZED, "Unauthorized");
            ERROR_MESSAGES.put(PID_MISMATCH, "PID mismatch");
            ERROR_MESSAGES.put(CLAIM_FAILED, "Failed to claim item");
        }
        
        public static String getMessage(int errorCode) {
            return ERROR_MESSAGES.getOrDefault(errorCode, "Unknown error");
        }
    }
    
    // Status Values
    public static class Status {
        public static final NoteBytesReadOnly OK            = new NoteBytesReadOnly("ok");
        public static final NoteBytesReadOnly READY         = new NoteBytesReadOnly("ready");
        public static final NoteBytesReadOnly PENDING       = new NoteBytesReadOnly("pending");
        public static final NoteBytesReadOnly PROCESSING    = new NoteBytesReadOnly("processing");
        public static final NoteBytesReadOnly COMPLETE      = new NoteBytesReadOnly("complete");
        public static final NoteBytesReadOnly FAILED        = new NoteBytesReadOnly("failed");
        public static final NoteBytesReadOnly CANCELLED     = new NoteBytesReadOnly("cancelled");
        public static final NoteBytesReadOnly ACTIVE        = new NoteBytesReadOnly("active");
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
        nbo.add(Keys.CMD, subject);
        nbo.add(Keys.TIMESTAMP, System.currentTimeMillis());
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
      
        NoteBytesPair nameElement = bytesObject != null ? bytesObject.get(Keys.NAME) : null;
        
        return nameElement != null ? nameElement.getValue() : new NoteBytes( ProtocolMesssages.UNKNOWN);
    
    }

    public static boolean isNameInBytesArray(NoteBytesArray nbArray, String name){
        NoteBytes nameNoteBytes = new NoteBytes(name);
        NoteBytes[] array = nbArray.getAsArray();

        for(NoteBytes noteBytes : array){
  
            NoteBytesPair pair = noteBytes.getAsNoteBytesObject().get(Keys.NAME);
            
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
        obj.add("data", new NoteBytesArray(data));
        return obj;
    }

    NoteBytesObject getNoteDataObject(NoteBytes type, NoteBytes code, NoteBytes receiverId, NoteBytes senderId, NoteBytesArray data){
        NoteBytesObject obj = getNoteDataObject(type, code, System.currentTimeMillis(), receiverId, senderId);
        obj.add("data", data);
        return obj;
    }

     NoteBytesObject getNoteDataObject(NoteBytes type, NoteBytes code, NoteBytes receiverId, NoteBytes senderId, String... data){
        NoteBytesObject obj = getNoteDataObject(type, code, System.currentTimeMillis(), receiverId, senderId);
        obj.add("data", new NoteStringArray(data));
        return obj;
    }

    public static NoteBytesObject getNoteDataObject(NoteBytes type, NoteBytes code, long timeStamp, NoteBytes receiverId, NoteBytes senderId){
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(Keys.TIMESTAMP, timeStamp);
        nbo.add(Keys.RECEIVER_ID, receiverId);
        nbo.add(Keys.SENDER_ID, senderId);
        nbo.add(Keys.CODE_KEY, code);
        return nbo;
    }
    
}
