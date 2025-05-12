package io.netnotes.engine;


import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.ergoplatform.appkit.NetworkType;
import org.ergoplatform.sdk.ErgoToken;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.networks.ergo.ErgoBoxAsset;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;

public class PriceAmount  {
    private long m_timeout = NoteConstants.QUOTE_TIMEOUT;


    private final SimpleObjectProperty<BigDecimal> m_amount = new SimpleObjectProperty<>();
    private final PriceCurrency m_currency;
    
    private long m_created;
    private SimpleLongProperty m_timeStampProperty = new SimpleLongProperty(System.currentTimeMillis());

    private final boolean m_readonly;

    private PriceQuote m_priceQuote = null;

    public PriceAmount(BigDecimal amount, PriceCurrency priceCurrency){
        m_currency = priceCurrency;
        m_amount.set(amount);
        m_created = System.currentTimeMillis();
        m_readonly = false;
    }

    public PriceAmount(long amount, PriceCurrency currency) {
        this(amount, currency, false); 
    }

    public PriceAmount(long amount, PriceCurrency currency, boolean readonly) {
        m_currency = currency;
        BigDecimal bigDecimal = calculateLongToBigDecimal(amount, currency.getDecimals());
        m_amount.set(bigDecimal);
        m_created = System.currentTimeMillis();
        m_readonly = readonly;
    }

    public PriceAmount(ErgoBoxAsset asset, boolean readonly){
        this(asset, NetworkType.MAINNET.toString(), readonly);
    }

    public PriceAmount(ErgoBoxAsset asset, String networkType, boolean readonly){
        this(asset.getAmount(), new PriceCurrency(asset.getTokenId(), asset.getName(), asset.getDecimals(), asset.getType(), networkType), readonly);
      
    }

    public PriceAmount(double amount, PriceCurrency currency) {
        this(BigDecimal.valueOf(amount), currency);
    }

    public PriceAmount(long amount, PriceCurrency currency, long timeStamp){
        this(amount, currency, timeStamp, false);
    }

    public PriceAmount(long amount, PriceCurrency currency, long timeStamp, boolean readonly){
        this(calculateLongToBigDecimal(amount, currency.getDecimals()), currency, timeStamp, readonly);
    }

    public PriceAmount(BigDecimal amount, PriceCurrency currency, long timeStamp, boolean readonly){
        m_currency = currency;
        m_amount.set(amount);
        m_created = System.currentTimeMillis();
        m_readonly = readonly;
    }

    public PriceAmount(JsonObject json) throws Exception{
        
        JsonElement amountElement = json.get("amount");
        JsonElement timeStampElement = json.get("timeStamp");
        JsonElement currencyObjectElement = json.get("currency");
        JsonElement readonlyElement = json.get("readOnly");
        if(amountElement == null || timeStampElement == null  || currencyObjectElement == null || (currencyObjectElement != null && !currencyObjectElement.isJsonObject())){
            throw new Exception("Null argument");
        }

        JsonObject currencyObject = currencyObjectElement.getAsJsonObject();
        JsonElement tokenIdElement = currencyObject.get("tokenId");
        JsonElement nameElement = currencyObject.get("name");
        JsonElement decimalsElement = currencyObject.get("decimals");
        JsonElement tokenTypeElement = currencyObject.get("tokenType");
        JsonElement networkTypeElement = currencyObject.get("networkType");
        

        String tokenId = tokenIdElement != null && tokenIdElement.isJsonPrimitive() ? tokenIdElement.getAsString() : null;
        int decimals = decimalsElement != null && decimalsElement.isJsonPrimitive() ? decimalsElement.getAsInt() : -1;

        if(tokenId == null || decimals == -1){
            throw new Exception("currency null");
        }
        String name = nameElement != null && nameElement.isJsonPrimitive() ? nameElement.getAsString() : "null"; 
        String tokenType = tokenTypeElement != null && tokenTypeElement.isJsonPrimitive() ? tokenTypeElement.getAsString() : null;
        String networkType = networkTypeElement != null && networkTypeElement.isJsonPrimitive() ? networkTypeElement.getAsString() : NetworkType.MAINNET.toString();
        PriceCurrency importCurrency = new PriceCurrency(tokenId,name,decimals,tokenType, networkType);


        m_currency = importCurrency;
        BigDecimal bigDecimal = calculateLongToBigDecimal(amountElement.getAsLong(), decimals);
        m_amount.set(bigDecimal);
        m_created = timeStampElement.getAsLong();
        m_readonly = readonlyElement != null && !readonlyElement.isJsonNull() ? readonlyElement.getAsBoolean() : false;
        
    }
    

    public JsonObject getAmountObject(){
        String name = getCurrency().getName();
        int decimals = getCurrency().getDecimals();
        String networkType = getCurrency().getNetworkTypeString();

        JsonObject json = new JsonObject();
        json.addProperty("decimalAmount", getBigDecimalAmount());
        json.addProperty("value", getLongAmount());
        if(name != null){
            json.addProperty("name", name);
        }
        json.addProperty("id", getCurrency().getTokenId());
        json.addProperty("decimals", decimals);
        if(networkType != null){
            json.addProperty("networkType", networkType);
        }

        return json;
    }

    public static PriceAmount getAmountFromObject(JsonObject json){

        JsonElement valueElement = json.get("value");
        JsonElement nameElement = json.get("name");
        JsonElement idElement = json.get("id");
        JsonElement decimalsElement = json.get("decimals");
        JsonElement networkTypeElement = json.get("networkType");

        if(valueElement == null || idElement == null){
            return null;
        }

        String name = nameElement != null ? nameElement.getAsString() : "";
        String id = idElement.getAsString();
        int decimals = decimalsElement != null ? decimalsElement.getAsInt() : 0;
        String networkType = networkTypeElement != null ? networkTypeElement.getAsString() : "";


        return new PriceAmount(valueElement.getAsLong(), new PriceCurrency(id, name, decimals, null, networkType));
        
    }

    public static ErgoToken getErgoToken(JsonObject json){

        JsonElement longAmountElement = json.get("value");
        JsonElement tokenIdElement = json.get("id");
        
        return longAmountElement != null && !longAmountElement.isJsonNull() && tokenIdElement != null && !tokenIdElement.isJsonNull() ? new ErgoToken(tokenIdElement.getAsString(), longAmountElement.getAsLong()) : null;
    }

    public ErgoToken getErgoToken(){
        return new ErgoToken(getTokenId(), getLongAmount());
    }

    public static PriceAmount getByAmountObject(JsonObject json, NetworkType networkType){

        JsonElement decimalElement = json.get("decimalAmount");
        JsonElement longElement = json.get("longAmount");
        JsonElement nameElement = json.get("name");
        JsonElement tokenIdElement = json.get("tokenId");
        JsonElement decimalsElement = json.get("decimals");

        BigDecimal decimalAmount = decimalElement != null ? decimalElement.getAsBigDecimal() : null;
        long longAmount = decimalAmount == null && longElement != null ? longElement.getAsLong() : -1;

        if(decimalAmount == null && longAmount == -1){
            return null;
        }

        String name = nameElement != null ? nameElement.getAsString() : null;
        String tokenId = tokenIdElement != null ? tokenIdElement.getAsString() : null;
        int decimals = decimalsElement != null ? decimalsElement.getAsInt() : -1;

        if(name == null || tokenId == null || decimals == -1){
            return null;
        }
        PriceCurrency currency = new PriceCurrency(tokenId, name, decimals, "simple", networkType.toString());

        return decimalAmount != null ? new PriceAmount(decimalAmount, currency) : new PriceAmount(longAmount, currency);
        
    }

    public PriceQuote getPriceQuote(){
        return m_priceQuote;
    }

    public void setPriceQuote(PriceQuote priceQuote){
        m_priceQuote = priceQuote;
    }
   
    public String getName(){
        return m_currency.getName();
    }

    public String getSymbol(){
        return m_currency.getSymbol();
    }

    public long getTimeStamp(){
        return m_timeStampProperty.get();
    }

    public void setTimeStamp(long timestamp){
        m_timeStampProperty.set(timestamp);
        
    }

    public SimpleLongProperty timeStampProperty(){
        return m_timeStampProperty;
    }
    public boolean getAmountValid(){
        return getAmountValid(System.currentTimeMillis());
    }
    
    public boolean getAmountValid(long timestamp){
        return  ((timestamp - getTimeStamp())) < m_timeout;
    }


    public BigDecimal getBigDecimalAmount(){
        return m_amount.get();
    }

    /*public void setBigDecimalAmount(BigDecimal amount) {

        m_amount.set(amount);
        m_timeStampProperty.set(System.currentTimeMillis());
    } */

    public boolean isReadonly(){
        return m_readonly;
    }

    public void setBigDecimalAmount(BigDecimal amount, long timestamp) {
        if(!m_readonly){
            m_amount.set(amount);
            m_timeStampProperty.set(timestamp);
        }
    }

    public void addBigDecimalAmount(BigDecimal amount){
        if(!isReadonly()){
            BigDecimal a = m_amount.get() != null ? m_amount.get() : BigDecimal.ZERO;
            BigDecimal newVal = a.add(amount);

            m_amount.set(newVal);
        }
    }


    public PriceCurrency getCurrency(){
        return m_currency;
    }

    

    public String getTokenId(){
        return m_currency.getTokenId();
    }
    
    public ReadOnlyObjectProperty<BigDecimal> amountProperty(){
        return m_amount;
    }

    public double getDoubleAmount() {
        if(m_amount.get() == null || m_currency == null){
            return 0;
        }
        return m_amount.get() != null ? m_amount.get().doubleValue() : 0;
    }


    public static BigDecimal calculateLongToBigDecimal(long amount, int decimals){
        
        if(amount == 0){
            return BigDecimal.ZERO;
        }
       
        BigDecimal bigAmount = BigDecimal.valueOf(amount);

        if(decimals > 0){
            BigDecimal pow = BigDecimal.valueOf(10).pow(decimals);
            return bigAmount.divide(pow);
        }else{
            return bigAmount;
        }
    }

    public void setLongAmount(long amount) {
        if(!isReadonly()){
            setLongAmount(amount,  System.currentTimeMillis());
        }
    }

    public void setLongAmount(long longAmount, long timeStamp) {
        if(!isReadonly()){
            BigDecimal amount = calculateLongToBigDecimal(longAmount, getDecimals());


            m_amount.set(amount);
            m_timeStampProperty.set(timeStamp);
        }
    }

    public void addLongAmount(long longAmount){
        if(!isReadonly()){
            BigDecimal bigAmount = calculateLongToBigDecimal(longAmount, getDecimals());
            addBigDecimalAmount(bigAmount);
        }
    }

    public int getDecimals(){
        return getCurrency().getDecimals();
    }

    public long getLongAmount() {
        return calculateBigDecimalToLong(m_amount.get(), getCurrency().getDecimals());
    }

    public static long calculateBigDecimalToLong(BigDecimal amount, int decimals){
        if(amount == null || amount.equals(BigDecimal.ZERO) || decimals == -1){
            return 0;
        }

        BigDecimal pow = BigDecimal.valueOf(10).pow(decimals);

        return amount.multiply(pow).longValue();
    }

    public void setDoubleAmount(double doubleAmount) {
        if(!isReadonly()){
            BigDecimal amount = BigDecimal.valueOf(doubleAmount);
            m_amount.set(amount);
        }
    }



    public LocalDateTime getCreatedTime() {
        return Utils.milliToLocalTime(m_created);
    }

    public String getAmountString(){
        BigDecimal amount = m_amount.get();
        return amount == null ? "0" : amount.toPlainString();
    }

    @Override
    public String toString() {
        

        return getAmountString() + (m_currency != null ? " " + getCurrency().getSymbol() : "");
    }

    public static PriceAmount readPriceAmount(JsonReader reader) throws IOException{
        SimpleLongProperty readAmount = new SimpleLongProperty(0);
        SimpleObjectProperty<PriceCurrency> currencyAmount = new SimpleObjectProperty<>(null);
        SimpleLongProperty readCreated = new SimpleLongProperty(-1);
        reader.beginObject();
        while(reader.hasNext()){
            switch(reader.nextName()){
                case "amount":
                    readAmount.set(reader.nextLong());
                break;
                case "timeStamp":
                    readCreated.set(reader.nextLong());
                break;
                case "currency":
                    currencyAmount.set(new PriceCurrency(reader));
                break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
        
        return currencyAmount.get() != null ? (readCreated.get() != -1 ? new PriceAmount(readAmount.get(), currencyAmount.get(), readCreated.get() ) : new PriceAmount(readAmount.get(), currencyAmount.get()))  : null;
    }

    public void writeJson(JsonWriter writer) throws IOException{
        writer.beginObject();
        writer.name("amount");
        writer.value(getLongAmount());
        writer.name("timeStamp");
        writer.value(m_created);
        writer.name("currency");
        m_currency.writeJson(writer);
        writer.endObject();
    }

    public JsonObject getJsonObject(){
        long amount = getLongAmount();

        JsonObject json = new JsonObject();
        json.addProperty("amount", amount);
        json.addProperty("timeStamp", m_created);
        json.add("currency", m_currency == null ? null : getCurrency().getJsonObject());
        return json;
    }

    public String getTokenType(){
        return m_currency.getTokenType();
    }

    public JsonObject getBalanceObject(){

        JsonObject json = new JsonObject();

        json.addProperty("tokenId", getTokenId());
        json.addProperty("amount",  getLongAmount());
        json.addProperty("decimals", getDecimals());
        json.addProperty("name", getName());
        json.addProperty("tokenType", getTokenType());

        return json;

    }


}
