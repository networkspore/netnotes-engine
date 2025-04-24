package io.netnotes.engine.networks.ergo;

import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.PriceAmount;


public class ErgoTransactionPartner {
    public static class PartnerType{
        public final static String RECEIVER = "Receiver";
        public final static String SENDER = "Sender";
        public final static String MINER = "Miner";
        public final static String UNKNOWN = "Unknown";
        public final static String OTHER = "...";
    }

    public final static String OTHER_ADDRESSES = "...";
    public final static String PENDING_ADDRESS = "Pending...";

    private String m_addressString;
    private String m_partnerType;
    private long m_nanoErgs = 0;
    private ArrayList< PriceAmount> m_tokensList = new ArrayList<>();

    private long m_remainingNanoErgs = 0;
    private ArrayList< PriceAmount> m_remainingTokensList = new ArrayList<>();

    private ArrayList<ErgoBox> m_inputBoxes = new ArrayList<>();
    private ArrayList<ErgoBox> m_outputBoxes = new ArrayList<>();

    public ErgoTransactionPartner(String partnerType, ErgoBox ergoBox){
        
        m_partnerType = partnerType;
        
        if(partnerType.equals(PartnerType.SENDER)){
            m_addressString = ergoBox.getAddress();
            m_inputBoxes.add(ergoBox);
        }else{
            m_addressString = partnerType.equals(PartnerType.OTHER) ? OTHER_ADDRESSES : ergoBox.getAddress();
            if(ergoBox.getAssets() != null){
                for(int i = 0; i< ergoBox.getAssets().length; i++){
                    m_tokensList.add(new PriceAmount(ergoBox.getAssets()[i], false));
                }
            }
            m_nanoErgs = ergoBox.getValue();
            m_outputBoxes.add(ergoBox);
        }
    }

    public ErgoTransactionPartner(String address, String partnerType, long nanoErgs, PriceAmount... tokens){
        m_addressString = address;
        m_partnerType = partnerType;
        if(tokens != null && tokens.length > 0){
            for(PriceAmount token : tokens){
                m_tokensList.add(token);
            }
        }
        m_nanoErgs = nanoErgs;
    }

    public ErgoTransactionPartner(JsonObject json){
        JsonElement partnerTypeElement = json.get("type");
        JsonElement addressElement = json.get("address");
        JsonElement nanoErgsElement = json.get("nanoErgs");
        JsonElement tokensElement = json.get("tokens");
        JsonElement outputBoxesElement = json.get("outputBoxes");
        JsonElement inputBoxesElement = json.get("inputBoxes");
        JsonElement remainingNanoErgsElement = json.get("remainingNanoErgs");
        JsonElement remainingTokensElement = json.get("remainingTokens");

        if(partnerTypeElement == null || addressElement == null || nanoErgsElement == null){
            return;
        }
        
        m_partnerType = partnerTypeElement.getAsString();

        m_nanoErgs = nanoErgsElement != null && !nanoErgsElement.isJsonNull() ? nanoErgsElement.getAsLong() : 0;
        m_remainingNanoErgs = remainingNanoErgsElement != null && !remainingNanoErgsElement.isJsonNull() ? remainingNanoErgsElement.getAsLong() : 0;
        JsonArray tokensArray = tokensElement != null && tokensElement.isJsonArray() ? tokensElement.getAsJsonArray() : null;
        if(tokensArray != null){
            try{
                setTokens( ErgoTransactionView.getJsonTokenAmounts(tokensArray));
            }catch(Exception e){

            }
        }
        JsonArray remainingTokensArray = remainingTokensElement != null && remainingTokensElement.isJsonArray() ? remainingTokensElement.getAsJsonArray() : null;
        if(remainingTokensArray != null){
            try{
                setRemainingTokens(ErgoTransactionView.getJsonTokenAmounts(remainingTokensArray));
            }catch(Exception e){

            }
        }

        m_addressString = addressElement != null && !addressElement.isJsonNull() ? addressElement.getAsString() : "";

        if(outputBoxesElement != null && !outputBoxesElement.isJsonNull() && outputBoxesElement.isJsonArray()){
            JsonArray outputBoxes = outputBoxesElement.getAsJsonArray();
            for(int i =0 ; i< outputBoxes.size() ; i++){
                m_outputBoxes.add(new ErgoBox(outputBoxes.get(i).getAsJsonObject()));
            }
        }

        if(inputBoxesElement != null && !inputBoxesElement.isJsonNull() && inputBoxesElement.isJsonArray()){
            JsonArray inputBoxes = inputBoxesElement.getAsJsonArray();
            for(int i =0 ; i< inputBoxes.size() ; i++){
                m_inputBoxes.add(new ErgoBox(inputBoxes.get(i).getAsJsonObject()));
            }
        }

    }

    public ErgoTransactionPartner(JsonReader reader) throws IOException{
        readJson(reader);
    }

    public ArrayList<ErgoBox> getInputBoxList(){
        return m_inputBoxes;
    }

    public void addInputBox(ErgoBox ergoBox){
        m_inputBoxes.add(ergoBox);
    }
    

    public ErgoBox[] getOutputBoxes(){
        return m_outputBoxes.toArray(new ErgoBox[m_outputBoxes.size()]);
    }

    public void addOutputBoxToValue(ErgoBox outputBox){
        if(!m_partnerType.equals(PartnerType.OTHER)){
            m_outputBoxes.add(outputBox);
        }

        addNanoErgs(outputBox.getValue());
        PriceAmount[] amounts = outputBox.getAssetsAsPriceAmounts(false);
        if(amounts != null && amounts.length > 0){
            addTokens(amounts);
        }
        
    }

    public void addOutputBoxToRemaining(ErgoBox outputBox){
        m_outputBoxes.add(outputBox);

        addRemainingNanoErgs(outputBox.getValue());
        PriceAmount[] amounts = outputBox.getAssetsAsPriceAmounts(false);
        if(amounts != null && amounts.length > 0){
            addRemainingTokens(amounts);
        }
        
    }

    public void addNanoErgs(long nanoErgs){
        m_nanoErgs += nanoErgs;
    }

    public void addRemainingNanoErgs(long nanoErgs){
        m_remainingNanoErgs += nanoErgs;
    }



    public void addTokens(PriceAmount[] tokens){
        if(tokens != null){
            for(int i = 0; i < tokens.length ; i++){
                PriceAmount token = tokens[i];
                if(token != null){
                    PriceAmount currentToken = getToken(token.getTokenId());

                    if(currentToken != null){
                        currentToken.addBigDecimalAmount(token.getBigDecimalAmount());

                    }else{
                        m_tokensList.add(token);
                    }
                }
            }
        }
    }

    public void addRemainingTokens(PriceAmount[] tokens){
        if(tokens != null){
            for(int i = 0; i < tokens.length ; i++){
                PriceAmount token = tokens[i];
                if(token != null){
                    PriceAmount currentToken = getRemainingToken(token.getTokenId());

                    if(currentToken != null){
                        currentToken.addBigDecimalAmount(token.getBigDecimalAmount());
                    }else{
                        
                        m_remainingTokensList.add(token);
                    }
                }
            }
        }
    }


    public String getAddressString(){
        return m_addressString;
    }

    public String getPartnerType(){
        return m_partnerType;
    }

    public long getNanoErgs(){
        return m_nanoErgs;
    }

    public void setNanoErgs(long nanoErgs){
        m_nanoErgs = nanoErgs;
    }

    public void setTokens(PriceAmount[] tokens){
        m_tokensList.clear();
        if(tokens != null && tokens.length > 0){
            for(PriceAmount token : tokens){
                m_tokensList.add(token);
            }
        }
    }

    public void setRemainingTokens(PriceAmount[] tokens){
        m_remainingTokensList.clear();
        if(tokens != null && tokens.length > 0){
            for(PriceAmount token : tokens){
                m_remainingTokensList.add(token);
            }
        }
    }

    public PriceAmount[] getRemainingTokens(){
        return m_remainingTokensList.toArray(new PriceAmount[m_remainingTokensList.size()]);
    }

    public PriceAmount[] getTokens(){
        return m_tokensList.toArray(new PriceAmount[ m_tokensList.size()]);
    }

    public PriceAmount getRemainingToken(String tokenId){
        if(tokenId != null){
            for(int i = 0; i < m_remainingTokensList.size(); i++){
                PriceAmount token = m_remainingTokensList.get(i);
                if(token.getTokenId().equals(tokenId)){
                    return token;
                }
            }
        }
        return null;
    }

    public PriceAmount getToken(String tokenId){
        if(tokenId != null){

            for(int i = 0; i < m_tokensList.size() ; i++){
                PriceAmount token = m_tokensList.get(i);
                if(token.getTokenId().equals(tokenId)){
                    return token;
                }
            }
        }
        return null;
    }

    public ArrayList<PriceAmount> tokensList(){
        return m_tokensList;
    }

    public ArrayList<PriceAmount> remaingTokensList(){
        return m_tokensList;
    }

    public long remainingNanoErgs(){
        return m_remainingNanoErgs;
    }

    public JsonArray getInputBoxesJsonArray(){
        JsonArray jsonArray = new JsonArray();
        for(int i = 0; i < m_inputBoxes.size(); i++){
            jsonArray.add(m_inputBoxes.get(i).getJsonObject());
        }
        return jsonArray;
    }

    public JsonArray getOutputBoxesJsonArray(){
        JsonArray jsonArray = new JsonArray();
        for(int i = 0; i < m_outputBoxes.size(); i++){
            jsonArray.add(m_outputBoxes.get(i).getJsonObject());
        }
        return jsonArray;
    }

    public void readRemainingTokens(JsonReader reader) throws IOException{

        reader.beginArray();
        PriceAmount amount =  PriceAmount.readPriceAmount(reader);
        if(amount != null){
            m_remainingTokensList.add(amount);
        }
        reader.endArray();

    }

    public void readTokens(JsonReader reader) throws IOException{

        reader.beginArray();
        PriceAmount amount =  PriceAmount.readPriceAmount(reader);
        if(amount != null){
            m_tokensList.add(amount);
        }
        reader.endArray();

    }

    public void writeTokens(JsonWriter writer) throws IOException{
        writer.beginArray();
        for(PriceAmount amount : m_tokensList){
            amount.writeJson(writer);
        }
        writer.endArray();
    }

    public void writeRemainingTokens(JsonWriter writer) throws IOException{
        writer.beginArray();
        for(PriceAmount amount : m_remainingTokensList){
            amount.writeJson(writer);
        }
        writer.endArray();
    }


    public void writeJson(JsonWriter writer) throws IOException{
        writer.beginObject();
        writer.name("type");
        writer.value(m_partnerType);
        writer.name("address");
        writer.value(m_addressString);
        writer.name("nanoErgs");
        writer.value(m_nanoErgs);
        if(m_tokensList.size() > 0){
            writer.name("tokens");
            writeTokens(writer);
        }
        if(m_remainingNanoErgs > 0){
            writer.name("remainingNanoErgs");
            writer.value(m_remainingNanoErgs);
        }
        if(m_remainingTokensList.size() > 0){
            writer.name("remainingTokens");
            writeRemainingTokens(writer);
        }
        if(m_inputBoxes.size() > 0){
            writer.name("inputBoxes");
            writeInputBoxes(writer);
        }
        if(m_outputBoxes.size() > 0){
            writer.name("outputBoxes");
            writeOutputBoxes(writer);
        }
        writer.endObject();
    }

    public void writeInputBoxes(JsonWriter writer) throws IOException {
        writer.beginArray();
        for(ErgoBox ergoBox : m_inputBoxes){
            ergoBox.writeJson(writer);
        }
        writer.endArray();
    }

    public void writeOutputBoxes(JsonWriter writer) throws IOException {
        writer.beginArray();
        for(ErgoBox ergoBox : m_outputBoxes){
            ergoBox.writeJson(writer);
        }
        writer.endArray();
    }

    public void readInputBoxes(JsonReader reader) throws IOException {
        reader.beginArray();
        while(reader.hasNext()){
            m_inputBoxes.add(new ErgoBox(reader));
        }
        reader.endArray();
    }

    public void readOutputBoxes(JsonReader reader) throws IOException {
        reader.beginArray();
        while(reader.hasNext()){
            m_outputBoxes.add(new ErgoBox(reader));
        }
        reader.endArray();
    }

    private void readJson(JsonReader reader) throws IOException{
        reader.beginObject();
        while(reader.hasNext()){
            switch(reader.nextName()){
                case "type":
                    m_partnerType = reader.nextString();
                break;
                case "address":
                    m_addressString = reader.nextString();
                break;
                case "nanoErgs":
                    m_nanoErgs = reader.nextLong();
                break;
                case "tokens":
                    readTokens(reader);
                break;
                case "remainingNanoErgs":
                    m_remainingNanoErgs = reader.nextLong();
                break;
                case "remainingTokens":
                    readRemainingTokens(reader);
                break;
                case "inputBoxes":
                    readInputBoxes(reader);
                break;
                case "outputBoxes":
                    readOutputBoxes(reader);
                break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }

    public JsonObject getJsonObject(){
        JsonObject json = new JsonObject();
        json.addProperty("type", m_partnerType);
        json.addProperty("address", m_addressString);
        json.addProperty("nanoErgs", m_nanoErgs);
        if(m_tokensList.size() > 0){
            json.add("tokens", ErgoTransactionView.getTokenJsonArray(getTokens()));
        }
        if(m_remainingNanoErgs > 0){
            json.addProperty("remainingNanoErgs", m_remainingNanoErgs);
        }
        if(m_remainingTokensList.size() > 0){
            json.add("remainingTokens", ErgoTransactionView.getTokenJsonArray(getRemainingTokens()));
        }
        if(m_inputBoxes.size() > 0){
            json.add("inputBoxes", getInputBoxesJsonArray());
        }
        if(m_outputBoxes.size() > 0){
            json.add("outputBoxes", getOutputBoxesJsonArray());
        }
        return json;
    }
}
