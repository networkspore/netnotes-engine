package io.netnotes.engine;

import java.io.File;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

public class AppConstants {

    public final static File LOG_FILE = new File("netnotes-log.txt");
    public final static String ASSETS_DIRECTORY = "/assets";

    public static final String APP_ICON = ASSETS_DIRECTORY + "/apps-outline-35.png";
    public static final String CHECKMARK_ICON = ASSETS_DIRECTORY + "/checkmark-25.png";
    public static final String MINIMIZE_ICON = ASSETS_DIRECTORY + "/minimize-white-20.png";
    public static final String MAXIMIZE_ICON = ASSETS_DIRECTORY + "/maximize-white-30.png";
    public static final String FILL_RIGHT_ICON = ASSETS_DIRECTORY + "/fillRight.png";
    public static final String CLOSE_ICON = ASSETS_DIRECTORY + "/close-outline-white.png";
    public static final String SETTINGS_ICON = ASSETS_DIRECTORY + "/settings-outline-white-30.png";
    public static final String CARET_DOWN_ICON = ASSETS_DIRECTORY + "/caret-down-15.png";
    public static final String MENU_ICON = ASSETS_DIRECTORY + "/menu-outline-30.png";
    public static final String NAV_ICON = ASSETS_DIRECTORY + "/navigate-outline-white-30.png";
    public static final String SHOW_ICON = ASSETS_DIRECTORY + "/eye-30.png";
    public static final String HIDE_ICON = ASSETS_DIRECTORY + "/eye-30.png";
    public static final String CLOUD_ICON = ASSETS_DIRECTORY + "/cloud-download-30.png";
    public static final String BAR_CHART_ICON = ASSETS_DIRECTORY + "/bar-chart-30.png";
    public static final String UNAVAILBLE_ICON = ASSETS_DIRECTORY + "/unavailable.png";
    public static final String NETWORK_ICON = ASSETS_DIRECTORY + "/globe-outline-white-30.png";
    public static final String NETWORK_ICON256 = ASSETS_DIRECTORY + "/globe-outline-white-120.png";

    public final static ExtensionFilter JAR_EXT = new FileChooser.ExtensionFilter("application/x-java-archive", "*.jar");
    public final static ExtensionFilter JSON_EXT = new FileChooser.ExtensionFilter("application/json", "*.json");


}
