package io.netnotes.engine.noteFiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKey;
import javax.security.auth.DestroyFailedException;

import org.bouncycastle.crypto.RuntimeCryptoException;

import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.crypto.HashData;
import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteRandom;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.utils.Utils;
import io.netnotes.engine.utils.Version;

public class SettingsData {

    private static final String SETTINGS_FILE_NAME = "settings.dat";
    public static final File HOME_DIRECTORY = new File(System.getProperty("user.home"));
    public static final File DESKTOP_DIRECTORY = new File(HOME_DIRECTORY + "/Desktop");
    
    private static final int m_saltLength = 16;
    private static File m_appDir = null;
    private static File m_appFile = null;
    private static HashData m_appHashData = null;

    private static Version m_javaVersion = null;


    private SecretKey m_oldKey = null;
    private SecretKey m_secretKey = null;
    private NoteBytes m_bcryptKey;
    private NoteBytes m_salt = null;

    static{
        try{
            URL classLocation;
        
            classLocation = Utils.getLocation(SettingsData.class);
        
            m_appFile = Utils.urlToFile(classLocation);
            m_appHashData = new HashData(m_appFile);
            m_appDir = m_appFile.getParentFile();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public SettingsData(SecretKey secretKey, NoteBytes bcrypt, NoteBytes salt){
   

        m_secretKey = secretKey;
        m_bcryptKey = bcrypt;
        m_salt = salt;
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

    public void updatePassword(NoteBytesEphemeral oldPassword, NoteBytesEphemeral newPassword) throws VerifyError, InvalidKeySpecException, NoSuchAlgorithmException, IOException{
   
        NoteBytes bcrypt = HashServices.getBcryptHash(newPassword);
        NoteBytes salt = new NoteRandom(m_saltLength);
        SecretKey secretKey = CryptoService.createKey(newPassword, salt);
      
        m_salt = salt;
        m_bcryptKey = bcrypt;
        m_secretKey = secretKey;

        save();
    }

    public HashData getAppHashData(){
        return m_appHashData;
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



     public NoteBytes getSalt(){
        return m_salt;
    }

    public void setSalt(NoteBytes salt){
        m_salt = salt;
    }

    public SecretKey getSecretKey(){
        return m_secretKey;
    }

    public void setSecretKey(SecretKey secretKey){
        m_secretKey = secretKey;
    }


    public NoteBytes getAppKey() {
        return m_bcryptKey;
    }

    public byte[] getAppKeyBytes() {
        return m_bcryptKey.getBytes();
    }

    public void setAppKey(NoteBytes hash) throws IOException {
        m_bcryptKey = hash;
    }

    private static File getSettingsFile() throws IOException{
        File dataDir = getAppDataDir();
        
        return new File(dataDir.getAbsolutePath() + "/" + SETTINGS_FILE_NAME);
        
    }

    public static void verifyPassword(NoteBytesEphemeral password, NoteBytes bcrypt) throws VerifyError{
        
        if(!HashServices.verifyBCryptPassword(password, bcrypt)){
            throw new VerifyError("Password not verified");
        }
    }

    public void save() throws IOException {
        save(m_bcryptKey, m_salt);
    }

    private static void save( NoteBytes bcryptKey, NoteBytes salt)throws IOException{
           File file = getSettingsFile();
        NoteBytesObject obj = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("appKey", bcryptKey),
            new NoteBytesPair("salt", salt)
        });
        FileStreamUtils.writeFileBytes(file, obj.get());
    }

    public void shutdown(){

        try {
            getSecretKey().destroy();
        } catch (DestroyFailedException e) {
            Utils.writeLogMsg("NetworsData.onClosing", "Cannot destroy");
        }
    }


    public static SettingsData createSettings(NoteBytesEphemeral password) throws InvalidKeySpecException, NoSuchAlgorithmException, IOException{
        NoteBytes bcrypt = HashServices.getBcryptHash(password);
        NoteBytes salt = new NoteRandom(m_saltLength);
        SettingsData settingsData = new SettingsData(CryptoService.createKey(password, salt), bcrypt, salt);
        settingsData.save();
        return settingsData;
    }

    public static SettingsData readSettings(NoteBytesEphemeral password)throws VerifyError,FileNotFoundException, IOException{
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
                        case "appKey":
                            bcryptKey = bcryptKey == null ? reader.nextNoteBytes() : bcryptKey;
                        break;
                     
                        case "salt":
                            salt = salt == null ? reader.nextNoteBytes() : salt;
                        break;
                    }
                }
               
                verifyPassword(password, bcryptKey);

                return new SettingsData(CryptoService.createKey(password, salt), bcryptKey, salt);
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                throw new RuntimeCryptoException("Specification error");
            } 
            
        }else{
            throw new FileNotFoundException("Settings file not found.");
        }

    
    }

}
