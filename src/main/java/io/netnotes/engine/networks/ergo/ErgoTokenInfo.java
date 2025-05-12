package io.netnotes.engine.networks.ergo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ErgoTokenInfo {
    private String m_boxId = "";
    private long m_emissionAmount = 0;
    private String m_description = "";
    private String m_tokenType = "";
    private String m_name = "";
    private int m_decimals = 0;
    private String m_tokenId = "";

    public String getBoxId() {
        return m_boxId;
    }

    public long getEmissionAmount() {
        return m_emissionAmount;
    }

    public String getDescription() {
        return m_description;
    }

    public String getTokenType() {
        return m_tokenType;
    }

    public String getName() {
        return m_name;
    }

    public int getDecimals() {
        return m_decimals;
    }

    public String getTokenId() {
        return m_tokenId;
    }

    public ErgoTokenInfo(JsonObject json) throws NullPointerException{
        JsonElement idElement = json.get("id");
        JsonElement boxIdElement = json.get("boxId");
        JsonElement emissionAmountElement = json.get("emissionAmount");

        if(idElement != null && boxIdElement != null && emissionAmountElement != null && !idElement.isJsonNull() && !boxIdElement.isJsonNull() && !emissionAmountElement.isJsonNull()){
            JsonElement decimalsElement = json.get("decimals");
            JsonElement nameElement = json.get("name");
            JsonElement descriptionElement = json.get("description");
            JsonElement typeElement = json.get("type");

            m_tokenId = idElement != null ? idElement.getAsString() : m_tokenId;
            m_boxId = boxIdElement != null && !boxIdElement.isJsonNull() ? boxIdElement.getAsString() : m_boxId;
            m_emissionAmount = emissionAmountElement != null && !emissionAmountElement.isJsonNull() ? emissionAmountElement.getAsLong() : m_emissionAmount;
            m_description = descriptionElement != null && !descriptionElement.isJsonNull() ? descriptionElement.getAsString() : m_description;
            m_tokenType = typeElement != null && !typeElement.isJsonNull() ? typeElement.getAsString() : m_tokenType;
            m_name = nameElement != null && !nameElement.isJsonNull()  ? nameElement.getAsString() : m_name;
            m_decimals = decimalsElement != null ? decimalsElement.getAsInt() : m_decimals;
        }else{
            throw new NullPointerException("Id is null");
        }
    }

    public ErgoTokenInfo(String id, String boxId, long emissionAmount, String name, String decription, String type, int decimals){
        m_tokenId = id;
        m_boxId = boxId;
        m_emissionAmount = emissionAmount;
        m_name = name;
        m_description = decription;
        m_tokenType = type;
        m_decimals = decimals;
    }


    public JsonObject getJsonObject(){
        JsonObject json = new JsonObject();
        json.addProperty("id", m_tokenId);
        json.addProperty("boxId", m_boxId);
        json.addProperty("emissionAmount", m_emissionAmount);
        json.addProperty("name", m_name);
        json.addProperty("description", m_description);
        json.addProperty("type", m_tokenType);
        json.addProperty("decimals", m_decimals);

        return json;
    }
}
