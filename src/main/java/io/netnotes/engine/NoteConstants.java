package io.netnotes.engine;



public class NoteConstants {

    public static final byte KEY_AND_VALUE = 0x12;
    public static final byte VALUE_AND_KEY = 0x21;
    public static final byte KEY_NO_VALUE = 0x10;
    public static final byte VALUE_NO_KEY = 0x00;

    public final static long POLLING_TIME = 7000;
    
    public static final String DEFAULT = "Default";
    public static final String SUCCESS = "Success";
    public static final String ERROR = "Error";

    public static final String DISABLED = "Disabled";
    public static final String STARTING = "Starting";
    public static final String STARTED = "Started";

    public static final String STOPPING = "Stopping";
    public static final String STOPPED = "Stopped";
    public static final String SHUTDOWN = "Shutdown";

    public static final String WARNING = "Warning";
    public static final String STATUS = "Status";
    public static final String UPDATING = "Updating";
    public static final String UPDATED = "Updated";
    public static final String INFO = "Info";
    public static final String CANCEL = "Cancel";
    public static final String READY = "Ready";

    public static final String MSG_SEND_NOTE = "Send note";

    
    public static final String LIST_CHANGED = "List changed";
    public static final String LIST_CHECKED = "List checked";
    public static final String LIST_UPDATED = "List updated";
    public static final String LIST_ITEM_ADDED = "List item added";
    public static final String LIST_ITEM_REMOVED = "List item removed";
    public static final String LIST_DEFAULT_CHANGED = "Default item changed";



    public static final String STATIC_TYPE ="STATIC";
    public static final String STATUS_MINIMIZED ="Minimized";
    public static final String STATUS_UPDATED ="Updated";
    public static final String STATUS_STOPPED ="Stopped";
    public static final String STATUS_STARTED ="Started";
    public static final String STATUS_STARTING ="Starting";
    public static final String STATUS_UNAVAILABLE ="Unavailable";
    public static final String STATUS_AVAILABLE ="Available";
    public static final String STATUS_ERROR ="Error";
    public static final String STATUS_SHUTTING_DOWN ="Shutting Down";
    public static final String STATUS_SHUTDOWN ="Shutdown";
    public static final String STATUS_READY ="Ready";
    public static final String STATUS_TIMED_OUT ="Timed Out";
    public static final String STATUS_DISABLED ="Disabled";
    public static final String STATUS_UNKNOWN ="Unknown";
    
    public static final String VERIFIED ="Verified";
    public static final String BLOCKED ="Blocked";


    public static final String ERROR_CLOSING = "Closing";
    public static final String ERROR_CANCELED = "Canceled";
    public static final String ERROR_EXISTS = "Exists";
    public static final String ERROR_INVALID = "Invalid";
    public static final String ERROR_IO = "IO";
    public static final String ERROR_NOT_FOUND = "Not found";
    public static final String ERROR_CONTROL_NOT_AVAILABLE = "Control not available";
    public static final String ERROR_OUT_OF_RANGE = "Out of range";

    public static final String SEARCH_SORT_ASC = "asc";
    public static final String SEARCH_SORT_DSC = "dsc";

    public static final String CMD_NOT_PRESENT = "cmd not present";

    public static final String CMD = "cmd";






    public static String checkAsc(String value){
       return (value != null && value.toLowerCase().equals(NoteConstants.SEARCH_SORT_ASC.toLowerCase())) ? NoteConstants.SEARCH_SORT_ASC : NoteConstants.SEARCH_SORT_DSC;
    }

    public static String getNoteBytesObjectName(String defaultString, NoteBytesObject json){
        String name = json == null ? defaultString : NoteConstants.getNoteBytesObjectName(json);
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
                NoteBytes nodeObjectId = NoteConstants.getValueByMemberName(nbo, "networkId");
                if(nodeObjectId != null && nodeObjectId.equals(id)){
                    return nbo;
                }
            }
        }
        return null;
    }



    public static NoteBytesObject getJsonObjectById(NoteBytes id, NoteBytesArray nbArray){
        if(nbArray != null){
            NoteBytes[] noteBytesarray = nbArray.getAsArray();
            for(NoteBytes noteBytes : noteBytesarray){
                NoteBytesObject nbo = noteBytes.getAsNoteBytesObject();
                NoteBytes nodeObjectId = NoteConstants.geNoteBytesObjectId(nbo);
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

    public static NoteBytesObject getCmdObject(NoteBytes subject) {
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add(NoteConstants.CMD, subject);
        nbo.add("timeStamp", System.currentTimeMillis());
        return nbo;
    }

    public static NoteBytesObject getMsgObject(int code, long timeStamp, String networkId){
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add("timeStamp", timeStamp);
        nbo.add("networkId", networkId);
        nbo.add("code", code);
        return nbo;
    }

    public static NoteBytesObject getMsgObject(int code, String msg){
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add("code", code);
        nbo.add("msg", msg);
        return nbo;
    }

    

    public static NoteBytesObject getCmdObject(NoteBytes cmd, NoteBytes locationId){        
        NoteBytesObject note = NoteConstants.getCmdObject(cmd);
        note.add("locationId", locationId);
        return note;
    }

    public static NoteBytesObject getCmdObject(NoteBytes cmd, NoteBytes networkId, NoteBytes locationId){        
        NoteBytesObject note = NoteConstants.getCmdObject(cmd);
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


    /*public static Future<?> getInterfaceNetworkObjects(Iterator<NoteInterface> it, JsonArray jsonArray, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded,  EventHandler<WorkerStateEvent> onFailed ){
        if(it.hasNext()){
            NoteInterface noteInterface = it.next();
            return noteInterface.sendNote(NoteConstants.getCmdObject("getNetworkObject"),(onNetworkObject)->{
                Object obj = onNetworkObject.getSource().getValue();
                if(obj != null && obj instanceof JsonObject){
                    jsonArray.add((JsonObject) obj);
                    getInterfaceNetworkObjects(it, jsonArray, execService, onSucceeded, onFailed);
                }
            } , onFailed);
        }else{
            return Utils.returnObject(jsonArray, execService, onSucceeded);
        }
    }*/
}
