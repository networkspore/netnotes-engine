package io.netnotes.engine.crypto;

import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM encryption session
 */
public class EncryptedSession {
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
        this.secretKey = new SecretKeySpec(sharedSecret, CryptoService.AES_ALGORITHM);
        
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
        
        Cipher cipher = CryptoService.getAESDecryptCipher(iv, secretKey);
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
        Cipher cipher = CryptoService.getAESEncryptCipher(iv, secretKey);
        
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