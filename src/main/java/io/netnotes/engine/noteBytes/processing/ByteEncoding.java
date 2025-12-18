package io.netnotes.engine.noteBytes.processing;

import java.util.Base64;

import org.bouncycastle.util.encoders.Base32;
import org.bouncycastle.util.encoders.Hex;

public class ByteEncoding {
    
    public enum EncodingType{ 
        BASE_16("BASE_16"),
        BASE_32("BASE_32"),
        BASE_64("BASE_64"),
        BASE_64_URL_SAFE("URL_SAFE"),
        UNENCODED("UNENCODED");

        private final String val;

        EncodingType(String val){
            this.val = val;
        }

        public static EncodingType fromString(String string){
            if(string != null){
                for(EncodingType encoding : values()){
                    if(encoding.val.equals(string)){
                        return encoding;
                    }
                }
            }
            return UNENCODED;
        }

        public String getValue(){
            return val;
        }
    }

    public static byte[] encodeBytes(byte[] bytes, EncodingType type){
        switch(type){
            case BASE_16:
                return encodeHex(bytes);
            case BASE_32:
                return encodeBase32(bytes);
            case BASE_64:
                return encodeBase64(bytes);
            case BASE_64_URL_SAFE:
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
        return Base64.getEncoder().withoutPadding().encode(base64);
    }

    public static byte[] encodeUrlSafe(byte[] urlSafe){
        return Base64.getUrlEncoder().withoutPadding().encode(urlSafe);
    }


    public static String encodeString(byte[] bytes, EncodingType type){
        switch(type){
            case BASE_16:
                return encodeHexString(bytes);
            case BASE_32:
                return encodeBase32String(bytes);
            case BASE_64:
                return encodeBase64String(bytes);
            case BASE_64_URL_SAFE:
                return encodeB64UrlSafeString(bytes);
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

    public static String encodeB64UrlSafeString(byte[] urlSafe){
        return Base64.getUrlEncoder().encodeToString(urlSafe);
    }


  


    public static byte[] decodeEncodedString(String string, EncodingType type){
        switch(type){
            case BASE_16:
                return decodeHex(string);
            case BASE_32:
                return decodeBase32(string);
            case BASE_64:
                return decodeBase64(string);
            case BASE_64_URL_SAFE:
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
