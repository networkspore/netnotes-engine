package io.netnotes.engine.utils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArray;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.NoteString;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor.PhysicalProcessor;
import oshi.hardware.NetworkIF;
import oshi.hardware.PhysicalMemory;
import oshi.software.os.OSFileStore;

public class HardwareInfo {


    public static String getStringOrRandom(String str){
        return str != null && str.length() > 0? str : RandomService.getRandomString(30);
    }

    private static NoteBytesObject processorBytes(SystemInfo systemInfo){
        List<PhysicalProcessor> physicalProcessorList = systemInfo.getHardware().getProcessor().getPhysicalProcessors();
        
        NoteBytesMap map = new NoteBytesMap();
        int i = 0;
        for(PhysicalProcessor physicalProcessor : physicalProcessorList){
            map.put(new NoteBytes(i + ": " + physicalProcessor.getPhysicalPackageNumber()), new NoteString( getStringOrRandom(physicalProcessor.getIdString())));
            i++;
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
        int i = 0;
        
        for(NetworkIF networkIf : networkIfList){
            
            String mac = networkIf.getMacaddr();
            map.put(new NoteBytes(i + ": " + networkIf.getName()), new NoteBytes(mac));
            i++;
        }
        return map.getNoteBytesObject();
    }


    private static NoteBytesObject memorySerialNumberBytes(SystemInfo systemInfo){
        List<PhysicalMemory> pysicalMemoryList = systemInfo.getHardware().getMemory().getPhysicalMemory();
        
        NoteBytesMap map = new NoteBytesMap();
        int i = 0;
        for(PhysicalMemory physicalMemory : pysicalMemoryList){
            map.put(new NoteBytes(i + ": " + physicalMemory.getBankLabel()), new NoteString( getStringOrRandom(physicalMemory.getSerialNumber())));
            i++;
        }
        return map.getNoteBytesObject();
    }

    private static NoteBytesObject hdUuidBytes(SystemInfo systemInfo){
        List<OSFileStore> fileStores = systemInfo.getOperatingSystem().getFileSystem().getFileStores();

        NoteBytesMap map = new NoteBytesMap();
        for(int i = 0; i < fileStores.size() ; i++){
            OSFileStore store = fileStores.get(i);
            map.put(new NoteBytes(i + ": " + store.getName()), new NoteString( getStringOrRandom(store.getUUID())));
            i++;
        }
        return map.getNoteBytesObject();
    }



    public static CompletableFuture<NoteBytesObject> getHardwareInfo(NoteStringArray requestList, ExecutorService execService){
        
        return CompletableFuture.supplyAsync(()->{
            SystemInfo systemInfo = new SystemInfo();

            NoteBytesMap hardwareInfo = new NoteBytesMap();
           
            List<NoteBytes> list = requestList.getAsList();
            if(list.contains(new NoteBytes("nic"))){
                hardwareInfo.put("nic", nicBytes(systemInfo));
            }
            if(list.contains(new NoteBytes("cpu"))){
                hardwareInfo.put("cpu", processorBytes(systemInfo));
            }
            if(list.contains(new NoteBytes("memory"))){
                hardwareInfo.put("memory", memorySerialNumberBytes(systemInfo));
            }
            if(list.contains(new NoteBytes("hdd"))){
                hardwareInfo.put("hdd",  hdUuidBytes(systemInfo));
            }
            return hardwareInfo.getNoteBytesObject();
        }, execService);
    }

   

}
