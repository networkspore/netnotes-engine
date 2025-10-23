package io.netnotes.engine.utils.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import io.netnotes.engine.utils.FreeMemory;

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

    public static FreeMemory getFreeMemory() {
        try {
            String[] cmd = {
                "sh", "-c",
                "mem_total=$(sysctl -n hw.memsize); " +
                "page_size=$(vm_stat | grep 'page size of' | awk '{print $8}'); " +
                "free_pages=$(vm_stat | grep 'Pages free' | awk '{print $3}' | tr -d '.'); " +
                "inactive_pages=$(vm_stat | grep 'Pages inactive' | awk '{print $3}' | tr -d '.'); " +
                "speculative_pages=$(vm_stat | grep 'Pages speculative' | awk '{print $3}' | tr -d '.'); " +
                "swap_total=$(sysctl -n vm.swapusage | awk '{print $3}' | tr -d 'M'); " +
                "swap_free=$(sysctl -n vm.swapusage | awk '{print $9}' | tr -d 'M'); " +
                "mem_free=$(( (free_pages + inactive_pages + speculative_pages) * page_size )); " +
                "echo '{\"MemTotal\":'\"$mem_total\"',\"MemFree\":'\"$mem_free\"',\"MemAvailable\":'\"$mem_free\"',\"SwapTotal\":'\"$((swap_total*1024*1024))\"',\"SwapFree\":'\"$((swap_free*1024*1024))\"'}'"
            };

            Process proc = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            proc.waitFor();

            String j = json.toString().trim();
            if (j.isEmpty()) return null;

            long memTotal = ShellHelpers.parseJsonLong(j, "MemTotal") / 1024;      // convert bytes → KB
            long memFree = ShellHelpers.parseJsonLong(j, "MemFree") / 1024;
            long memAvailable = ShellHelpers.parseJsonLong(j, "MemAvailable") / 1024;
            long swapTotal = ShellHelpers.parseJsonLong(j, "SwapTotal") / 1024;
            long swapFree = ShellHelpers.parseJsonLong(j, "SwapFree") / 1024;

            if (memTotal > 0) {
                return new FreeMemory(swapTotal, swapFree, memFree, memAvailable, memTotal);
            }

        } catch (Exception ignore) {}
        return null;
    }
}
