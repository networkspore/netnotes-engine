package io.netnotes.engine.noteBytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteHashing;
import io.netnotes.engine.utils.HardwareInfo;
import io.netnotes.engine.utils.Utils;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public class NoteUUID extends NoteBytes {

    private static volatile AtomicInteger m_atomicByte = new AtomicInteger(ByteDecoding.bytesToIntBigEndian(RandomService.getRandomBytes(4)));

    private boolean m_isInit = false;

    public NoteUUID(byte[] bytes){
        super(bytes, ByteDecoding.RAW_BYTES);
    }


    @Override
    public void set(byte[] bytes, ByteDecoding byteDecoding){
        if(!m_isInit){
            super.set(bytes, byteDecoding);
            m_isInit = true;
        }
    }

    @Override
    public byte[] get(){
        byte[] bytes = super.get();
        return Arrays.copyOf(bytes, byteLength());
    }

    @Override
    public boolean equals(Object obj){
        if(obj != null){
            if( obj.getClass().equals(this.getClass())){
                return ((NoteBytes) obj).compareBytes(super.get());
            }else if(obj instanceof NoteBytes){
                return compareBytes(((NoteBytes) obj).get());
            }
        }
        return false;
    }

  

    public static byte[] littleEndianNanoTimeHash(){
        return ByteDecoding.intToBytesLittleEndian(Long.hashCode(System.nanoTime()));
    }

    public static byte[] littleEndianCurrentTime(){
        return ByteDecoding.longToBytesLittleEndian(System.currentTimeMillis());
    }

    public static int getAndIncrementByte(){
        return m_atomicByte.updateAndGet((i)->i < 255 ? i + 1 : 0);

    }

    public static NoteUUID createLocalUUID128(){
        return new NoteUUID(createTimeRndBytes());
    }

    public static NoteBytesReadOnly createLocalUUID128ReadOnly(){
        return new NoteBytesReadOnly(createTimeRndBytes());
    }

    public static String createSafeUUID128(){
        return createLocalUUID128().getAsUrlSafeString();
    }

    public static byte[] createTimeRndBytes(){
		byte[] nanoTime = littleEndianNanoTimeHash();
		byte[] randomBytes = RandomService.getRandomBytes(5);
		byte[] currentTime = littleEndianCurrentTime();
        byte[] bytes = new byte[] {
            nanoTime[2], randomBytes[0], currentTime[3], randomBytes[2],
            nanoTime[3], currentTime[2], randomBytes[4], nanoTime[0],
            currentTime[5], randomBytes[1], (byte) getAndIncrementByte(), currentTime[4],
            nanoTime[4], currentTime[7], randomBytes[3], currentTime[6]
        };

        return bytes;
    }

    public static void createNotesUUID256(ExecutorService execService, EventHandler<WorkerStateEvent> onComplete, EventHandler<WorkerStateEvent> onFailed ){ 
        HardwareInfo.getHardwareInfo(new NoteStringArray("nic","hdd"), execService, (onHardwareInfo)->{
            Object obj = onHardwareInfo.getSource().getValue();
            if(obj != null && obj instanceof HardwareInfo){
                HardwareInfo hardwareInfo = (HardwareInfo) obj;
                Task<Object> task = new Task<Object>() {
                    @Override
                    public Object call() throws IOException {
                        try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
                            NoteBytesPair[] sources = hardwareInfo.getAsArray();
                            for(NoteBytesPair source : sources){
                                NoteBytesPair[] sourceItems = source.getValue().getAsNoteBytesObject().getAsArray();
                                int itemsLength = sourceItems.length;
                                if(itemsLength > 0){
                                    outputStream.write( sourceItems[RandomService.getRandomInt(0, itemsLength-1)].getValue().get() );
                                }
                            }
                            return ByteDecoding.concat(createTimeRndBytes(), ByteHashing.digestBytesToBytes(outputStream.toByteArray(), 16));
                        }
                    }
                };
                task.setOnFailed(onFailed);
                task.setOnSucceeded(onComplete);
                execService.submit(task);
            }else{
                Utils.returnException(NoteMessaging.Error.INVALID, execService, onFailed);
            }
        }, onFailed);
    }

    public static NoteUUID fromURLSafeString(String urlSafeString){
        return new NoteUUID(Base64.getUrlDecoder().decode(urlSafeString));
    }




    @Override
    public String toString(){
        return new String(decodeCharArray());
    }
}
