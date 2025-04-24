package io.netnotes.engine.networks.ergo;

import java.io.IOException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.networks.ergo.ErgoTransactionView.TransactionStatus;

public class ErgoBoxInfo{
    private String m_boxId;
    private String m_status = "Transmitting";
    private String m_txId = null;
    private long m_timeStamp;

    public ErgoBoxInfo(String boxId, String status, String txId, long timeStamp){
        m_boxId = boxId;
        m_status = status;
        m_txId = txId;
        m_timeStamp = timeStamp;
    }

    public ErgoBoxInfo(JsonObject boxObject){
        JsonElement boxIdElement = boxObject.get("boxId");
        JsonElement statusElement = boxObject.get("status");
        JsonElement txIdElement = boxObject.get("txId");
        JsonElement timeStampElement = boxObject.get("timeStamp");

        m_boxId = boxIdElement.getAsString();
        m_status = statusElement.getAsString();
        m_timeStamp = timeStampElement.getAsLong();
        ErgoBoxInfo.this.m_txId = txIdElement != null && !txIdElement.isJsonNull() ? txIdElement.getAsString() : null;
    }

    public ErgoBoxInfo(JsonReader reader) throws IOException{
        reader.beginObject();
        while(reader.hasNext()){
            boolean notNull = reader.peek() != JsonToken.NULL;
            switch(reader.nextName()){
                case "boxId":
                    m_boxId = reader.nextString();
                break;
                case "status":
                    m_status = notNull ? reader.nextString() : TransactionStatus.PENDING;
                break;
                case "txId":
                    ErgoBoxInfo.this.m_txId = notNull ? reader.nextString() : TransactionStatus.PENDING;
                break;
                case "timeStamp":
                    m_timeStamp = notNull ? reader.nextLong() : 0;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }

    public long getTimeStamp(){
        return m_timeStamp;
    }

    public void setTimeStamp(long timeStamp){
        m_timeStamp = timeStamp;
    }

    public String getTxId(){
        return ErgoBoxInfo.this.m_txId;
    }

    public void setTxId(String txId){
        ErgoBoxInfo.this.m_txId = txId;
    }

    public String getBoxId(){
        return m_boxId;
    }
    public void setBoxId(String boxId){
        m_boxId = boxId;
    }

    public String getStatus(){
        return m_status;
    }

    public void setStatus(String status){
        m_status = status;
    }

    public JsonObject getJsonObject(){
        JsonObject json = new JsonObject();
        json.addProperty("boxId", ErgoBoxInfo.this.m_boxId);
        json.addProperty("status", ErgoBoxInfo.this.m_status);
        json.addProperty("txId", ErgoBoxInfo.this.m_txId);
        json.addProperty("timeStamp", ErgoBoxInfo.this.m_timeStamp);
        return json;
    }

    public void writeJson(JsonWriter writer) throws IOException{
        writer.beginObject();
        writer.name("boxId");
        writer.value(ErgoBoxInfo.this.m_boxId);
        writer.name("status");
        writer.value(ErgoBoxInfo.this.m_status);
        writer.name("txId");
        writer.value(ErgoBoxInfo.this.m_txId);
        writer.name("timeStamp");
        writer.value(ErgoBoxInfo.this.m_timeStamp);
        writer.endObject();
    }
}