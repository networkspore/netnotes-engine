package io.netnotes.engine.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;


import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public class CryptoService {
    public static final int CHACHA20_KEY_SIZE = 32; // 256 bits
    public static final int CHACHA20_NONCE_SIZE = 12; // 96 bits for ChaCha20-Poly1305
    public static final int AES_IV_SIZE = 12;

    public static final String AES_GCM_NO_PADDING ="AES/GCM/NoPadding";
    public static final String CHA_CHA_20_POLY_1305 = "ChaCha20-Poly1305";

    public static final NoteBytesReadOnly AES_GCM_NO_PADDING_ALGORITHM = new NoteBytesReadOnly(AES_GCM_NO_PADDING);
    public static final NoteBytesReadOnly CHA_CHA_20_POLY_1305_ALGORITHM = new NoteBytesReadOnly(CHA_CHA_20_POLY_1305);

 

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static Cipher getAESEncryptCipher(byte[] iV, SecretKey secretKey) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException{
        return getAESCipher(iV, secretKey, Cipher.ENCRYPT_MODE);
    }

    public static Cipher getAESDecryptCipher(byte[] iV, SecretKey secretKey) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException{
        return getAESCipher(iV, secretKey, Cipher.DECRYPT_MODE);
    }

    public static Cipher getAESCipher(byte[] iV, SecretKey secretKey, int mode) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException{
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);
        cipher.init(mode, secretKey, parameterSpec);
        return cipher;
    }


    public static byte[] deriveHKDFKey(byte[] sharedSecret, NoteBytesReadOnly info, int keyLength) throws Exception {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(sharedSecret, null, info.getBytes()));
        byte[] derivedKey = new byte[keyLength];
        hkdf.generateBytes(derivedKey, 0, keyLength);
        return derivedKey;
    }

    public static byte[] deriveHKDFKey(byte[] sharedSecret, byte[] salt, NoteBytesReadOnly info, int keyLength) throws Exception {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(sharedSecret, salt, info.getBytes()));
        byte[] derivedKey = new byte[keyLength];
        hkdf.generateBytes(derivedKey, 0, keyLength);
        return derivedKey;
    }

    
    public static SecretKeySpec createKey(NoteBytes password, NoteBytes salt) throws InvalidKeySpecException, NoSuchAlgorithmException {
        try(NoteBytesEphemeral encoded = createPBKDF2Key(password, salt)){
            return new SecretKeySpec(encoded.get(), "AES");
        }
    }

    public static NoteBytesEphemeral createPBKDF2Key(NoteBytes password, NoteBytes salt) throws InvalidKeySpecException, NoSuchAlgorithmException  {
        return new NoteBytesEphemeral(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.getChars(), salt.get(), 65536, 256)).getEncoded());
    }



    public static byte[] getX245519SharedSecret(X25519PrivateKeyParameters ephemeralPrivate, X25519PublicKeyParameters recipientKey, int keySize){
        X25519Agreement agreement = new X25519Agreement();
        agreement.init(ephemeralPrivate);
        byte[] sharedSecret = new byte[keySize];
        agreement.calculateAgreement(recipientKey, sharedSecret, 0);
        return sharedSecret;
    }
    


    public static Cipher getChaCha20Poly1305Cipher(NoteBytesEphemeral key, NoteBytes nonce, int mode) throws Exception{
        Cipher cipher = Cipher.getInstance(CryptoService.CHA_CHA_20_POLY_1305, "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "ChaCha20");
        ChaCha20ParameterSpec paramSpec = new ChaCha20ParameterSpec(nonce.getBytes(), 1);
        cipher.init(mode, keySpec, paramSpec);
        return cipher;
    }


   
}