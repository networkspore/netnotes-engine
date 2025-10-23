package io.netnotes.engine.noteBytes.processing;

import java.util.Base64;

import org.bouncycastle.util.encoders.Base32;
import org.bouncycastle.util.encoders.Hex;

public class EncodingHelpers {
    
    public enum Encoding{ 
        BASE_16(255),
        BASE_32(254),
        BASE_64(253),
        URL_SAFE(252),
        UNENCODED(0);

        private final int val;

        Encoding(int val){
            this.val = val;
        }

        public static Encoding fromValue(int value){
            for(Encoding encoding : values()){
                if(encoding.val == value){
                    return encoding;
                }
            }
            return UNENCODED;
        }
    }

    public static byte[] encodeBytes(byte[] bytes, Encoding type){
        switch(type){
            case BASE_16:
                return encodeHex(bytes);
            case BASE_32:
                return encodeBase32(bytes);
            case BASE_64:
                return encodeBase64(bytes);
            case URL_SAFE:
                return encodeUrlSafe(bytes);
            default:
                return bytes;
        }
    }

      public static byte[] encodeHex(byte[] hex){
        return Hex.encode(hex);
    }

    public static byte[] encodeBase32(byte[] base32){
        return Base32.encode(base32);
    }

    public static byte[] encodeBase64(byte[] base64){
        return Base64.getEncoder().encode(base64);
    }

    public static byte[] encodeUrlSafe(byte[] urlSafe){
        return Base64.getUrlEncoder().encode(urlSafe);
    }


    public static String encodeString(byte[] bytes, Encoding type){
        switch(type){
            case BASE_16:
                return encodeHexString(bytes);
            case BASE_32:
                return encodeBase32String(bytes);
            case BASE_64:
                return encodeBase64String(bytes);
            case URL_SAFE:
                return encodeUrlSafeString(bytes);
            default:
                return new String(bytes);
        }
    }

    public static String encodeHexString(byte[] hex){
        return Hex.toHexString(hex);
    }

    public static String encodeBase32String(byte[] base32){
        return Base32.toBase32String(base32);
    }

    public static String encodeBase64String(byte[] base64){
        return Base64.getEncoder().encodeToString(base64);
    }

    public static String encodeUrlSafeString(byte[] urlSafe){
        return Base64.getUrlEncoder().encodeToString(urlSafe);
    }


  


    public static byte[] decodeEncodedString(String string, Encoding type){
        switch(type){
            case BASE_16:
                return decodeHex(string);
            case BASE_32:
                return decodeBase32(string);
            case BASE_64:
                return decodeBase64(string);
            case URL_SAFE:
                return decodeUrlSafe(string);
            default:
                return string.getBytes();
        }
        
    }

    public static byte[] decodeHex(String hex){
        return Hex.decode(hex);
    }

    public static byte[] decodeBase32(String base32){
        return Base32.decode(base32);
    }

    public static byte[] decodeBase64(String base64){
        return Base64.getDecoder().decode(base64);
    }

    public static byte[] decodeUrlSafe(String urlSafe){
        return Base64.getUrlDecoder().decode(urlSafe);
    }

}
