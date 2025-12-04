package io.netnotes.engine.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
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
    public static final String BOUNCY_CASTLE_PROVIDER = "BC";

    public static final int CHACHA20_KEY_SIZE = 32; // 256 bits
    public static final int CHACHA20_NONCE_SIZE = 12; // 96 bits for ChaCha20-Poly1305
    public static final int AES_IV_SIZE = 12;
    public static final int GCM_TAG_LENGTH = 128; 

    public static final String CHA_CHA_20_POLY_1305 = "ChaCha20-Poly1305";
    public static final String DH_ALGORITHM = "DH";
    public static final String AES_ALGORITHM = "AES";
    public static final String AES_GCM_NO_PADDING ="AES/GCM/NoPadding";
    

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
        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iV);
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
         SecureRandom.getInstanceStrong().generateSeed(16);
         RandomService.getRandomBytes(16);
        try(NoteBytesEphemeral encoded = createPBKDF2Key(password, salt)){
            return new SecretKeySpec(encoded.get(), "AES");
        }
    }

    public static NoteBytesEphemeral createPBKDF2Key(NoteBytes password, NoteBytes salt) throws InvalidKeySpecException, NoSuchAlgorithmException  {
        return new NoteBytesEphemeral(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.getAsChars(), salt.get(), 65536, 256)).getEncoded());
    }

      /**
     * Set the peer's public key from encoded bytes
     * @throws NoSuchAlgorithmException 
     * @throws InvalidKeySpecException 
     */
    public static PublicKey createDHPubKey(byte[] encodedKey) throws NoSuchAlgorithmException, InvalidKeySpecException{
        KeyFactory keyFactory = KeyFactory.getInstance(DH_ALGORITHM);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
        return keyFactory.generatePublic(keySpec);
    }
        


    public static byte[] getX245519SharedSecret(X25519PrivateKeyParameters ephemeralPrivate, X25519PublicKeyParameters recipientKey, int keySize){
        X25519Agreement agreement = new X25519Agreement();
        agreement.init(ephemeralPrivate);
        byte[] sharedSecret = new byte[keySize];
        agreement.calculateAgreement(recipientKey, sharedSecret, 0);
        return sharedSecret;
    }
    


    public static Cipher getChaCha20Poly1305Cipher(NoteBytesEphemeral key, NoteBytes nonce, int mode) throws Exception{
        Cipher cipher = Cipher.getInstance(CryptoService.CHA_CHA_20_POLY_1305, BOUNCY_CASTLE_PROVIDER);
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "ChaCha20");
        ChaCha20ParameterSpec paramSpec = new ChaCha20ParameterSpec(nonce.getBytes(), 1);
        cipher.init(mode, keySpec, paramSpec);
        return cipher;
    }



     /**
     * Diffie-Hellman Key Exchange handler
     */
    public static class DHKeyExchange {
        private KeyPair keyPair;
        private PublicKey peerPublicKey;
        private byte[] sharedSecret;
        
        /**
         * Generate DH key pair
         * Uses standard 2048-bit parameters
         */
        public void generateKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
            // Use standard 2048-bit DH parameters (RFC 3526 Group 14)
            // p (prime modulus) - 2048-bit MODP Group
            // g (generator) = 2
            
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(DH_ALGORITHM);
            
            // For production, you should use the exact same prime and generator as the C++ side
            // This is a simplified version using Java's default parameters
            keyPairGen.initialize(2048);
            
            this.keyPair = keyPairGen.generateKeyPair();
        }
        
        /**
         * Get our public key in X.509 encoded format
         */
        public byte[] getPublicKeyEncoded() {
            if (keyPair == null) {
                throw new IllegalStateException("Key pair not generated");
            }
            return keyPair.getPublic().getEncoded();
        }
        
      
        /**
         * Derive the shared secret
         */
        public boolean deriveSharedSecret() {
            if (keyPair == null || peerPublicKey == null) {
                return false;
            }
            
            try {
                KeyAgreement keyAgreement = KeyAgreement.getInstance(DH_ALGORITHM);
                keyAgreement.init(keyPair.getPrivate());
                keyAgreement.doPhase(peerPublicKey, true);
                
                byte[] rawSecret = keyAgreement.generateSecret();
                
                // Hash the shared secret with SHA-256 to get 256-bit key
                // This matches the C++ implementation
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                this.sharedSecret = digest.digest(rawSecret);
                
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        
        /**
         * Get the derived shared secret (32 bytes)
         */
        public byte[] getSharedSecret() {
            if (sharedSecret == null) {
                throw new IllegalStateException("Shared secret not derived");
            }
            return sharedSecret.clone();
        }
        
        /**
         * Clear sensitive data
         */
        public void clear() {
            if (sharedSecret != null) {
                Arrays.fill(sharedSecret, (byte) 0);
                sharedSecret = null;
            }
            keyPair = null;
            peerPublicKey = null;
        }

        public void setPeerPublicKey(PublicKey pubKey){
            peerPublicKey = pubKey;   
        }


        public void setPeerPublicKey(byte[] bytes) throws Exception{
            setPeerPublicKey(CryptoService.createDHPubKey(bytes));
        }
        
    }
    
    /**
     * AES-GCM encryption session
     */
    public static class EncryptedSession {
        private SecretKey secretKey;
        private byte[] iv;
        
        /**
         * Initialize with shared secret
         */
        public void init(byte[] sharedSecret) throws Exception {
            if (sharedSecret.length != 32) {
                throw new IllegalArgumentException("Shared secret must be 32 bytes");
            }
            
            // Create AES key from shared secret
            this.secretKey = new SecretKeySpec(sharedSecret, AES_ALGORITHM);
            
            // Generate initial IV
            this.iv = RandomService.getRandomBytes(CryptoService.AES_IV_SIZE);
        }
        
        /**
         * Get current IV (to send to peer if needed)
         */
        public byte[] getIV() {
            return iv.clone();
        }
        
        /**
         * Set IV from peer
         */
        public void setIV(byte[] iv) {
            if (iv.length != CryptoService.AES_IV_SIZE) {
                throw new IllegalArgumentException("IV must be " + CryptoService.AES_IV_SIZE + " bytes");
            }
            this.iv = iv.clone();
        }

      
        /**
         * Decrypt a packet from daemon
         * Input: ciphertext + 16-byte authentication tag
         * Output: plaintext
         */
        public byte[] decrypt(byte[] ciphertext) throws Exception  {
            if (ciphertext.length < 16) {
                throw new IllegalArgumentException("Ciphertext too short (needs at least 16 bytes for tag)");
            }
            
            Cipher cipher = getAESDecryptCipher(iv, secretKey);
            byte[] plaintext = cipher.doFinal(ciphertext);
            
            // Increment IV counter
            incrementIV();
            
            return plaintext;
        }
        
        /**
         * Encrypt a packet to send to daemon
         * Output: ciphertext + 16-byte authentication tag
         */
        public byte[] encrypt(byte[] plaintext) throws Exception {
            Cipher cipher = getAESEncryptCipher(iv, secretKey);
            
            byte[] ciphertext = cipher.doFinal(plaintext);
            
            // Increment IV counter
            incrementIV();
            
            return ciphertext;
        }
        
        /**
         * Increment IV as a counter (matches C++ implementation)
         */
        private void incrementIV() {
            for (int i = iv.length - 1; i >= 0; i--) {
                iv[i]++;
                if (iv[i] != 0) break; // No carry needed
            }
        }
        
        /**
         * Clear sensitive data
         */
        public void clear() {
            if (iv != null) {
                Arrays.fill(iv, (byte) 0);
                iv = null;
            }
            secretKey = null;
        }
    }
    
   
}