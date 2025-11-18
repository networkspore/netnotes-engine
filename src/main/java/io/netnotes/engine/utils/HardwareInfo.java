package io.netnotes.engine.utils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.EncodingHelpers;
import io.netnotes.engine.noteBytes.NoteString;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor.PhysicalProcessor;
import oshi.hardware.NetworkIF;
import oshi.hardware.PhysicalMemory;
import oshi.software.os.OSFileStore;

public class HardwareInfo {

    private static CompletableFuture<NoteBytesReadOnly> CPU_FINGERPRINT = null;

    public static String getStringOrRandom(String str){
        return str != null && str.length() > 0? str : RandomService.getRandomString(30);
    }

    private static NoteBytesObject processorBytes(SystemInfo systemInfo){
        List<PhysicalProcessor> physicalProcessorList = systemInfo.getHardware().getProcessor().getPhysicalProcessors();
        
        NoteBytesMap map = new NoteBytesMap();
        for(PhysicalProcessor physicalProcessor : physicalProcessorList){
            map.put(new NoteBytes(physicalProcessor.getPhysicalPackageNumber()), new NoteString( physicalProcessor.getIdString()));
        }
        return map.getNoteBytesObject();
    }

    public static byte[] filterHexStringToBytes(String mac, Set<Integer> filterSet){

        int[] cps = mac.toUpperCase().codePoints().filter(filterSet::contains).toArray();
        char[] chars = new char[cps.length];
        for(int i = 0; i < cps.length; i++){
            int cp = cps[i];
            chars[i] = (char) cp;
        }
        return ByteDecoding.charsToBytes(chars);
    }

    private static NoteBytesObject nicBytes(SystemInfo systemInfo){
        List<NetworkIF> networkIfList = systemInfo.getHardware().getNetworkIFs();
        NoteBytesMap map = new NoteBytesMap();
        
        for(NetworkIF networkIf : networkIfList){
            
            String mac = networkIf.getMacaddr();
            map.put(new NoteString(networkIf.getName()), new NoteString(mac));
        }
        return map.getNoteBytesObject();
    }


    private static NoteBytesObject memorySerialNumberBytes(SystemInfo systemInfo){
        List<PhysicalMemory> pysicalMemoryList = systemInfo.getHardware().getMemory().getPhysicalMemory();
        
        NoteBytesMap map = new NoteBytesMap();
        for(PhysicalMemory physicalMemory : pysicalMemoryList){
            map.put(new NoteString(physicalMemory.getBankLabel()), new NoteString( physicalMemory.getSerialNumber()));
        }
        return map.getNoteBytesObject();
    }

    private static NoteBytesObject hdUuidBytes(SystemInfo systemInfo){
        List<OSFileStore> fileStores = systemInfo.getOperatingSystem().getFileSystem().getFileStores();

        NoteBytesMap map = new NoteBytesMap();
        for(int i = 0; i < fileStores.size() ; i++){
            OSFileStore store = fileStores.get(i);
            map.put(new NoteBytes(store.getName()), new NoteString((store.getUUID())));
        }
        return map.getNoteBytesObject();
    }

    public static final String NIC_STRING      = "nic";
    public static final String CPU_STRING      = "cpu";
    public static final String HDD_STRING      = "hdd";
    public static final String MEMORY_STRING   = "memory";

    public static final NoteBytesReadOnly NIC_KEY = new NoteBytesReadOnly(NIC_STRING);
    public static final NoteBytesReadOnly CPU_KEY = new NoteBytesReadOnly(CPU_STRING);
    public static final NoteBytesReadOnly HDD_KEY = new NoteBytesReadOnly(HDD_STRING);
    public static final NoteBytesReadOnly MEMORY_KEY = new NoteBytesReadOnly(MEMORY_STRING);

    public static CompletableFuture<NoteBytesObject> getHardwareInfo(String... keys){
        return getHardwareInfo(new NoteStringArrayReadOnly(keys));
    }

    public static CompletableFuture<NoteBytesObject> getHardwareInfo(NoteBytes... keys){
        return getHardwareInfo(new NoteStringArrayReadOnly(keys));
    }



    public static CompletableFuture<NoteBytesObject> getHardwareInfo(NoteBytesArrayReadOnly keys){
        
        return CompletableFuture.supplyAsync(()->{
            SystemInfo systemInfo = new SystemInfo();
            NoteBytesMap hardwareInfo = new NoteBytesMap();
           

            if(keys.contains(NIC_KEY)){
                hardwareInfo.put(NIC_KEY, nicBytes(systemInfo));
            }
            if(keys.contains(CPU_KEY)){
                hardwareInfo.put(CPU_KEY, processorBytes(systemInfo));
            }
            if(keys.contains(MEMORY_KEY)){
                hardwareInfo.put(MEMORY_KEY, memorySerialNumberBytes(systemInfo));
            }
            if(keys.contains(HDD_KEY)){
                hardwareInfo.put(HDD_KEY,  hdUuidBytes(systemInfo));
            }
            return hardwareInfo.getNoteBytesObject();
        }, VirtualExecutors.getVirtualExecutor());
    }

    public static CompletableFuture<NoteBytesReadOnly> getCPUFingerPrint(){
        CPU_FINGERPRINT = CPU_FINGERPRINT == null 
            ? getHardwareInfo(CPU_KEY)
                .thenApply(hardwareInfo -> {
                    return new NoteBytesReadOnly( 
                        HashServices.digestBytesToBytes(
                            hardwareInfo.getBytes(),32
                        )
                    );
                }) 
            : CPU_FINGERPRINT;

        return CPU_FINGERPRINT;
    }

    public static CompletableFuture<String> getCPUFingerPrintString(){
        CompletableFuture<NoteBytesReadOnly> future = getCPUFingerPrint();

        return future.thenApply(readOnlyBytes ->EncodingHelpers.encodeB64UrlSafeString(readOnlyBytes.get()));
    }
}
