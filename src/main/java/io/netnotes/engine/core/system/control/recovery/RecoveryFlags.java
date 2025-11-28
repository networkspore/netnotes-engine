package io.netnotes.engine.core.system.control.recovery;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import io.netnotes.engine.core.SettingsData;

public class RecoveryFlags {
    private static final String RECOVERY_FLAG_FILENAME = "needs_recovery.flag";
    
    public static File getRecoveryFlagFile() {
        return new File(SettingsData.getDataDir(), RECOVERY_FLAG_FILENAME);
    }
    
    public static void setRecoveryNeeded(String reason) {
        try {
            File flagFile = getRecoveryFlagFile();
            Files.writeString(flagFile.toPath(), 
                reason + "\n" + System.currentTimeMillis());
            System.out.println("[Recovery] Flag set: " + reason);
        } catch (IOException e) {
            System.err.println("[Recovery] Failed to set flag: " + e.getMessage());
        }
    }
    
    public static boolean isRecoveryNeeded() {
        return getRecoveryFlagFile().exists();
    }
    
    public static void clearRecoveryFlag() {
        try {
            Files.deleteIfExists(getRecoveryFlagFile().toPath());
            System.out.println("[Recovery] Flag cleared");
        } catch (IOException e) {
            System.err.println("[Recovery] Failed to clear flag: " + e.getMessage());
        }
    }
    
    public static String getRecoveryReason() {
        try {
            File flagFile = getRecoveryFlagFile();
            if (flagFile.exists()) {
                String content = Files.readString(flagFile.toPath());
                return content.split("\n")[0];
            }
        } catch (IOException e) {
            System.err.println("[Recovery] Failed to read flag: " + e.getMessage());
        }
        return "Unknown reason";
    }
}