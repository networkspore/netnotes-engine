package io.netnotes.engine.core;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.crypto.SecretKey;


import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteRandom;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteFiles.FileStreamUtils;
import io.netnotes.engine.utils.JarHelpers;
import io.netnotes.engine.utils.VirtualExecutors;

public class SettingsData {
    public static final NoteBytes BCRYPT_KEY = new NoteBytes(new byte[]{(byte) 1});
    public static final NoteBytes SALT_KEY = new NoteBytes(new byte[]{(byte) 2}); 
    public static final NoteBytes OLD_BCRYPT_KEY = new NoteBytes(new byte[]{(byte) 3});
    public static final NoteBytes OLD_SALT_KEY = new NoteBytes(new byte[]{(byte) 4}); 

    public static class InvalidPasswordException extends RuntimeException {
        public InvalidPasswordException(String msg) { super(msg); }
    }

    private static final String SETTINGS_FILE_NAME = "settings.dat";


     
    private static File m_appDir = null;
    private static File m_appFile = null;
    
    static {
        try {
            URL classLocation = JarHelpers.getLocation(SettingsData.class);
            m_appFile = JarHelpers.urlToFile(classLocation);
            m_appDir = m_appFile.getParentFile();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static File getBootstrapFile() throws IOException {
        File dataDir = getAppDataDir();
        return new File(dataDir, "bootstrap.dat");
    }
    
    public static File getAppDataDir() {
        File dataDir = new File(m_appDir, "data");
        if (!dataDir.isDirectory()) {
            try {
                Files.createDirectory(dataDir.toPath());
            } catch (IOException e) {
                throw new RuntimeException("Cannot create data directory", e);
            }
        }
        return dataDir;
    }
    
    public static File getAppDir() {
        return m_appDir;
    }
    
    public static File getAppFile() {
        return m_appFile;
    }
    
    public static File getDataDir() {
        return getAppDataDir();
    }
    


    private SecretKey m_oldKey = null;
    private SecretKey m_secretKey = null;
    private NoteBytes m_oldSalt = null;
    private NoteBytes m_oldBcrypt = null;
    private NoteBytes m_salt = null;
    private NoteBytes m_bcryptKey = null;


    public SettingsData(SecretKey secretKey, NoteBytes salt, NoteBytes bcrypt, NoteBytes... oldValues){
        m_salt = salt;
        m_secretKey = secretKey;
        m_bcryptKey = bcrypt;
        if(oldValues != null && oldValues.length > 0){
            m_oldSalt = oldValues[0];
            if(oldValues.length > 1){
                m_oldBcrypt = oldValues[1];
            }
        }
    }

    public boolean hasOldKey() {
        return m_oldKey != null && m_oldSalt != null;
    }

   
    public SecretKey getOldKey(){
        return m_oldKey;
    }

    public NoteBytes oldSalt(){
        return m_oldSalt;
    }

    /**
     * Clear old key/salt (after successful password change completion)
     */
    public void clearOldKey() {
        m_oldKey = null;
        m_oldSalt = null;
        m_oldBcrypt = null;
        try{
            save();
        }catch(IOException e){
            System.err.println("[SettingsData] Old key/salt cleared, but not saved:\n" + e.toString());
            e.printStackTrace();
        }
        System.out.println("[SettingsData] Old key/salt cleared");
    }

    public CompletableFuture<Boolean> verifyPassword(NoteBytesEphemeral password){
        NoteBytesEphemeral copy = password.copy();
        return CompletableFuture.supplyAsync(()->{
            try(copy){
                
                return HashServices.verifyBCryptPassword(password, m_bcryptKey);
            }
        },  VirtualExecutors.getVirtualExecutor());
    }

    public static CompletableFuture<Boolean> verifyPassword(NoteBytesEphemeral password, NoteBytesMap map){
        NoteBytesEphemeral copy = password.copy();
        return CompletableFuture.supplyAsync(()->{
            try(copy){
                NoteBytes bcrypt = map.get(BCRYPT_KEY);
                return HashServices.verifyBCryptPassword(password, bcrypt);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }



    public CompletableFuture<Boolean> verifyOldPassword(NoteBytesEphemeral password){
        NoteBytesEphemeral copy = password.copy();
        return CompletableFuture.supplyAsync(()->{
            try(copy){
                if(HashServices.verifyBCryptPassword(copy, m_oldBcrypt)){
                    m_oldKey = CryptoService.createKey(copy, m_oldSalt);
                    return true;
                }
                return false;
            }catch(Exception e){
                throw new CompletionException("Crypto exception", e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }



    public void updatePassword(NoteBytesEphemeral oldPassword, NoteBytesEphemeral newPassword) throws InvalidPasswordException, InvalidKeySpecException, NoSuchAlgorithmException, IOException{
        if(HashServices.verifyBCryptPassword(oldPassword, m_bcryptKey)){

            m_oldKey = m_secretKey;
            m_oldSalt = m_salt;
            m_oldBcrypt = m_bcryptKey;
            NoteBytes salt = new NoteBytes(RandomService.getRandomBytes(16));

            NoteBytes bcrypt = HashServices.getBcryptHash(newPassword);
            SecretKey secretKey = CryptoService.createKey(newPassword, salt);

            m_salt = salt;
            m_bcryptKey = bcrypt;
            m_secretKey = secretKey;

            save();
        }else{
            throw new InvalidPasswordException("Invalid password");
        }
    }



    /**
     * Rollback to old password state
     * Swaps current key/salt with old key/salt and saves to disk
     * 
     * This is used during recovery when user wants to restore the previous password
     * after a failed password change operation.
     * 
     * Requirements:
     * - m_oldKey and m_oldSalt must be available (non-null)
     * - System must not have been restarted since password change
     * 
     * Process:
     * 1. Verify old key/salt exist
     * 2. Swap: current ↔ old
     * 3. Save to disk (old password becomes active again)
     * 
     * @throws IllegalStateException if old key/salt not available
     * @throws IOException if save fails
     */
    public void rollbackToOldPassword() throws IllegalStateException, IOException {
        if (m_oldKey == null || m_oldSalt == null || m_oldBcrypt == null) {
            throw new IllegalStateException(
                "Cannot rollback: old key and salt not available. " +
                "Rollback is only possible if system hasn't restarted since password change.");
        }
        
        System.out.println("[SettingsData] Rolling back to old password");
        
        // Save current as temporary
        SecretKey tempKey = m_secretKey;
        NoteBytes tempSalt = m_salt;
        NoteBytes tempBcrypt = m_bcryptKey;
        
        // Restore old as current
        m_secretKey = m_oldKey;
        m_salt = m_oldSalt;
        m_bcryptKey = m_oldBcrypt;

        m_oldBcrypt = tempBcrypt;
        m_oldSalt = tempSalt;
        m_oldKey = tempKey;
        save();
    }


    public NoteBytes getOldBCrypt(){
        return m_oldBcrypt;
    }

    public SecretKey getSecretKey(){
        return m_secretKey;
    }

    public void setSecretKey(SecretKey secretKey){
        m_secretKey = secretKey;
    }


    public NoteBytes getBCryptKey() {
        return m_bcryptKey;
    }

    public byte[] getBCryptKeyBytes() {
        return m_bcryptKey.getBytes();
    }

    public void setBCryptKey(NoteBytes hash) throws IOException {
        m_bcryptKey = hash;
    }

    private static File getSettingsFile() throws IOException{
        File dataDir = getDataDir();
        
        return new File(dataDir.getAbsolutePath() + "/" + SETTINGS_FILE_NAME);
        
    }

    public void save() throws IOException {
        if(m_oldBcrypt != null && m_oldSalt != null){
            save( 
                new NoteBytesPair(BCRYPT_KEY, m_bcryptKey),
                new NoteBytesPair(SALT_KEY, m_salt),
                new NoteBytesPair(OLD_BCRYPT_KEY, m_oldBcrypt),
                new NoteBytesPair(OLD_SALT_KEY, m_oldSalt)
            );
        }else{
            save( 
                new NoteBytesPair(BCRYPT_KEY, m_bcryptKey),
                new NoteBytesPair(SALT_KEY, m_salt)
            );
        }
        
    }

    private static void save( NoteBytesPair... pairs)throws IOException{
        File file = getSettingsFile();

        NoteBytesObject obj = new NoteBytesObject(
           
        );

        FileStreamUtils.writeFileBytes(file, obj.get());
    }

    public void shutdown(){

    }

    public static boolean isSettingsData() throws IOException{
        File settingsFile = getSettingsFile();
        if(settingsFile.exists() && settingsFile.isFile()){
            return true;
        }

        return false;
    }

    public static boolean isBootstrapData() throws IOException{
        File bootstrapFile = getBootstrapFile();
        if(bootstrapFile.exists() && bootstrapFile.isFile()){
            return true;
        }

        return false;
    }

    public static CompletableFuture<Void> saveBootstrapConfig( NoteBytesMap map){
        return CompletableFuture.runAsync(()->{
            try {
                saveBootstrapConfig( map.getNoteBytesObject());
            } catch (IOException e) {
                throw new CompletionException("Failed to save", e);
            }

        }, VirtualExecutors.getVirtualExecutor());
    }
    
    public static void saveBootstrapConfig( NoteBytesObject nbo) throws IOException{
        File file = getBootstrapFile();

        FileStreamUtils.writeFileBytes(file, nbo.get());
    }
    

    public static CompletableFuture<SettingsData> createSettings(NoteBytesEphemeral password){
        NoteBytesEphemeral pass = password.copy();

        return CompletableFuture.supplyAsync(()->{
            try{
                if(pass.byteLength() < 6){
                    throw new InvalidPasswordException("Password must be at least 6 characters long");
                }
                NoteBytes bcrypt = HashServices.getBcryptHash(pass);
                NoteBytes salt = new NoteRandom(16);
                SettingsData settingsData = new SettingsData(CryptoService.createKey(pass, salt), salt,  bcrypt);
                settingsData.save();
                return settingsData;
            }catch(Exception e){
                throw new CompletionException("Could not create settings", e);
            }finally{
                pass.close();
            }
        }, VirtualExecutors.getVirtualExecutor());
    }

    public static CompletableFuture<NoteBytesMap> loadSettingsMap(){

        return CompletableFuture.supplyAsync(()->{
            try{
                File settingsFile = getSettingsFile();
                return FileStreamUtils.readFileToMap(settingsFile);
            }catch(Exception e){
                throw new CompletionException("Settings could not be read", e);
            }

        }, VirtualExecutors.getVirtualExecutor());
    }

    public static CompletableFuture<NoteBytesMap> loadBootStrapConfig(){

        return CompletableFuture.supplyAsync(()->{
            try{
                File bootStrapFile = getBootstrapFile();
                return FileStreamUtils.readFileToMap(bootStrapFile);
            }catch(Exception e){
                throw new CompletionException("Settings could not be read", e);
            }

        }, VirtualExecutors.getVirtualExecutor());
    }


    public static CompletableFuture<SettingsData> loadSettingsData(NoteBytesEphemeral pass, NoteBytesMap map){
        final NoteBytesEphemeral password = pass.copy();
        return CompletableFuture.supplyAsync(()->{
            try(password){
                NoteBytes bcryptKey = map.get(BCRYPT_KEY);
                NoteBytes salt = map.get(SALT_KEY);
                NoteBytes oldSalt = map.get(OLD_SALT_KEY);
                NoteBytes oldBcrypt = map.get(OLD_BCRYPT_KEY);
                if(salt != null && bcryptKey != null){
                    if(oldSalt != null){
                        if(oldBcrypt != null){
                            return new SettingsData(CryptoService.createKey(password, salt), salt, bcryptKey, oldSalt, oldBcrypt);
                        }else{
                            return new SettingsData(CryptoService.createKey(password, salt), salt, bcryptKey, oldSalt);
                        }
                    }else{
                        return new SettingsData(CryptoService.createKey(password, salt), salt, bcryptKey);
                    }
                }else{
                    String saltString = salt == null ? "Salt unavailable file is corrupt" : "";
                    String bcryptString = bcryptKey == null ? "Key is unavailable file is corrupt" : "";
                    throw new NullPointerException(bcryptString + ", " + saltString);
                }
            }catch(Exception e){
                  throw new CompletionException(e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }

}
