package io.netnotes.engine.apps;

import java.io.File;

import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

public class AppConstants {

    public final static File LOG_FILE = new File("netnotes-log.txt");

    public static final String APP_ICON = "/assets/apps-outline-35.png";
    public static final String CHECKMARK_ICON = "/assets/checkmark-25.png";
    public static final String MINIMIZE_ICON = "/assets/minimize-white-20.png";
    public static final String MAXIMIZE_ICON = "/assets/maximize-white-30.png";
    public static final String FILL_RIGHT_ICON = "/assets/fillRight.png";
    public static final String CLOSE_ICON = "/assets/close-outline-white.png";
    public static final String SETTINGS_ICON = "/assets/settings-outline-white-30.png";
    public static final String CARET_DOWN_ICON = "/assets/caret-down-15.png";
    public static final String MENU_ICON = "/assets/menu-outline-30.png";
    public static final String NAV_ICON = "/assets/navigate-outline-white-30.png";
    public static final String SHOW_ICON = "/assets/eye-30.png";
    public static final String HIDE_ICON = "/assets/eye-30.png";
    public static final String CLOUD_ICON = "/assets/cloud-download-30.png";

    public final static ExtensionFilter JAR_EXT = new FileChooser.ExtensionFilter("application/x-java-archive", "*.jar");
    public final static ExtensionFilter JSON_EXT = new FileChooser.ExtensionFilter("application/json", "*.json");

}
