package io.netnotes.engine;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteListString;
import io.netnotes.engine.noteBytes.NoteString;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor.PhysicalProcessor;
import oshi.hardware.NetworkIF;
import oshi.hardware.PhysicalMemory;
import oshi.software.os.OSFileStore;

public class HardwareInfo extends NoteBytesObject {

    public HardwareInfo(){
        super();
    }

    public static String getStringOrRandom(String str){
        return str != null && str.length() > 0? str : Utils.getRandomString(30);
    }

    private static NoteBytesObject processorBytes(SystemInfo systemInfo){
        List<PhysicalProcessor> physicalProcessorList = systemInfo.getHardware().getProcessor().getPhysicalProcessors();
        
        NoteBytesObject pairTree = new NoteBytesObject();
        int i = 0;
        for(PhysicalProcessor physicalProcessor : physicalProcessorList){
            pairTree.add(new NoteBytes(i + ": " + physicalProcessor.getPhysicalPackageNumber()), new NoteString( getStringOrRandom(physicalProcessor.getIdString())));
            i++;
        }
        return  pairTree;
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
        NoteBytesObject pairTree = new NoteBytesObject();
        int i = 0;
        
        for(NetworkIF networkIf : networkIfList){
            
            String mac = networkIf.getMacaddr();
            pairTree.add(new NoteBytes(i + ": " + networkIf.getName()), new NoteBytes(mac));
            i++;
        }
        return pairTree;
    }


    private static NoteBytesObject memorySerialNumberBytes(SystemInfo systemInfo){
        List<PhysicalMemory> pysicalMemoryList = systemInfo.getHardware().getMemory().getPhysicalMemory();
        
        NoteBytesObject pairTree = new NoteBytesObject();
        int i = 0;
        for(PhysicalMemory physicalMemory : pysicalMemoryList){
            pairTree.add(new NoteBytes(i + ": " + physicalMemory.getBankLabel()), new NoteString( getStringOrRandom(physicalMemory.getSerialNumber())));
            i++;
        }
        return pairTree;
    }

    private static NoteBytesObject hdUuidBytes(SystemInfo systemInfo){
        List<OSFileStore> fileStores = systemInfo.getOperatingSystem().getFileSystem().getFileStores();

        NoteBytesObject pairTree = new NoteBytesObject();
        for(int i = 0; i < fileStores.size() ; i++){
            OSFileStore store = fileStores.get(i);
            pairTree.add(new NoteBytes(i + ": " + store.getName()), new NoteString( getStringOrRandom(store.getUUID())));
            i++;
        }
        return pairTree;
    }



    public static Future<?> getHardwareInfo(NoteListString requestList, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() {
                SystemInfo systemInfo = new SystemInfo();
  
                HardwareInfo notePairTree = new HardwareInfo();
                List<NoteBytes> list = requestList.getAsList();
                if(list.contains(new NoteBytes("nic"))){
                    notePairTree.add("nic", nicBytes(systemInfo));
                }
                if(list.contains(new NoteBytes("cpu"))){
                    notePairTree.add("cpu", processorBytes(systemInfo));
                }
                if(list.contains(new NoteBytes("memory"))){
                    notePairTree.add("memory", memorySerialNumberBytes(systemInfo));
                }
                if(list.contains(new NoteBytes("hdd"))){
                    notePairTree.add("hdd",  hdUuidBytes(systemInfo));
                }
                return notePairTree;
            }

        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    

    }

   

}
