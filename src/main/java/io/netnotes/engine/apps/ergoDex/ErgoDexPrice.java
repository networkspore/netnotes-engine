package io.netnotes.engine.apps.ergoDex;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

public class ErgoDexPrice{
        private BigDecimal m_price;
        private long m_timeStamp;

        public ErgoDexPrice(JsonObject json) throws Exception{
            JsonElement timestampElement = json != null && json.isJsonObject() ? json.get("timestamp") : null;
            JsonElement priceElement = json != null && json.isJsonObject() ? json.get("price") : null;

            m_price = priceElement != null && priceElement.isJsonPrimitive() ? priceElement.getAsBigDecimal() : null;
            m_timeStamp = timestampElement != null && timestampElement.isJsonPrimitive() ? timestampElement.getAsLong() : -1;

            if(m_price == null || m_timeStamp == -1){
                throw new Exception("Invalid Spectrum Price");
            }
        }

        
        public ErgoDexPrice(JsonObject json, boolean invert) throws Exception{
            JsonElement timestampElement = json != null && json.isJsonObject() ? json.get("timestamp") : null;
            JsonElement priceElement = json != null && json.isJsonObject() ? json.get("price") : null;

            BigDecimal price = priceElement != null && priceElement.isJsonPrimitive() ? priceElement.getAsBigDecimal() : null;
            m_timeStamp = timestampElement != null && timestampElement.isJsonPrimitive() ? timestampElement.getAsLong() : -1;

            if(price == null || m_timeStamp == -1){
                throw new Exception("Invalid Spectrum Price");
            }

            if(invert){
                try{
                    m_price = BigDecimal.ONE.divide(price, 8, RoundingMode.HALF_UP);
                }catch(ArithmeticException e){
                    m_price = BigDecimal.ZERO;
                }
            }else{
                m_price = price;
            }
        }


        public ErgoDexPrice(BigDecimal price, long timeStamp){
            m_price = price;
            m_timeStamp = timeStamp;
        }
        public void invert(){
            if(!m_price.equals(BigDecimal.ZERO)){
                m_price = BigDecimal.ONE.divide(m_price, 15, RoundingMode.HALF_UP);
            }
        }

        public BigDecimal getPrice(){
            
            return m_price;
        }

        public long getTimeStamp(){
            return m_timeStamp;
        }

        public BigDecimal getInvertedPrice() throws ArithmeticException{
       
            return BigDecimal.ONE.divide(m_price, 15, RoundingMode.HALF_UP);
            
        }

        public ErgoDexPrice getInverted() throws ArithmeticException{
            return new ErgoDexPrice(getInvertedPrice(), m_timeStamp);
        }

        public JsonObject getInvertedJson(){
            JsonObject json = new JsonObject();
            json.addProperty("price", getInvertedPrice());
            json.addProperty("timestamp", m_timeStamp);
            return json;
        }
        public JsonObject getJson(){
            JsonObject json = new JsonObject();
            json.addProperty("price", getPrice());
            json.addProperty("timestamp", m_timeStamp);
            return json;
        }
    }