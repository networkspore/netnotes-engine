package io.netnotes.engine;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

import javafx.beans.property.SimpleObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.apps.AppConstants;
import io.netnotes.engine.networks.ergo.ErgoCurrency;
import io.netnotes.engine.networks.ergo.ErgoTokenInfo;

public class PriceCurrency {

    public final static int VALID_TIMEOUT = 1000*60;
    public final static String DEFAULT_TOKEN_URL = "https://explorer.ergoplatform.com/en/token/";
    private String m_boxId = "";
    private String m_defaultCurrencyName = ""; 
    private String m_tokenId = null;
    private String m_symbol = null;
    private String m_name = null;
    private String m_imageString =  Stages.UNKNOWN_IMAGE_PATH;
    private int m_fractionalPrecision = 2;
    private String m_networkType = null;
    
    private String m_fontSymbol = null;
    private String m_description = null;
    private long m_emissionAmount = -1;
    private String m_tokenType = null;
    private String m_url = null;

    private HashData m_hashData = null;

    public final SimpleObjectProperty<LocalDateTime> m_lastUpdated = new SimpleObjectProperty<>(null); 

    public PriceCurrency(String tokenId, String name, int decimals, String tokenType, String networkType){
        m_tokenId = tokenId;
        m_name = name;
        m_symbol = name;
        m_tokenType = tokenType;
        m_imageString = Stages.UNKNOWN_IMAGE_PATH;
        m_fractionalPrecision = decimals;
        m_networkType = networkType;
        m_url = !m_tokenId.equals(ErgoCurrency.TOKEN_ID) ? DEFAULT_TOKEN_URL + m_tokenId : ErgoCurrency.URL_STRING;
    }


    public PriceCurrency(JsonObject json){
        openJson(json);

    }

   /* public void setErgoTokens(ErgoTokens ergoTokens){
        m_ergoTokens = ergoTokens;
        update();
    } */


    public PriceCurrency(String token_id, String name, String symbol, String description, int fractionalPrecision, String unitImageString, String networkType, long emissionAmount, long timestamp) {
        this(token_id, name, symbol,  fractionalPrecision, networkType, unitImageString, null,null);
        m_description = description;
        m_emissionAmount = emissionAmount;
    }

    public PriceCurrency(String token_id, String name, String symbol, int fractionalPrecision,  String networkType, String unitImageString, String tokenType, String fontSymbol) {
        m_tokenId = token_id;
        m_name = name;
        m_symbol = symbol;
        m_imageString = unitImageString;
        m_fractionalPrecision = fractionalPrecision;
        m_fontSymbol = fontSymbol;
        m_tokenType = tokenType;
        m_networkType = networkType;

        if( m_imageString != null && !m_imageString.startsWith("/assets")){
            File file = new File(m_imageString);
            if(file.isFile()){
                try {

                    m_hashData = new HashData(file);
                } catch (IOException e) {

                    try {
                        Files.writeString(new File("netnotes-log.txt").toPath(), "\nPrice currency " + m_name + " hashdata failed.", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException e1) {

                    }
                    m_hashData = null;
                }

            }else{
                m_hashData = null;
            }        
        }
    }

    public void openJson(JsonObject json) {

        JsonElement emissionElement = json.get("emissionAmount");
        JsonElement descriptionElement = json.get("description");
        JsonElement decimalsElement = json.get("decimals");
        JsonElement symbolElement = json.get("symbol");
        JsonElement fontSymbolElement = json.get("fontSymbol");
        JsonElement imageStringElement = json.get("imageString");
        JsonElement hashDataElement = json.get("hashData");
        JsonElement tokenIdElement = json.get("tokenId");
        JsonElement boxIdElement = json.get("boxId");
        JsonElement urlElement = json.get("url");
        
        m_boxId = boxIdElement != null && !boxIdElement.isJsonNull() ? boxIdElement.getAsString() : m_boxId;
        m_tokenId = tokenIdElement != null && !tokenIdElement.isJsonNull() ? tokenIdElement.getAsString() : m_tokenId;

        m_fractionalPrecision = decimalsElement.getAsInt();
        m_symbol = symbolElement != null && !symbolElement.isJsonNull() ? symbolElement.getAsString() : m_symbol;
        m_emissionAmount = emissionElement != null && emissionElement.isJsonPrimitive() ? emissionElement.getAsLong() : -1;
        m_description = descriptionElement != null && descriptionElement.isJsonPrimitive() ? descriptionElement.getAsString(): null;
        m_fontSymbol = fontSymbolElement != null && fontSymbolElement.isJsonPrimitive() ? fontSymbolElement.getAsString() : null;

        if (hashDataElement != null && hashDataElement.isJsonObject()) {
            m_hashData = new HashData(hashDataElement.getAsJsonObject());
        }

        if (imageStringElement != null && getImgHashData() != null) {
            setImageString(imageStringElement.getAsString());
        }
        m_url = urlElement != null && !urlElement.isJsonNull() ? urlElement.getAsString() :( !m_tokenId.equals(ErgoCurrency.TOKEN_ID) ? DEFAULT_TOKEN_URL + m_tokenId : ErgoCurrency.URL_STRING);
    }
        


    public String getDefaultName(){
        return m_defaultCurrencyName;
    }

    public void setDefaultName(String name){
        m_defaultCurrencyName = name;
    }

    public HashData getImgHashData(){
        return m_hashData;
    }

    public void setImgHashData(HashData hashData){
        m_hashData = hashData;
    }

    public SimpleObjectProperty<LocalDateTime> getLastUpdated(){
        return m_lastUpdated;
    }

    public String getTokenType(){
        return m_tokenType;
    }

    public void setTokenType(String tokenType){
        m_tokenType = tokenType;
    }
   
    public void setDecimals(int decimals){
        m_fractionalPrecision = decimals;
    }

    public int getDecimals(){
        return m_fractionalPrecision;
    }


    public void setEmissionAmount(long amount){
        m_emissionAmount = amount;
    }
    public long getEmissionAmount(){
        return m_emissionAmount;
    }

    public void setDescription(String description){
        m_description = description;
    }

    public String getDescription(){
        return m_description;
    }

    public String getNetworkTypeString(){
        return m_networkType;
    }
    
    public String getFontSymbol(){
        return m_fontSymbol;
    }
  
    public void setUrl(String url){
        m_url = url;
    }
    public String getUrl(){
        
        return m_url;
    }
 

    public String getTokenId() {
        return m_tokenId;
    }

    public Image getIcon() {
        if (m_symbol != null && m_name != null && m_imageString != null) {
            if(m_imageString.startsWith("/assets")){
                return new Image(m_imageString);
            }else{
                return Utils.checkAndLoadImage(getImageString(), getImgHashData());
            }

        } else {
            return getUnknownUnitImage();
        }
    }

    private static BufferedImage m_bgImg = null;
    private static Graphics2D m_g2d = null;

    public static Image getBlankBgIcon(double height, String symbol){
        java.awt.Font font = new java.awt.Font("OCR A Extended", java.awt.Font.BOLD, 10);

        
        m_bgImg = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        m_g2d = m_bgImg.createGraphics();
        m_g2d.setFont(font);
        FontMetrics fontMetrics = m_g2d.getFontMetrics();
        //int fontHeight = fontMetrics.getHeight();
        //int fontAscent = fontMetrics.getAscent();
        int symbolStringWidth = fontMetrics.stringWidth(symbol) + 10;

        m_bgImg = new BufferedImage(symbolStringWidth,(int) height, BufferedImage.TYPE_INT_ARGB);

        m_g2d = m_bgImg.createGraphics();
        m_g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        m_g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        m_g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        m_g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        m_g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        m_g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        m_g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        m_g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        m_g2d.setColor(new Color(0xcdd4da));
        m_g2d.drawString(symbol, 5, (int)height - fontMetrics.getDescent());

        Image img =  SwingFXUtils.toFXImage(m_bgImg, null);
        m_g2d.dispose();
        m_bgImg = null;
        return img;
    }

    public Image getBackgroundIcon(double height){
        String symbol = getSymbol();
        java.awt.Font font = new java.awt.Font("OCR A Extended", java.awt.Font.BOLD, 10);

        
        BufferedImage unitImage = SwingFXUtils.fromFXImage( getIcon(), null);
        Drawing.setImageAlpha(unitImage, 0x30);

        int imgWidth = unitImage.getWidth();
        int imgHeight = unitImage.getHeight();
        
        Dimension scaledDimension = Drawing.getScaledDimension(new Dimension(imgWidth, imgHeight),new Dimension((int)(height + (height*.5)), (int)(height + (height*.5))));
        
        m_bgImg = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        m_g2d = m_bgImg.createGraphics();
        m_g2d.setFont(font);
        FontMetrics fontMetrics = m_g2d.getFontMetrics();
        //int fontHeight = fontMetrics.getHeight();
        //int fontAscent = fontMetrics.getAscent();
        int symbolStringWidth = fontMetrics.stringWidth(symbol) + 10;

        m_bgImg = new BufferedImage(imgWidth < symbolStringWidth ? symbolStringWidth : imgWidth,(int) height, BufferedImage.TYPE_INT_ARGB);

        m_g2d = m_bgImg.createGraphics();
        m_g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        m_g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        m_g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        m_g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        m_g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        m_g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        m_g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        m_g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        m_g2d.drawImage(unitImage, (int)-Math.ceil(imgWidth *.2)  , (int) ((height / 2) - (scaledDimension.getHeight() / 2)), (int) scaledDimension.getWidth(), (int) scaledDimension.getHeight(), null);

        m_g2d.setColor(new Color(0xcdd4da));
        m_g2d.drawString(symbol, 5, (int)height - fontMetrics.getDescent());

        Image img =  SwingFXUtils.toFXImage(m_bgImg, null);
        m_g2d.dispose();
        m_bgImg = null;
        return img;
    }

    public static Image getUnknownUnitImage() {
        return Stages.unknownImg;
    }

    public String getImageString(){
        return m_imageString;
    }
    

    public void setImageString(String imgString){
        m_imageString = imgString;
    }

    public void setNetworkType(String networkType){
        m_networkType = networkType;
    }

    public String getName() {
        return m_name;
    }

    public void setName(String name){
        m_name = name;
    }

    public String getSymbol() {
        String symbol = m_symbol != null ? m_symbol : (m_name != null ? m_name : "unknown");

        int space = symbol != null ? symbol.indexOf(" ") : -1;
        
        return  space != -1 ? m_symbol.substring(0, space) : m_symbol;
    }

    public void setSymbol(String symbol){
        m_symbol = symbol;
    }


    public int getFractionalPrecision() {
        return m_fractionalPrecision;
    }

    @Override
    public String toString() {
        return m_symbol;
    }

    public void setTokenInfo(JsonObject tokenInfoJson){
        if(tokenInfoJson != null){
            setTokenInfo(new ErgoTokenInfo(tokenInfoJson));
        }
    }

    public void setTokenInfo(ErgoTokenInfo info){
        if(info.getTokenId().equals(m_tokenId)){
            m_boxId = info.getBoxId();
            m_emissionAmount = info.getEmissionAmount();
            m_description = info.getDescription();
            m_tokenType = info.getTokenType();
            m_name = info.getName();
        }
    }

    public ErgoTokenInfo getTokenInfo(){
        return new ErgoTokenInfo(m_tokenId, m_boxId, m_emissionAmount, m_name, m_description, m_tokenType, m_fractionalPrecision);
    }

    public PriceCurrency(JsonReader reader) throws IOException{
        reader.beginObject();
        while(reader.hasNext()){
            switch(reader.nextName()){
                case "tokenId":
                    m_tokenId = reader.nextString();
                break;
                case "emissionAmount":
                    m_emissionAmount = reader.nextLong();
                break;
                case "boxId":
                    m_boxId = reader.nextString();
                break;
                case "name":
                    m_name = reader.nextName();
                break;
                case "symbol":
                    m_symbol = reader.nextString();
                break;
                case "description":
                    m_description = reader.nextString();
                break;
                case "decimals":
                    m_fractionalPrecision = reader.nextInt();
                break;
                case "networkType":
                    m_networkType = reader.nextString();
                break;
                case "imageString":
                    m_imageString = reader.nextString();
                break;
                case "fontSymbol":
                    m_fontSymbol = reader.nextString();
                break;
                case "tokenType":
                    m_tokenType = reader.nextString();
                break;
                case "hashData":
                    m_hashData = new HashData(reader);
                break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }

    public void writeJson(JsonWriter writer) throws IOException{
        writer.beginObject();
        writer.name( "tokenId");
        writer.value(m_tokenId);
        writer.name( "name");
        writer.value(m_name);
        writer.name( "decimals");
        writer.value(m_fractionalPrecision); 
        if(m_emissionAmount != -1){
            writer.name( "emissionAmount");
            writer.value(m_emissionAmount);
        }
        if(m_boxId != null){
            writer.name( "boxId");
            writer.value(m_boxId);
        }
        if(m_symbol != null){
            writer.name( "symbol");
            writer.value( m_symbol); 
        }
        if(m_description != null){
            writer.name( "description");
            writer.value(m_description); 
        }
        if(m_networkType != null){
            writer.name( "networkType");
            writer.value(m_networkType); 
        }
        if(m_imageString != null){
            writer.name( "imageString");
            writer.value(m_imageString);
        }
        if(m_fontSymbol != null){
            writer.name( "fontSymbol");
            writer.value(m_fontSymbol); 
        }
        if(m_tokenType != null){
            writer.name( "tokenType");
            writer.value(m_tokenType); 
        }
        if(m_hashData != null){
            writer.name("hashData");
            m_hashData.writeJson(writer); 
        }
        writer.endObject();
    }

    public JsonObject getJsonObject(){
        JsonObject json = new JsonObject();
      
        json.addProperty("tokenId", m_tokenId);
        if(m_emissionAmount != -1){
            json.addProperty("emissionAmount", m_emissionAmount);
        }
        if(m_boxId != null){
            json.addProperty("boxId", m_boxId);
        }
        json.addProperty("name", m_name);
        if(m_symbol != null){
            json.addProperty("symbol", m_symbol);
        }
        if(m_description != null){
            json.addProperty("description", m_description);
        }
        json.addProperty("decimals", m_fractionalPrecision);
        if(m_networkType != null){
            json.addProperty("networkType", m_networkType);
        }
      
        
        if(m_imageString != null){
            json.addProperty("imageString",m_imageString);
        }
        if(m_fontSymbol != null){
            json.addProperty("fontSymbol", m_fontSymbol);
        }
        if(m_tokenType != null){
            json.addProperty("tokenType",m_tokenType);
        }
        if (getImgHashData() != null) {
            json.add("hashData", getImgHashData().getJsonObject());
        }
        if(m_url != null){
            json.addProperty("url", m_url);
        }

        return json;
    }
}
