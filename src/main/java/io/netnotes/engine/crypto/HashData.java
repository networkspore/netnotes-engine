package io.netnotes.engine.crypto;

import java.io.File;
import java.io.IOException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesPair;
import io.netnotes.engine.noteBytes.NoteHex;
import io.netnotes.engine.noteBytes.NoteUUID;
public class HashData {

    public static String DEFAULT_HASH = "Blake2b-256";

    private NoteBytes m_id;
    private String m_name = DEFAULT_HASH;
    private NoteBytes m_hashBytes = null;

    public HashData(File file) throws  IOException{
        m_id = NoteUUID.createLocalUUID128();
        m_hashBytes = new NoteBytes(HashServices.digestFile(file));
    }

    public HashData(byte[] bytes) {
        m_id = NoteUUID.createLocalUUID128();
        m_hashBytes =  new NoteBytes(bytes);
    }

    public HashData(NoteBytesObject nbo)  {
        init(nbo);
    }

    public HashData(NoteBytes id, NoteBytes hash){
        m_id = id;
        m_hashBytes =  hash;
    }

    public HashData(NoteBytes hashId, String name, NoteBytes hash) {

        m_id = hashId;
        m_name = name;
        m_hashBytes = hash;

    }

    public HashData(JsonObject json){
        openJson(json);
    }


    public void init(NoteBytesObject nbo)  {

        NoteBytesPair idElement = nbo.get("id");
        NoteBytesPair nameElement = nbo.get("name");
        NoteBytesPair hashStringElement = nbo.get("hash");

        m_id = idElement != null ? idElement.getValue() : NoteUUID.createLocalUUID128();         
        
        if (nameElement != null) {
            m_name = nameElement.getAsString();
          
        }
        if (hashStringElement != null) {
           m_hashBytes = hashStringElement.getValue();
        }

    }

    public void openJson(JsonObject json)  {

        JsonElement idElement = json.get("id");
        JsonElement nameElement = json.get("name");
        JsonElement hashStringElement = json.get("hash");

        m_id = idElement != null ? NoteUUID.fromURLSafeString(idElement.getAsString()) : NoteUUID.createLocalUUID128();         
        
        if (nameElement != null) {
            m_name = nameElement.getAsString();
          
        }
        if (hashStringElement != null) {
           m_hashBytes = new NoteHex(idElement.getAsString());
        }

    }

    public void setHashHex(String hex){
        m_hashBytes = new NoteHex(hex);
    }

    public String getHashStringHex(){
        return m_hashBytes.getAsHexString();
    }
    
    public HashData(JsonReader reader) throws IOException{
        reader.beginObject();
        while(reader.hasNext()){
            switch(reader.nextName()){
                case "id":
                    m_id = NoteUUID.fromURLSafeString(reader.nextString());
                break;
                case "name":
                    m_name = reader.nextString();
                break;
                case "hash":
                    setHashHex(reader.nextString());
                break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }

    public void writeJson(JsonWriter writer) throws IOException{
        writer.beginObject();
        writer.name("id");
        writer.value(getId());
        writer.name("name");
        writer.value(getHashName());
        writer.name("hash");
        writer.value(getHashStringHex());
        writer.endObject();
    }


    public String getId() {
        return m_id.getAsUrlSafeString();
    }

    public NoteBytes getUUID(){
        return m_id;
    }

    public String getHashName() {
        return m_name;
    }

 
    public NoteBytes getHashBytes() {
        return m_hashBytes;
    }




    public void setHash(NoteBytes hashBytes) {
        m_hashBytes = hashBytes;
    }

    public NoteBytesObject getNoteBytesObject() {
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add("id", m_id);
        nbo.add("name", m_name);
        if (m_hashBytes != null) {
            nbo.add("hash", m_hashBytes);
        }
        return nbo;
    }

    public JsonObject getJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("id", getId());
        json.addProperty("name", getHashName());
        if (m_hashBytes != null) {
            json.addProperty("hash", getHashStringHex());
        }
        return json;
    }
}
