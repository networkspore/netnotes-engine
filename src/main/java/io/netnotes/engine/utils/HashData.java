package io.netnotes.engine.utils;


import java.io.File;
import java.io.IOException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.EncodingHelpers;
import io.netnotes.engine.noteBytes.processing.EncodingHelpers.Encoding;

public class HashData {

    public static String DEFAULT_HASH = "Blake2b-256";


    private String m_name = DEFAULT_HASH;
    private byte[] m_hashBytes = null;

    public HashData(File file) throws  IOException{
        m_hashBytes = HashServices.digestFile(file);
    }

    public HashData(byte[] hashBytes) {
        m_hashBytes =  hashBytes;
    }

    public HashData(NoteBytesObject nbo)  {
        init(nbo);
    }


    public HashData(JsonObject json){
        openJson(json);
    }


    public void init(NoteBytesObject nbo)  {
        NoteBytesPair nameElement = nbo.get("name");
        NoteBytesPair hashStringElement = nbo.get("hash");

        if (nameElement != null) {
            m_name = nameElement.getAsString();
          
        }
        if (hashStringElement != null) {
           m_hashBytes = hashStringElement.getValue().get();
        }

    }

    public void openJson(JsonObject json)  {

        JsonElement nameElement = json != null ? json.get("name") : null;
        JsonElement hashStringElement = json != null ?  json.get("hash") : null;

        if (nameElement != null) {
            m_name = nameElement.getAsString();
        }
        if (hashStringElement != null) {
           m_hashBytes = EncodingHelpers.decodeHex(hashStringElement.getAsString());
        }

    }

    public void setHashHex(String hex){
        m_hashBytes = EncodingHelpers.decodeEncodedString(hex, Encoding.BASE_16);
    }

    public String getHashStringHex(){
        return EncodingHelpers.encodeHexString(m_hashBytes);
    }
    
    public HashData(JsonReader reader) throws IOException{
        reader.beginObject();
        while(reader.hasNext()){
            switch(reader.nextName()){
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
        writer.name("name");
        writer.value(getHashName());
        writer.name("hash");
        writer.value(getHashStringHex());
        writer.endObject();
    }



    public String getHashName() {
        return m_name;
    }

 
    public byte[] getHashBytes() {
        return m_hashBytes;
    }




    public void setHash(byte[] hashBytes) {
        m_hashBytes = hashBytes;
    }

    public NoteBytesObject getNoteBytesObject() {
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add("name", m_name);
        if (m_hashBytes != null) {
            nbo.add("hash", m_hashBytes);
        }
        return nbo;
    }

    public JsonObject getJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("name", getHashName());
        if (m_hashBytes != null) {
            json.addProperty("hash", getHashStringHex());
        }
        return json;
    }


    
}
