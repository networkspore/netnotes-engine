package io.netnotes.engine.networks.ergo;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

import com.google.gson.JsonElement;

public class ErgoBoxRegister {
 

        private String m_serializedValue;
        private String m_sigmaType;
        private JsonElement m_renderedValue;

        public String getSerializedValue() {
            return m_serializedValue;
        }

        public String getSigmaType() {
            return m_sigmaType;
        }

        public JsonElement getRenderedValue() {
            return m_renderedValue;
        }

        public ErgoBoxRegister(JsonObject json){
            JsonElement serializedValueElement = json.get("serializedValue");
            JsonElement sigmaTypeElement = json.get("sigmaType");
            JsonElement renderedValueElement = json.get("renderedValue");

            m_serializedValue = serializedValueElement != null ? serializedValueElement.getAsString() : "";
            m_sigmaType = sigmaTypeElement != null ? sigmaTypeElement.getAsString() : "";
            m_renderedValue = renderedValueElement;

        }

        public ErgoBoxRegister(JsonReader reader) throws IOException{
            reader.beginObject();
            while(reader.hasNext()){
                switch(reader.nextName()){
                    case "serializedValue":
                        m_serializedValue = reader.nextString();
                    break;
                    case "sigmaType":
                        m_sigmaType = reader.nextString();
                    break;
                    case "renderedValue":
                        m_renderedValue = new JsonPrimitive(reader.nextString());
                    break;
                    default:
                        reader.skipValue();
                }
            }
            reader.endObject();
        }

        public JsonObject getJsonObject(){
            JsonObject json = new JsonObject();
            json.addProperty("serializedValue", m_serializedValue);
            json.addProperty("sigmaType", m_sigmaType);
            json.add("renderedValue", m_renderedValue);
            return json;
        }

        public void writeJson(JsonWriter writer) throws IOException{
            writer.beginObject();
            writer.name("serializedValue");
            writer.value(m_serializedValue);
            writer.name("sigmaType");
            writer.value(m_sigmaType);
            writer.name("reenderedValue");
            writer.value(m_renderedValue.getAsString());
            writer.endObject();
        }
    
}
