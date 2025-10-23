package io.netnotes.engine.noteBytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.EncodingHelpers;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.EncodingHelpers.Encoding;
import io.netnotes.engine.utils.HardwareInfo;

public class NoteUUID extends NoteBytes {

    private static volatile AtomicInteger m_atomicByte = new AtomicInteger(ByteDecoding.bytesToIntBigEndian(RandomService.getRandomBytes(4)));

    private NoteUUID(byte[] bytes){
        super(bytes, NoteBytesMetaData.STRING_ISO_8859_1_TYPE);
    }


    @Override
    public void set(byte[] bytes, byte type){
   
    }

    @Override
    public byte[] get(){
        byte[] bytes = super.internalGet();
        return Arrays.copyOf(bytes, byteLength());
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
        return fromUnencodedBytes(createTimeRndBytes());
    }

    public static String createSafeUUID128(){
        return createLocalUUID128().getAsString();
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

    public static CompletableFuture<NoteUUID> createNotesUUID256(ExecutorService execService){ 
        return HardwareInfo.getHardwareInfo(new NoteStringArray("nic","hdd"), execService).thenApply((hardwareInfo)->{
            try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
                NoteBytesPair[] sources = hardwareInfo.getAsArray();
                for(NoteBytesPair source : sources){
                    NoteBytesPair[] sourceItems = source.getValue().getAsNoteBytesObject().getAsArray();
                    int itemsLength = sourceItems.length;
                    if(itemsLength > 0){
                        outputStream.write( sourceItems[RandomService.getRandomInt(0, itemsLength-1)].getValue().get() );
                    }
                }
                byte[] bytes = ByteDecoding.concat(createTimeRndBytes(), HashServices.digestBytesToBytes(outputStream.toByteArray(), 16));

                return fromUnencodedBytes(bytes);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    public static NoteUUID fromNoteUUIDString(String urlSafeString){
        return new NoteUUID(urlSafeString.getBytes());
    }

    public static NoteUUID fromNoteUUIDBytes(byte[] bytes){
        return new NoteUUID(Arrays.copyOf(bytes, bytes.length));
    }

    public static NoteUUID fromUnencodedBytes(byte[] bytes){
        return fromNoteUUIDBytes( EncodingHelpers.encodeBytes(bytes, Encoding.URL_SAFE));

    }

}
