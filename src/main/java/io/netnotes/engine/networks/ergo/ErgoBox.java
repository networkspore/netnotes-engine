package io.netnotes.engine.networks.ergo;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.PriceAmount;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class ErgoBox {
    private String m_boxId;
    private String m_transactionId = null;
    private String m_blockId;
    private long m_value;
    private int m_index;
    private long m_globalIndex = -1;
    private int m_creationHeight = -1;
    private int m_settlementHeight = -1;
    private String m_ergoTree;
    private String m_ergoTreeConstants;
    private String m_ergoTreeScript = null;
    private String m_address;
    private ErgoBoxAsset[] m_assets = null;
    private HashMap<String, ErgoBoxRegister> m_additionalRegisters = new HashMap<>();
    private boolean m_mainChain = true;
    private String m_spentTransactionId = null;

    public ErgoBox(JsonObject json) throws NullPointerException{

        JsonElement boxIdElement = json.get("boxId");
        if(boxIdElement == null || boxIdElement.isJsonNull()){
            throw new NullPointerException("boxId is null");
        }
        JsonElement transactionIdElement = json.get("transactionId");
        JsonElement blockIdElement = json.get("blockId");
        JsonElement valueElement = json.get("value");
        JsonElement indexElement = json.get("index");
        JsonElement globalIndexElement = json.get("globalIndex");
        JsonElement creationHeightElement = json.get("creationHeight");
        JsonElement settlementHeightElement = json.get("settlementHeight");
        JsonElement ergoTreeElement = json.get("ergoTree");
        JsonElement ergoTreeConstantsElement = json.get("ergoTreeConstants");
        JsonElement ergoTreeScriptElement = json.get("ergoTreeScript");
        JsonElement addressElement = json.get("address");
        JsonElement assetsElement = json.get("assets");
        JsonElement additionalRegistersElement = json.get("additionalRegisters");
        JsonElement spentTransactionIdElement = json.get("spentTransactionId");
        JsonElement mainChainElement = json.get("mainChain");
        
        m_boxId =  boxIdElement.getAsString();
        m_transactionId = transactionIdElement != null ? transactionIdElement.getAsString() : null;
        m_blockId = blockIdElement != null ? blockIdElement.getAsString() : null;
        m_value = valueElement != null ? valueElement.getAsLong() : -1;
        m_index = indexElement != null ? indexElement.getAsInt() : -1;
        m_globalIndex = globalIndexElement != null ? globalIndexElement.getAsLong() : -1;
        m_creationHeight = creationHeightElement != null ? creationHeightElement.getAsInt(): -1;
        m_settlementHeight = settlementHeightElement != null ? settlementHeightElement.getAsInt() : -1;
        m_ergoTree = ergoTreeElement != null ? ergoTreeElement.getAsString() : null;
        m_ergoTreeConstants = ergoTreeConstantsElement != null ? ergoTreeConstantsElement.getAsString() : null;
        m_ergoTreeScript = ergoTreeScriptElement != null ? ergoTreeScriptElement.getAsString() : null;
        m_address = addressElement != null ? addressElement.getAsString() : null;
        m_spentTransactionId = spentTransactionIdElement != null && !spentTransactionIdElement.isJsonNull() ? spentTransactionIdElement.getAsString() : null;
        if(assetsElement != null && assetsElement.isJsonArray()){
            setAssets(assetsElement.getAsJsonArray());
        }

        if(additionalRegistersElement != null && !additionalRegistersElement.isJsonNull() && additionalRegistersElement.isJsonObject()){
            setRegisters(additionalRegistersElement.getAsJsonObject());
        }
        m_mainChain = mainChainElement != null ? mainChainElement.getAsBoolean() : true;
    }

    public ErgoBox(JsonReader reader) throws IOException{
        readJson(reader);
    }
    //Expected a name but was NULL at line 1 column 6818 path $.items[0].outputs[0].spentTransactionId
    public void readJson(JsonReader reader) throws IOException{
        
        reader.beginObject();
        while(reader.hasNext()){
            String name = reader.nextName();
            boolean isNotNull = reader.peek() != JsonToken.NULL;
            if(!isNotNull){
                reader.nextNull();
            }
            switch(name){
                case "boxId":
                    m_boxId = reader.nextString() ;
                break;
                case "transactionId":
                    m_transactionId = isNotNull ? reader.nextString() : null;
                break;
                case "blockId":
                    m_blockId = isNotNull ? reader.nextString() : null;
                break;
                case "value":
                    m_value = isNotNull ? reader.nextLong() : m_value;
                break;
                case "index":
                    m_index =  isNotNull ? reader.nextInt() : m_index;
                break;
                case "globalIndex":
                    m_globalIndex =   isNotNull ?  reader.nextLong() : m_globalIndex ;
                break;
                case "creationHeight":
                    m_creationHeight = isNotNull ? reader.nextInt() : m_creationHeight;
                break;
                case "settlementHeight":
                    m_settlementHeight = isNotNull ? reader.nextInt() : m_settlementHeight;
                break;
                case "ergoTree":
                    m_ergoTree =  isNotNull ? reader.nextString() : null;
                break;
                case "ergoTreeConstants":
                    m_ergoTreeConstants =  isNotNull ? reader.nextString() : null;
                break;
                case "ergoTreeScript":
                    m_ergoTreeScript = isNotNull ? reader.nextString() : null;
                break;
                case "address":
                    m_address =  isNotNull ? reader.nextString() : null;
                break;
                case "assets":
                    readAssets(reader);
                break;
                case "additionalRegisters":
                    readRegisters(reader);
                break;
                case "spentTransactionId":
                    m_spentTransactionId = isNotNull ? reader.nextString() : null;
                break;
                case "mainChain":
                    m_mainChain = reader.nextBoolean();
                break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }

    public void writeJson(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("boxId");
        writer.value(m_boxId);
        if(m_transactionId != null){
            writer.name("transactionId");
            writer.value(m_transactionId);
        }
        if(m_blockId != null){
            writer.name("blockId");
            writer.value(m_blockId);
        }
        writer.name("value");
        writer.value(m_value);
        writer.name("index");
        writer.value(m_index);
        if(m_globalIndex > -1){
            writer.name("globalIndex");
            writer.value(m_globalIndex);
        }
        if(m_creationHeight > -1){
            writer.name("creationHeight");
            writer.value(m_creationHeight);
        }
        if(m_settlementHeight > -1){
            writer.name("settlementHeight");
            writer.value(m_settlementHeight);
        }
        writer.name("ergoTree");
        writer.value(m_ergoTree);
        writer.name("ergoTreeConstants");
        writer.value(m_ergoTreeConstants);
        if(m_ergoTreeScript != null){
            writer.name("ergoTreeScript");
            writer.value(m_ergoTreeScript);
        }
        writer.name("address");
        writer.value(m_address);
        if(m_assets != null && m_assets.length > 0){
            writer.name("assets");
            writeJsonAssets(writer);
        }
        if(m_additionalRegisters != null && m_additionalRegisters.size() > 0){
            writer.name("additionalRegisters");
            writeRegisters(writer);
        }
        if(m_spentTransactionId != null){
            writer.name("spentTransactionId");
            writer.value(m_spentTransactionId);
        }
        writer.name("mainChain");
        writer.value(m_mainChain);
        writer.endObject();
    }

    public void writeJsonAssets(JsonWriter writer) throws IOException{
        int size = m_assets != null ? m_assets.length : 0;
        writer.beginArray();

        for(int i = 0; i < size ; i++){
            m_assets[i].writeJson(writer);
        }

        writer.endArray();
    }

    public void writeRegisters(JsonWriter writer) throws IOException{
        writer.beginObject();
        for(Map.Entry<String, ErgoBoxRegister> entry : m_additionalRegisters.entrySet()){
            writer.name(entry.getKey());
            entry.getValue().writeJson(writer);
        }
        writer.endObject();
    }

    public String getBoxId() {
        return m_boxId;
    }

    public String getTransactionId() {
        return m_transactionId;
    }

    public String getBlockId() {
        return m_blockId;
    }

    public long getValue() {
        return m_value;
    }

    public int getIndex() {
        return m_index;
    }

    public long getGlobalIndex() {
        return m_globalIndex;
    }

    public int getCreationHeight() {
        return m_creationHeight;
    }

    public int getSettlementHeight() {
        return m_settlementHeight;
    }

    public String getErgoTree() {
        return m_ergoTree;
    }

    public String getErgoTreeConstants() {
        return m_ergoTreeConstants;
    }

    public String getErgoTreeScript() {
        return m_ergoTreeScript;
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

    public String getSpentTransactionId(){
        return m_spentTransactionId;
    }

    public boolean getMainChain(){
        return m_mainChain;
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
        json.addProperty("boxId", m_boxId);
        if(m_transactionId != null){
            json.addProperty("transactionId", m_transactionId);
        }
        if(m_blockId != null){
            json.addProperty("blockId", m_blockId);
        }
        json.addProperty("value", m_value);
        json.addProperty("index", m_index);
        if(m_globalIndex > -1){
            json.addProperty("globalIndex", m_globalIndex);
        }
        if(m_creationHeight > -1){
            json.addProperty("creationHeight", m_creationHeight);
        }
        if(m_settlementHeight > -1){
            json.addProperty("settlementHeight", m_settlementHeight);
        }
        json.addProperty("ergoTree", m_ergoTree);
        json.addProperty("ergoTreeConstants", m_ergoTreeConstants);
        if(m_ergoTreeScript != null){
            json.addProperty("ergoTreeScript", m_ergoTreeScript);
        }
        json.addProperty("address", m_address);
        if(m_assets != null && m_assets.length > 0){
            json.add("assets", getAssetsJsonArray());
        }
        if(m_additionalRegisters != null && m_additionalRegisters.size() > 0){
            json.add("additionalRegisters", getRegistersJson());
        }
        if(m_spentTransactionId != null){
            json.addProperty("spentTransactionId", m_spentTransactionId);
        }
        json.addProperty("mainChain", m_mainChain);
        return json;
    }
}
