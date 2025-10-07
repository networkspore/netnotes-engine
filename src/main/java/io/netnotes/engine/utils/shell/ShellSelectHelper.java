package io.netnotes.engine.utils.shell;

import java.io.File;
import java.io.IOException;

public class ShellSelectHelper {
    private static final String WIN = "win";
    private static final String MAC = "mac";
    private static final String OS = System.getProperty("os.name").toLowerCase();

    // convenience passthroughs
    public static void open(File file) throws IOException {
        switch (OS) {
            case WIN:
                WinShellHelpers.open(file);
                break;
            case MAC:
                MacShellHelpers.open(file);
            break;
            default:
                LinuxShellHelpers.open(file);
                break;
        }
    }

    public static boolean sendKillSig(String jarName) {
        switch (OS) {
            case WIN:
                return WinShellHelpers.sendKillSig(jarName);
            case MAC:
                return MacShellHelpers.sendKillSig(jarName);
            default:
                return LinuxShellHelpers.sendKillSig(jarName);
        }
    }

    public static String[] findPIDs(String jarName) {
        switch (OS) {
            case WIN:
                return WinShellHelpers.findPIDs(jarName);
            case MAC:
                return MacShellHelpers.findPIDs(jarName);
            default:
                return LinuxShellHelpers.findPIDs(jarName);
        }
    }
}
