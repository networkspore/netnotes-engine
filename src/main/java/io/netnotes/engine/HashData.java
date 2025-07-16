package io.netnotes.engine;

import java.io.File;
import java.io.IOException;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;


public class HashData {

    public static String DEFAULT_HASH = "Blake2b-256";

    private NoteBytes m_id;
    private String m_name = DEFAULT_HASH;
    private byte[] m_hashBytes = null;

    public HashData(File file) throws  IOException{
        m_id = NoteUUID.createLocalUUID128();
        m_hashBytes = Utils.digestFile(file);
    }

    public HashData(byte[] bytes) {
        m_id = NoteUUID.createLocalUUID128();
        m_hashBytes = bytes;
    }

    public HashData(JsonObject json)  {
        openJson(json);
    }

    public HashData(NoteBytes id, byte[] bytes){
        m_id = id;
        m_hashBytes = bytes;
    }

    public HashData(NoteBytes hashId, String name, String hashHex) {

        m_id = hashId;
        m_name = name;
        setHashHex(hashHex);

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
        writer.value(m_id.getAsUrlSafeString());
        writer.name("name");
        writer.value(m_name);
        writer.name("hash");
        writer.value(getHashStringHex());
        writer.endObject();
    }

    public void openJson(JsonObject json)  {
        /*json.addProperty("id", m_id);
        json.addProperty("name", m_name);
        if (m_hashBytes != null) {
            json.addProperty("hash", getHashString());
        }*/
        JsonElement idElement = json.get("id");
        JsonElement nameElement = json.get("name");
        JsonElement hashStringElement = json.get("hash");

        if (idElement != null && idElement.isJsonPrimitive()) {
            m_id = NoteUUID.fromURLSafeString(idElement.getAsString());
            
        }
        if (nameElement != null && nameElement.isJsonPrimitive()) {
            m_name = nameElement.getAsString();
          
        }
        if (hashStringElement != null && hashStringElement.isJsonPrimitive()) {
            setHashHex(hashStringElement.getAsString());
         
        }

    }

    public NoteBytes getId() {
        return m_id;
    }

    public String getHashName() {
        return m_name;
    }

 
    public String getHashStringHex() {
       
        return  Hex.encodeHexString(m_hashBytes);
    }

    public byte[] getHashBytes() {
        return m_hashBytes;
    }



    public void setHashHex(String hashHexString) {
        
        try {
            m_hashBytes = Hex.decodeHex(hashHexString);
        } catch (DecoderException e) {
   
        }
     
    }

    public void setHash(byte[] hashBytes) {
        m_hashBytes = hashBytes;
    }

    public JsonObject getJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("id", m_id.getAsUrlSafeString());
        json.addProperty("name", m_name);
        if (m_hashBytes != null) {
            json.addProperty("hash", getHashStringHex());
        }
        return json;
    }
}
