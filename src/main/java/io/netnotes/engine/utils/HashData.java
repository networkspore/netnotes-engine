package io.netnotes.engine.utils;


import java.io.File;
import java.io.IOException;

import org.apache.commons.codec.DecoderException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.crypto.HashServices;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.collections.NoteBytesPair;
import io.netnotes.noteBytes.processing.ByteEncoding;
import io.netnotes.noteBytes.processing.ByteEncoding.EncodingType;

public class HashData {

    public static String DEFAULT_HASH = "Blake2b-256";


    private final String m_name;
    private byte[] m_hashBytes = null;

    public HashData(String name, byte[] hashBytes){
        this.m_name = name;
        this.m_hashBytes = hashBytes;
    }

    public HashData(File file) throws  IOException{
        m_name = DEFAULT_HASH;
        m_hashBytes = HashServices.digestFile(file);
    }

    public HashData(byte[] hashBytes) {
        m_name = DEFAULT_HASH;
        m_hashBytes =  hashBytes;
    }



    public static HashData createHashData(NoteBytesObject nbo)  {
        NoteBytesPair nameElement = nbo.get("name");
        NoteBytesPair hashBytes = nbo.get("hash");
        String name = nameElement != null ? nameElement.getAsString() : DEFAULT_HASH;

        if(hashBytes == null ){
            throw new IllegalStateException("hash is null");
        }

        byte[] hash = hashBytes.getValue().get();
        return new HashData(name, hash);
    }

    public static HashData createHashData(JsonObject json) throws IOException  {

        JsonElement nameElement = json != null ? json.get("name") : null;
        JsonElement hashStringElement = json != null ?  json.get("hash") : null;


        String name = nameElement != null ? nameElement.getAsString() : DEFAULT_HASH;
   
        byte[] bytes = null;
        if (hashStringElement != null) {
            try{
                bytes = ByteEncoding.decodeHex(hashStringElement.getAsString());
            }catch(DecoderException e){
                throw new IOException("Could not decode hex", e);
            }
        }else{
            throw new IOException("Hash is null");
        }
        return new HashData(name, bytes);
    }

    public void setHashHex(String hex){
        m_hashBytes = ByteEncoding.decodeEncodedString(hex, EncodingType.BASE_16);
    }

    public String getHashStringHex(){
        return ByteEncoding.encodeHexString(m_hashBytes);
    }
    
    public HashData(JsonReader reader) throws IOException{
        String name = null;
        reader.beginObject();
        while(reader.hasNext()){
            switch(reader.nextName()){
                case "name":
                    name = reader.nextString();
                break;
                case "hash":
                    setHashHex(reader.nextString());
                break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
        m_name = name == null ? DEFAULT_HASH : name;
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
