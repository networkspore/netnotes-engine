package io.netnotes.engine;

import java.io.File;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

public class CoreConstants {
    public final static File LOG_FILE = new File("netnotes-log.txt");
    public final static String ASSETS_DIRECTORY = "/assets";

    public final static ExtensionFilter JAR_EXT = new FileChooser.ExtensionFilter("application/x-java-archive", "*.jar");
    public final static ExtensionFilter JSON_EXT = new FileChooser.ExtensionFilter("application/json", "*.json");


}
