package io.netnotes.engine.adapters.notes;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.AppBox;
import io.netnotes.engine.AppData;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.Stages;
import io.netnotes.engine.TabInterface;
import io.netnotes.engine.Utils;
import io.netnotes.engine.adapters.Adapter;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.friendly_id.FriendlyId;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import scorex.util.encode.Base16;

public class NotesAdapter extends Adapter {

    public final static String DESCRIPTION = "Notes Adapter enables the P2P networking of Netnotes enabled applications via the Notes protocol";
    public final static String NAME = "Notes Adapter";
    public final static String WEB_URL = "";
    public final static String NETWORK_ID = "NOTES_ADAPTER";
    public final static String ICON_URL = Stages.NETNOTES_LOGO_WHITE;
    public final static String ICON_256_URL = Stages.NETNOTES_LOGO_256;

    public final static String DEFAULT_FOLDER_NAME = ".netnotes";
    public final static String DEFAULT_FOLDER = AppData.HOME_DIRECTORY + "/" + DEFAULT_FOLDER_NAME;
    
    public final static long WATCH_INTERVAL = 50;


    private HashMap<String, File> m_availableAdapters = new HashMap<>();


    private File m_rootDir = null;
    private File m_notesDir = null;
    private File m_adapterDir = null;

    private NoteWatcher m_noteWatcher = null;

    public NotesAdapter(NetworksData networksData){
        super(new Image(ICON_URL), NAME, NETWORK_ID, networksData);
        setDescpription(DESCRIPTION);
        getNetworksData().getData("data", ".", NETWORK_ID, NetworksData.ADAPTERS, (onComplete)->{
            Object obj = onComplete.getSource().getValue();
            openJson(obj != null && obj instanceof JsonObject ? (JsonObject) obj : null);
        });
    }

    private void openJson(JsonObject json){

        JsonElement rootDirElement = json != null ? json.get("rootDir") : null;
        JsonElement adapterIdElement = json != null ? json.get("adapterId") : null;
        JsonElement isEnabledElement = json != null ? json.get("isEnabled") : null;
        String rootDirElementString =  rootDirElement != null ?rootDirElement.getAsString() : null;

        String rootDir = rootDirElementString != null && Utils.findPathPrefixInRoots(rootDirElementString) ? rootDirElementString : DEFAULT_FOLDER;
        String adapterId = adapterIdElement != null ? adapterIdElement.getAsString() : FriendlyId.createFriendlyId();
        boolean isEnabled = isEnabledElement != null ? isEnabledElement.getAsBoolean() : true;

        setIsEnabled(isEnabled);
        setAdapterId(adapterId);

        m_rootDir = new File(rootDir);
        m_notesDir = new File(m_rootDir.getAbsolutePath() + "/" + adapterId);

       
            try {
                if(!m_rootDir.getParentFile().isDirectory() || !m_rootDir.isDirectory() || !m_notesDir.isDirectory()){
                    Files.createDirectories(m_notesDir.toPath());
                }
                m_adapterDir = new File(m_notesDir + "/" + adapterId);
                
                if (!m_adapterDir.isDirectory()) {
                     Files.createDirectory(m_adapterDir.toPath());
                }
               
                start();
                

            } catch (IOException e) {

                try {
                    Files.writeString(AppConstants.LOG_FILE.toPath(), "nodeAdapter error: " + e.toString() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e1) {

                }
                setConnectionStatus(NoteConstants.ERROR);
            }
        

    }

    public boolean isAvailable(){
        return getConnectionStatus() == NoteConstants.STARTED;
    }

    public static NetworkInformation getNetworkInformation(){
        return new NetworkInformation(NETWORK_ID, NAME, ICON_URL, ICON_256_URL, DESCRIPTION);
    }


    @Override
    public void start(){
        if(isEnabled()){
            if(m_noteWatcher != null){
                m_noteWatcher.shutdown();
            }
            NoteListener noteListener = new NoteListener() {
                private EventHandler<WorkerStateEvent> onNotes = (onChanged) ->{
                    Object obj = onChanged.getSource().getValue();
                    if(obj != null && obj instanceof String[]){
                        inputNotes((String[]) obj);
                    }
                };

                @Override
                public EventHandler<WorkerStateEvent> getChangeHandler() {
                    return onNotes;
                }
            };
            try {
                m_noteWatcher = new NoteWatcher(m_adapterDir, noteListener, getNetworksData().getExecService());
                super.start();
            } catch (IOException e) {
                try {
                    Files.writeString(AppConstants.LOG_FILE.toPath(), "Note Adapter, Note Watcher failed to initialize: " + e.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e1) {

                }
                setConnectionStatus(NoteConstants.ERROR);
            }
        }
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }


    public void setConnectionStatus(int status){
        super.setConnectionStatus(status);
    }

    @Override
    public void shutdown(){
        stop();
    }
    
    @Override
    public void stop(){
        if(m_noteWatcher != null){
            m_noteWatcher.shutdown();
            m_noteWatcher = null;
        }
        super.stop();
    }


    private void inputNotes(String[] notes){
        for(String note : notes){
            inputNote(note);
        }
    }

    private void inputNote(String note){
      
        try {
            String nameMsg = Base16.decode(note).getOrElse(null);
            Files.writeString(AppConstants.LOG_FILE.toPath(), nameMsg + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            try {
                Files.writeString(AppConstants.LOG_FILE.toPath(), "intputNoteError: " + e.toString() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e1) {

            }
        }
    }



    private NotesAdapterTab m_notesAdapterTab = null;

    @Override
    public TabInterface getTab(Stage appStage, SimpleDoubleProperty heightObject,
    SimpleDoubleProperty widthObject, Button menuBtn){
        if(m_notesAdapterTab != null){
            return m_notesAdapterTab;
        }else{
            m_notesAdapterTab = new NotesAdapterTab(appStage,  heightObject, widthObject, menuBtn);
            return m_notesAdapterTab;
        }
    }

    public class NotesAdapterTab extends AppBox implements TabInterface {
        private SimpleStringProperty m_status = new SimpleStringProperty(NoteConstants.STATUS_STOPPED);
        private SimpleDoubleProperty m_heightObject = null;
        private SimpleDoubleProperty m_widthObject = null;
        private Stage m_appStage = null;
        private Button m_menuBtn = null;

        public NotesAdapterTab(Stage appStage,  SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, Button menuBtn){
            super(getAdapterId());

            m_appStage = appStage;
            m_menuBtn = menuBtn;
            m_heightObject = heightObject;
            m_widthObject = widthObject;

            setPrefWidth(NetworksData.DEFAULT_STATIC_WIDTH);
            setMaxWidth(NetworksData.DEFAULT_STATIC_WIDTH);

        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public void setStatus(String status) {
            m_status.set(status);
        }

        @Override
        public String getStatus() {
            return m_status.get();
        }

        @Override
        public SimpleStringProperty titleProperty() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'titleProperty'");
        }
        
    } 
}
