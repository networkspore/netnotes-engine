package io.netnotes.engine.networks.ergo;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

import io.netnotes.engine.PriceAmount;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class ErgoDataInputBox {
    private String m_boxId;
    private String m_outputTransactionId;
    private String m_outputBlockId;
    private long m_value;
    private int m_index;
    private long m_outputIndex;
    private String m_ergoTree;
    private String m_address;
    private ErgoBoxAsset[] m_assets = null;
    private HashMap<String, ErgoBoxRegister> m_additionalRegisters = new HashMap<>();

    public ErgoDataInputBox(JsonObject json) throws NullPointerException{

        JsonElement boxIdElement = json.get("boxId");
        if(boxIdElement == null || boxIdElement.isJsonNull()){
            throw new NullPointerException("boxId is null");
        }
        
        JsonElement valueElement = json.get("value");
        JsonElement indexElement = json.get("index");
        JsonElement blockIdElement = json.get("outputBlockId");
        JsonElement transactionIdElement = json.get("outputTransactionId");
        JsonElement outputIndexElement = json.get("outputIndex");
        JsonElement ergoTreeElement = json.get("ergoTree");
        JsonElement addressElement = json.get("address");
        JsonElement assetsElement = json.get("assets");
        JsonElement additionalRegistersElement = json.get("additionalRegisters");
    
        m_boxId =  boxIdElement.getAsString();
        m_outputTransactionId = transactionIdElement != null ? transactionIdElement.getAsString() : null;
        m_outputBlockId = blockIdElement != null ? blockIdElement.getAsString() : null;
        m_value = valueElement != null ? valueElement.getAsLong() : -1;
        m_index = indexElement != null ? indexElement.getAsInt() : -1;
        m_outputIndex = outputIndexElement != null ? outputIndexElement.getAsLong() : -1;
        m_ergoTree = ergoTreeElement != null ? ergoTreeElement.getAsString() : null;
        m_address = addressElement != null ? addressElement.getAsString() : null;
        if(assetsElement != null && assetsElement.isJsonArray()){
            setAssets(assetsElement.getAsJsonArray());
        }

        if(additionalRegistersElement.isJsonObject()){
            setRegisters(additionalRegistersElement.getAsJsonObject());
        }
    }

    public ErgoDataInputBox(JsonReader reader) throws IOException{
        reader.beginObject();
        while(reader.hasNext()){
            switch(reader.nextName()){
                case "boxId":
                    m_boxId = reader.nextString();
                break;
                case "outputTransactionId":
                    m_outputTransactionId = reader.nextString();
                break;
                case "outputBlockId":
                    m_outputBlockId = reader.nextString();
                break;
                case "value":
                    m_value = reader.nextLong();
                break;
                case "index":
                    m_index = reader.nextInt();
                break;
                case "outputIndex":
                    m_outputIndex = reader.nextLong();
                break;
         
                case "ergoTree":
                    m_ergoTree = reader.nextString();
                break;
   
                case "address":
                    m_address = reader.nextString();
                break;
                case "assets":
                    readAssets(reader);
                break;
                case "additionalRegisters":
                    readRegisters(reader);
                break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }


    public String getBoxId() {
        return m_boxId;
    }

    public String getOutputTransactionId() {
        return m_outputTransactionId;
    }

    public String getOutputBlockId() {
        return m_outputBlockId;
    }

    public long getValue() {
        return m_value;
    }

    public int getIndex() {
        return m_index;
    }

    public long getOutputIndex() {
        return m_outputIndex;
    }

    public String getErgoTree() {
        return m_ergoTree;
    }

    public String getAddress() {
        return m_address;
    }

    public ErgoBoxAsset[] getAssets() {
        return m_assets;
    }

    public HashMap<String, ErgoBoxRegister> getAdditionalRegisters() {
        return m_additionalRegisters;
    }


    private void setRegisters(JsonObject json){
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String registerName = entry.getKey();
            JsonElement registerElement = entry.getValue();

            m_additionalRegisters.put(registerName, new ErgoBoxRegister(registerElement.getAsJsonObject()));
        }
    }

    private void readRegisters(JsonReader reader) throws IOException{
        reader.beginObject();
        while (reader.hasNext()) {
            m_additionalRegisters.put(reader.nextName(), new ErgoBoxRegister(reader));
        }
        reader.endObject();
    }

    private void setAssets(JsonArray jsonArray){
        int size = jsonArray.size();
        m_assets = new ErgoBoxAsset[size];

        for(int i = 0; i < size ; i++){

            JsonElement jsonElement = jsonArray.get(i);
            
            JsonObject json = !jsonElement.isJsonNull() && jsonElement.isJsonObject() ? jsonElement.getAsJsonObject() : null;

            m_assets[i] = json != null ? new ErgoBoxAsset(json) : null;
        }
    }

    private void readAssets(JsonReader reader) throws IOException{
        reader.beginArray();
        ArrayList<ErgoBoxAsset> assetList = new ArrayList<>();
        while(reader.hasNext()){
            assetList.add(new ErgoBoxAsset(reader));
        }
        m_assets = assetList.toArray(new ErgoBoxAsset[assetList.size()]);
        reader.endArray();
    }

    public PriceAmount[] getAssetsAsPriceAmounts(){
        return getAssetsAsPriceAmounts(false);
    }

    public PriceAmount[] getAssetsAsPriceAmounts(boolean readonly){
        int size = m_assets != null ? m_assets.length : 0;
        PriceAmount[] amounts = new PriceAmount[size];

        for(int i = 0; i < size ; i++){
            amounts[i] = new PriceAmount(m_assets[i], readonly);
        }

        return amounts;
    }

    public JsonArray getAssetsJsonArray(){
        int size = m_assets != null ? m_assets.length : 0;
        JsonArray jsonArray = new JsonArray();

        for(int i = 0; i < size ; i++){
            jsonArray.add(m_assets[i].getJsonObject());
        }

        return jsonArray;
    }

    public JsonObject getRegistersJson(){
        JsonObject json = new JsonObject();
        for(Map.Entry<String, ErgoBoxRegister> entry : m_additionalRegisters.entrySet()){
            json.add(entry.getKey(), entry.getValue().getJsonObject());
        }

        return json;
    }

    public JsonObject getJsonObject(){
        JsonObject json = new JsonObject();
        json.addProperty("outputTransactionId", m_outputTransactionId);
        json.addProperty("outputBlockId", m_outputBlockId);
        json.addProperty("value", m_value);
        json.addProperty("index", m_index);
        json.addProperty("outputIndex", m_outputIndex);
        json.addProperty("ergoTree", m_ergoTree);
        json.addProperty("address", m_address);
        if(m_assets.length > 0){
            json.add("assets", getAssetsJsonArray());
        }
        if(m_additionalRegisters.size() > 0){
            json.add("additionalRegisters", getRegistersJson());
        }
        return json;
    }
}
