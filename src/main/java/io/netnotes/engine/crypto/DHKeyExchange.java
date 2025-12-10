package io.netnotes.engine.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;

import javax.crypto.KeyAgreement;

/**
 * Diffie-Hellman Key Exchange handler
 */
public class DHKeyExchange {
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
        
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(CryptoService.DH_ALGORITHM);
        
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
            KeyAgreement keyAgreement = KeyAgreement.getInstance(CryptoService.DH_ALGORITHM);
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