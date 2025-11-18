package io.netnotes.engine.core;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import javax.crypto.SecretKey;

import io.netnotes.engine.core.bootstrap.BootstrapManager;
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

public class SettingsData {
    public static final NoteBytes BCRYPT_KEY = new NoteBytes(new byte[]{(byte) 1});
    public static final NoteBytes SALT_KEY = new NoteBytes(new byte[]{(byte) 2}); 
    public static class InvalidPasswordException extends RuntimeException {
        public InvalidPasswordException(String msg) { super(msg); }
    }

    private static final String SETTINGS_FILE_NAME = "settings.dat";

    


    private SecretKey m_oldKey = null;
    private SecretKey m_secretKey = null;
    private NoteBytes m_oldSalt = null;
    private NoteBytes m_salt = null;
    private NoteBytes m_bcryptKey;


    public SettingsData(SecretKey secretKey, NoteBytes salt, NoteBytes bcrypt){
        m_salt = salt;
        m_secretKey = secretKey;
        m_bcryptKey = bcrypt;
    }


   
    public SecretKey getOldKey(){
        return m_oldKey;
    }

    public NoteBytes oldSalt(){
        return m_oldSalt;
    }

    public static CompletableFuture<Boolean> verifyPassword(NoteBytesEphemeral password, NoteBytesMap map, ExecutorService executorService ){
        NoteBytesEphemeral copy = password.copy();
        return CompletableFuture.supplyAsync(()->{
            try(copy){
                NoteBytes bcrypt = map.get(BCRYPT_KEY);
                return HashServices.verifyBCryptPassword(password, bcrypt);
            }
        }, executorService);
    }

    public void updatePassword(NoteBytesEphemeral oldPassword, NoteBytesEphemeral newPassword) throws InvalidPasswordException, InvalidKeySpecException, NoSuchAlgorithmException, IOException{
        if(HashServices.verifyBCryptPassword(oldPassword, m_bcryptKey)){

            m_oldKey = m_secretKey;
            m_oldSalt = m_salt;

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
        File dataDir = BootstrapManager.getDataDir();
        
        return new File(dataDir.getAbsolutePath() + "/" + SETTINGS_FILE_NAME);
        
    }

    public void save() throws IOException {
        save(m_bcryptKey, m_salt);
    }

    private static void save( NoteBytes bcryptKey, NoteBytes salt)throws IOException{
        File file = getSettingsFile();

        NoteBytesObject obj = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(BCRYPT_KEY, bcryptKey),
            new NoteBytesPair(SALT_KEY, salt)
        });

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


    public static CompletableFuture<SettingsData> createSettings(NoteBytesEphemeral password, ExecutorService service){
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
        });
    }

    public static CompletableFuture<NoteBytesMap> loadSettingsMap(ExecutorService service){

        return CompletableFuture.supplyAsync(()->{
            try{
                File settingsFile = getSettingsFile();
                return FileStreamUtils.readFileToMap(settingsFile);
            }catch(Exception e){
                throw new CompletionException("Settings could not be read", e);
            }

        }, service);
    }

    public static CompletableFuture<SettingsData> loadSettingsData(NoteBytesEphemeral pass, NoteBytesMap map, ExecutorService executor){
        final NoteBytesEphemeral password = pass.copy();
        return CompletableFuture.supplyAsync(()->{
            try(password){
                NoteBytes bcryptKey = map.get(BCRYPT_KEY);
                NoteBytes salt = map.get(SALT_KEY);
                if(salt != null && bcryptKey != null){
                     
                    return new SettingsData(CryptoService.createKey(password, salt), salt, bcryptKey);
                }else{
                    String saltString = salt == null ? "Salt unavailable file is corrupt" : "";
                    String bcryptString = bcryptKey == null ? "Key is unavailable file is corrupt" : "";
                    throw new NullPointerException(bcryptString + ", " + saltString);
                }
            }catch(Exception e){
                  throw new CompletionException(e);
            }
        }, executor);
    }

}
