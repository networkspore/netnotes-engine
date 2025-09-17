package io.netnotes.engine.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;


public class RandomService {
    public static final int PRINTABLE_CHAR_RANGE_START = 32;
    public static final int PRINTABLE_CHAR_RANGE_END = 127;
    private static final SecureRandom m_secureRandom = new SecureRandom();

    public static byte[] getIV() throws NoSuchAlgorithmException{
       
        byte[] iV = new byte[12];
        m_secureRandom.nextBytes(iV);
        return iV;
    }

    public static SecureRandom getSecureRandom(){
        return m_secureRandom;
    }
  
    public static char[] getCharRange(int rangeStart, int rangeEnd){
        char[] chars = new char[rangeEnd - rangeStart];
        int j = 0;
        int i = 32;
        while(i < 127){
            chars[j] = (char) i;
            j++;
            i++;
        }

        return chars;
    }

    public final static char[] getAsciiCharArray(){
        char[] chars = new char[128];

        for(int i = 0; i < 128; i++){
            chars[i] = (char) i;
        }

        return chars;
    }

    public static char[] getPrintableCharArray(){
        return getCharRange(PRINTABLE_CHAR_RANGE_START, PRINTABLE_CHAR_RANGE_END);
    }

    public static int getRandomInt(int min, int max) {

        return m_secureRandom.nextInt(min, max);
    }



    public static String getRandomString(int length){
        char[] chars = new char[length];
        fillCharArray(chars);
        return new String(chars);
    
    }

    public static void fillCharArray(char[] charArray) {
        fillCharArray(charArray, getPrintableCharArray());
    }

    public static void fillCharArray(char[] charArray, char[] chars){
        if(charArray != null && charArray.length > 0 && chars != null && chars.length > 0){

            for(int i = 0; i < charArray.length ; i++){
                charArray[i] = chars[getRandomInt(0, chars.length)];
            }
        }
    }


    public static byte[] getRandomBytes(int size){
        byte[] randomBytes = new byte[size];
        m_secureRandom.nextBytes(randomBytes);
        return randomBytes;
    }




}


