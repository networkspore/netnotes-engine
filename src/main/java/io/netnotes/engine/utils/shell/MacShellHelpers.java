package io.netnotes.engine.utils.shell;

import java.io.File;
import java.io.IOException;

public class MacShellHelpers {

    public static String[] findPIDs(String jarName) {
        return LinuxShellHelpers.findPIDs(jarName); // same logic works
    }

    public static void open(File file) throws IOException {
        String path = file.getCanonicalPath().replace("\"", "\\\"");
        String[] cmd = { "open", path };
        Runtime.getRuntime().exec(cmd);
    }
 
    public static boolean sendKillSig(String jarName) {
        return LinuxShellHelpers.sendKillSig(jarName); 
    }
}
