package io.netnotes.engine.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class CollectionHelpers {
    

    public static byte[] appendBytes(byte[] a, byte[] b){
        byte[] bytes = new byte[a.length + b.length];
       
        System.arraycopy(a, 0, bytes, 0, a.length);
        System.arraycopy(b, 0, bytes, a.length, b.length);
        return bytes;
    }

    public static int arrayCopy(Object src, int srcOffset, Object dst, int dstOffset, int length){
        System.arraycopy(src, srcOffset, dst, dstOffset, length);
        return dstOffset + length;
    }



    public static boolean isCollection(Object obj) {
        return obj.getClass().isArray() || obj instanceof Collection;
      }

    public static List<?> convertObjectToList(Object obj) {
        List<?> list = new ArrayList<>();
        if (obj.getClass().isArray()) {
            list = Arrays.asList((Object[]) obj);
        } else if (obj instanceof Collection) {
            list = new ArrayList<>((Collection<?>) obj);
        }
        return list;
    }

    
    
 
    public boolean compareStreams(Stream<Object> stream1, Stream<Object> stream2){
        return stream1.count() == stream2.count() && stream1
               .allMatch(element -> stream2.anyMatch(element2 -> element2.equals(element)));
    }

}
