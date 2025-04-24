package io.netnotes.engine.networks.ergo;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

import com.google.gson.JsonElement;

public class ErgoBoxAsset {
    private String m_tokenId;
    private long m_amount;
    private int m_index;
    private String m_name;
    private int m_decimals = 0;
    private String m_type = null;


    public String getTokenId() {
        return m_tokenId;
    }

    public long getAmount() {
        return m_amount;
    }

    public int getIndex() {
        return m_index;
    }

    public String getName() {
        return m_name;
    }

    public int getDecimals() {
        return m_decimals;
    }

    public String getType() {
        return m_type;
    }

    public ErgoBoxAsset(long amount, String tokenId, String name, int decimals, String tokenType){
        m_amount = amount;
        m_tokenId = tokenId;
        m_name = name == null ? "" : name;
        m_type = tokenType == null ? "" : tokenType;
        m_decimals = decimals < 0 ? 0 : decimals;
    }

    public ErgoBoxAsset(long amount, String tokenId){
        m_amount = amount;
        m_tokenId = tokenId;
        m_name = "";
        m_type = "";
        m_decimals = 0;
    }

    public ErgoBoxAsset(JsonObject json) throws NullPointerException{

        JsonElement tokenIdElement = json.get("tokenId");
        if(tokenIdElement == null){
            throw new NullPointerException("tokenId is null");
        }
        JsonElement amountElement = json.get("amount");
        JsonElement indexElement = json.get("index");
        JsonElement nameElement = json.get("name");
        JsonElement decimalsElement = json.get("decimals");
        JsonElement typeElement = json.get("type");

        m_tokenId = tokenIdElement.getAsString();
        m_amount = amountElement != null ? amountElement.getAsLong() : 0;
        m_index = indexElement != null ? indexElement.getAsInt() : 0;
        m_name = nameElement != null && !nameElement.isJsonNull() ? nameElement.getAsString() : "";
        m_decimals = decimalsElement != null && !decimalsElement.isJsonNull() ? decimalsElement.getAsInt() : 0;
        m_type = typeElement != null && !typeElement.isJsonNull() ? typeElement.getAsString() : "";

    }

    public ErgoBoxAsset(JsonReader reader) throws IOException{
        reader.beginObject();
        while(reader.hasNext()){
            boolean isNotNull = reader.peek() != JsonToken.NULL;
            if(!isNotNull){
                reader.nextNull();
            }
            switch(reader.nextName()){
                case "tokenId":
                    m_tokenId = reader.nextString();
                break;
                case "amount":
                    m_amount = isNotNull ? reader.nextLong() : 0;
                break;
                case "index":
                    m_index =  isNotNull ? reader.nextInt() : m_index;
                break;
                case "name":
                    m_name = isNotNull ? reader.nextString() : m_name;
                break;
                case "decimals":
                    m_decimals = isNotNull ? reader.nextInt() : m_decimals;
                break;
                case "type":
                    m_type = isNotNull ? reader.nextString() : m_type;
                break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }

    public void writeJson(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("tokenId");
        writer.value(m_tokenId);
        writer.name("amount");
        writer.value(m_amount);
        writer.name("index");
        writer.value(m_index);
        writer.name("name");
        writer.value(m_name);
        writer.name("decimals");
        writer.value(m_decimals);
        writer.name("type");
        writer.value(m_type);
        writer.endObject();
    }


    public JsonObject getJsonObject(){
        JsonObject json = new JsonObject();
        json.addProperty("tokenId", m_tokenId);
        json.addProperty("amount", m_amount);
        json.addProperty("index", m_index);
        json.addProperty("name", m_name);
        json.addProperty("decimals", m_decimals);
        json.addProperty("type", m_type);
        return json;
    }
}
