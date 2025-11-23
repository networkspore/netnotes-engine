package io.netnotes.engine.io.daemon;


import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.io.ContextPath;

/**
 * DeviceEncryptionSession - Per-device encryption handler
 * 
 * Wraps CryptoService to provide device-specific encryption state.
 * Matches C++ EncryptionHandshake protocol.
 * 
 * Flow:
 * 1. Daemon sends ENCRYPTION_OFFER with server_public_key
 * 2. Client calls acceptOffer() - generates key pair, derives shared secret
 * 3. Client sends ENCRYPTION_ACCEPT with client_public_key
 * 4. Daemon sends ENCRYPTION_READY with IV
 * 5. Client calls finalizeEncryption() - session is active
 * 6. All subsequent device events are encrypted/decrypted with this session
 */
public class DeviceEncryptionSession {
    
    private final String deviceId;
    private final ContextPath devicePath;
    
    private CryptoService.DHKeyExchange keyExchange;
    private CryptoService.EncryptedSession encryptedSession;
    
    private volatile boolean active = false;
    
    public DeviceEncryptionSession(String deviceId, ContextPath devicePath) {
        this.deviceId = deviceId;
        this.devicePath = devicePath;
    }
    
    /**
     * Accept encryption offer from daemon
     * Generates our key pair and derives shared secret
     */
    public void acceptOffer(byte[] serverPublicKey, String cipher) throws Exception {
        if (!cipher.equals("aes-256-gcm")) {
            throw new IllegalArgumentException("Unsupported cipher: " + cipher);
        }
        
        // Generate DH key pair
        keyExchange = new CryptoService.DHKeyExchange();
        keyExchange.generateKeyPair();
        
        // Set peer's public key
        keyExchange.setPeerPublicKey(serverPublicKey);
        
        // Derive shared secret
        if (!keyExchange.deriveSharedSecret()) {
            throw new Exception("Failed to derive shared secret");
        }
        
        System.out.println("Encryption keys exchanged for device: " + devicePath);
    }
    
    /**
     * Finalize encryption with daemon's IV
     * After this, session is active
     */
    public void finalizeEncryption(byte[] serverIV) throws Exception {
        if (keyExchange == null) {
            throw new IllegalStateException("Key exchange not completed");
        }
        
        // Create encrypted session
        encryptedSession = new CryptoService.EncryptedSession();
        encryptedSession.init(keyExchange.getSharedSecret());
        encryptedSession.setIV(serverIV);
        
        // Clear key exchange (no longer needed)
        keyExchange.clear();
        keyExchange = null;
        
        active = true;
        System.out.println("Encryption session active for device: " + devicePath);
    }
    
    /**
     * Get our public key to send to daemon
     */
    public byte[] getPublicKey() {
        if (keyExchange == null) {
            throw new IllegalStateException("Key exchange not started");
        }
        return keyExchange.getPublicKeyEncoded();
    }
    
    /**
     * Decrypt device event from daemon
     */
    public byte[] decrypt(byte[] ciphertext) throws Exception {
        if (!active || encryptedSession == null) {
            throw new IllegalStateException("Encryption session not active");
        }
        return encryptedSession.decrypt(ciphertext);
    }
    
    /**
     * Encrypt data to send to daemon
     * (Currently not used - device events flow daemon → client only)
     */
    public byte[] encrypt(byte[] plaintext) throws Exception {
        if (!active || encryptedSession == null) {
            throw new IllegalStateException("Encryption session not active");
        }
        return encryptedSession.encrypt(plaintext);
    }
    
    /**
     * Check if session is active
     */
    public boolean isActive() {
        return active;
    }
    
    /**
     * Clear sensitive data
     */
    public void clear() {
        active = false;
        
        if (keyExchange != null) {
            keyExchange.clear();
            keyExchange = null;
        }
        
        if (encryptedSession != null) {
            encryptedSession.clear();
            encryptedSession = null;
        }
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public ContextPath getDevicePath() {
        return devicePath;
    }
    
    @Override
    public String toString() {
        return String.format("DeviceEncryptionSession{sourceId=%d, path=%s, active=%s}",
            deviceId, devicePath, active);
    }
}