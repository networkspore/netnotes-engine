package io.netnotes.engine.utils.shell;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netnotes.engine.utils.FreeMemory;

public class ShellHelpers {
    private static final String WIN = "win";
    private static final String MAC = "mac";

    private static final String OS = System.getProperty("os.name").toLowerCase();

    private static final AtomicReference<CompletableFuture<FreeMemory>> cachedFreeMemory = new AtomicReference<>(null);
    private static final AtomicLong cachedFreeMemoryTime = new AtomicLong(0);
    private static final long FREE_MEMORY_CACHE_DURATION_MS = 5000;

    public static CompletableFuture<Void> open(File file, ExecutorService execService) throws IOException {
        return CompletableFuture.runAsync(()->{
            try{
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
            }catch(IOException e){
                throw new CompletionException("Could not open file", e);
            }

        }, execService);
    }

    public static CompletableFuture<Boolean> sendKillSig(String jarName, ExecutorService execService) {
        return CompletableFuture.supplyAsync(()->{
             try{
                switch (OS) {
                    case WIN:
                        return WinShellHelpers.sendKillSig(jarName);
                    case MAC:
                        return MacShellHelpers.sendKillSig(jarName);
                    default:
                        return LinuxShellHelpers.sendKillSig(jarName);
                }
             }catch(Exception e){
                throw new CompletionException("Could not kill task", e);
            }
        }, execService);
    }

    public static CompletableFuture<String[]> findPIDs(String jarName, ExecutorService execService) {
        return CompletableFuture.supplyAsync(()->{
            try{
                switch (OS) {
                    case WIN:
                        return WinShellHelpers.findPIDs(jarName);
                    case MAC:
                        return MacShellHelpers.findPIDs(jarName);
                    default:
                        return LinuxShellHelpers.findPIDs(jarName);
                }
            }catch(Exception e){
                 throw new CompletionException("Could not find pids", e);
            }
          },execService);
    }

    public static CompletableFuture<FreeMemory> getFreeMemory(ExecutorService execService){
      
        long currentTime = System.currentTimeMillis();
        CompletableFuture<FreeMemory> cached = cachedFreeMemory.get();
        if(cached != null && 
            (currentTime - cachedFreeMemoryTime.get()) < FREE_MEMORY_CACHE_DURATION_MS
        ){
            return cached;
        }else{
            CompletableFuture<FreeMemory> newCache = fetchFreeMemory(execService);
            cachedFreeMemory.set(newCache);
            cachedFreeMemoryTime.set(currentTime);
            return newCache;
        }
   
    }

    private static CompletableFuture<FreeMemory> fetchFreeMemory(ExecutorService execService){
        return CompletableFuture.supplyAsync(()->{
            try{
                switch (OS) {
                    case WIN:
                        return WinShellHelpers.getFreeMemory();
                    case MAC:
                        return MacShellHelpers.getFreeMemory();
                    default:
                        return LinuxShellHelpers.getFreeMemory();
                }
            }catch(Exception e){
                 throw new CompletionException("Could not get free memory", e);
            }
          },execService);
    }

    public static long parseValueLong(String line) {
        return parseValueBigDecimal(line).longValue();
    }

    public static BigDecimal parseValueBigDecimal(String line) {
        String digits = line.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return BigDecimal.ONE.negate();
        return new BigDecimal(digits);
    }

    public static BigDecimal parseJsonBigDecimal(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)");
        Matcher m = p.matcher(json);
        return m.find() ? new BigDecimal(m.group(1)) : BigDecimal.ONE.negate();
    }

    public static long parseJsonLong(String json, String key) {
        return parseJsonBigDecimal(json, key).longValue();
    }
}
