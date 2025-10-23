package io.netnotes.engine.noteFiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import javax.crypto.SecretKey;

import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteRandom;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.utils.JarHelpers;
import io.netnotes.engine.utils.Version;

public class SettingsData {
    public static final String BCRYPT = "bcrypt";
    public static final String SALT = "salt"; 
    public static class InvalidPasswordException extends RuntimeException {
        public InvalidPasswordException(String msg) { super(msg); }
    }

    private static final String SETTINGS_FILE_NAME = "settings.dat";
    public static final File HOME_DIRECTORY = new File(System.getProperty("user.home"));
    public static final File DESKTOP_DIRECTORY = new File(HOME_DIRECTORY + "/Desktop");
    
    private static File m_appDir = null;
    private static File m_appFile = null;
    private static NoteBytesReadOnly m_appHash = null;

    private static Version m_javaVersion = null;


    private SecretKey m_oldKey = null;
    private SecretKey m_secretKey = null;
    private NoteBytes m_oldSalt = null;
    private NoteBytes m_salt = null;
    private NoteBytes m_bcryptKey;

    static{
        try{
            URL classLocation;
        
            classLocation = JarHelpers.getLocation(SettingsData.class);
        
            m_appFile = JarHelpers.urlToFile(classLocation);
            m_appHash = new NoteBytesReadOnly(HashServices.digestFileToBytes(m_appFile, 16));
            m_appDir = m_appFile.getParentFile();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public SettingsData(SecretKey secretKey, NoteBytes salt, NoteBytes bcrypt){
        m_salt = salt;
        m_secretKey = secretKey;
        m_bcryptKey = bcrypt;
    }

    private static File getAppFileDir(){
        return m_appDir;
    }

    private static File getAppDataDir(){
        File dataDir = new File(getAppFileDir().getAbsolutePath() + "/data");
        if(!dataDir.isDirectory()){
            try{
                Files.createDirectory(dataDir.toPath());
            }catch(IOException e){
                throw new RuntimeException("Cannot create data directory", e);
            }
        }
        return dataDir;
    }

    public File getDataDir(){
        return getAppDataDir();
    }

   
    public SecretKey getOldKey(){
        return m_oldKey;
    }

    public NoteBytes oldSalt(){
        return m_oldSalt;
    }

    public void updatePassword(NoteBytesEphemeral oldPassword, NoteBytesEphemeral newPassword) throws InvalidPasswordException, InvalidKeySpecException, NoSuchAlgorithmException, IOException{
        verifyPassword(oldPassword, m_bcryptKey);

        m_oldKey = m_secretKey;
        m_oldSalt = m_salt;

        NoteBytes salt = new NoteBytes(RandomService.getRandomBytes(16));

        NoteBytes bcrypt = HashServices.getBcryptHash(newPassword);
        SecretKey secretKey = CryptoService.createKey(newPassword, salt);

        m_salt = salt;
        m_bcryptKey = bcrypt;
        m_secretKey = secretKey;

        save();
    }

    public NoteBytes getAppHash(){
        return m_appHash;
    }


    public File getAppDir(){
        return m_appDir;
    }

    public File getAppFile(){
        return m_appFile;
    }

    public Version getJavaVersion(){
        return m_javaVersion;
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
        File dataDir = getAppDataDir();
        
        return new File(dataDir.getAbsolutePath() + "/" + SETTINGS_FILE_NAME);
        
    }

    public static void verifyPassword(NoteBytesEphemeral password, NoteBytes bcrypt) throws InvalidPasswordException{
        
        if(!HashServices.verifyBCryptPassword(password, bcrypt)){
            throw new InvalidPasswordException("Password not verified");
        }
    }

    public void save() throws IOException {
        save(m_bcryptKey, m_salt);
    }

    private static void save( NoteBytes bcryptKey, NoteBytes salt)throws IOException{
        File file = getSettingsFile();

        NoteBytesObject obj = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(BCRYPT, bcryptKey),
            new NoteBytesPair(SALT, salt)
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
        return CompletableFuture.supplyAsync(()->{
            try{
                NoteBytes bcrypt = HashServices.getBcryptHash(password);
                NoteBytes salt = new NoteRandom(16);
                SettingsData settingsData = new SettingsData(CryptoService.createKey(password, salt), salt,  bcrypt);
                settingsData.save();
                return settingsData;
            }catch(Exception e){
                throw new CompletionException("Could not create settings", e);
            }
        });
    }

    public static CompletableFuture<SettingsData> readSettings(NoteBytesEphemeral password, ExecutorService service){
        return CompletableFuture.supplyAsync(()->{
            try{
                File settingsFile = getSettingsFile();
            
                if(settingsFile != null && settingsFile.isFile()){
                
                    try(
                        NoteBytesReader reader = new NoteBytesReader(new FileInputStream(settingsFile));    
                    ){
                        NoteBytes nextNoteBytes = null;
                        NoteBytes bcryptKey = null;
                        NoteBytes salt = null;
                        while((nextNoteBytes = reader.nextNoteBytes()) != null){
                            switch(nextNoteBytes.getAsString()){
                                case BCRYPT:
                                    bcryptKey = bcryptKey == null ? reader.nextNoteBytes() : bcryptKey;
                                break;
                                case SALT:
                                    salt = salt == null ? reader.nextNoteBytes() : salt;
                                break;
                            }
                        }
                        verifyPassword(password, bcryptKey);
                        return new SettingsData(CryptoService.createKey(password, salt), salt, bcryptKey);
                    }
                }else{
                    throw new FileNotFoundException("Settings file not found.");
                }
            }catch(Exception e){
                throw new CompletionException("Settings could not be read", e);
            }

        }, service);
    }

    

}
