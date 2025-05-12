package io.netnotes.engine;

import org.ergoplatform.appkit.ConstantsBuilder;
import org.ergoplatform.appkit.ErgoValue;
import org.ergoplatform.appkit.NetworkType;
import org.ergoplatform.appkit.Constants;
import org.ergoplatform.sdk.ErgoId;
import org.ergoplatform.sdk.ErgoToken;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.ergoplatform.appkit.Address;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import io.netnotes.engine.networks.ergo.AddressInformation;
import io.netnotes.engine.networks.ergo.ErgoAmount;
import io.netnotes.engine.networks.ergo.ErgoBoxAsset;
import io.netnotes.engine.networks.ergo.ErgoCurrency;
import io.netnotes.engine.networks.ergo.ErgoInputData;
import io.netnotes.engine.networks.ergo.ErgoNetworkUrl;
import io.netnotes.notes.ParamTypes;
import scorex.util.encode.Base16;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public class NoteConstants {
    public final static String WALLET_ADDRESS_PK = "walletAddressPK";
    public final static String WALLET_ADDRESS_PROP_BYTES = "walletAddressPropBytes";
    public final static String CURRENT_WALLET_FILE = "wallet file";
        

    public final static long POLLING_TIME = 7000;
    public final static long QUOTE_TIMEOUT = POLLING_TIME*2;
    


    public static final int SUCCESS = 1;
    public static final int ERROR = 2;

    public static final int DISABLED = -1;
    public static final int STARTING = 3;
    public static final int STARTED = 4;

    public static final int STOPPING = 5;
    public static final int STOPPED = 6;
    public static final int SHUTDOWN = 7;

    public static final int WARNING = 8;
    public static final int STATUS = 9;
    public static final int UPDATING = 10;
    public static final int UPDATED = 11;
    public static final int INFO = 12;
    public static final int CANCEL = 13;
    public static final int READY = 14;

    
    public static final int LIST_CHANGED = 20;
    public static final int LIST_CHECKED = 21;
    public static final int LIST_UPDATED = 22;
    public static final int LIST_ITEM_ADDED = 23;
    public static final int LIST_ITEM_REMOVED = 24;
    public static final int LIST_DEFAULT_CHANGED= 25;

    public static final String STATIC_TYPE = "STATIC";
    public static final String STATUS_MINIMIZED = "Minimized";
    public static final String STATUS_UPDATED = "Updated";
    public static final String STATUS_STOPPED = "Stopped";
    public static final String STATUS_STARTED = "Started";
    public static final String STATUS_STARTING = "Starting";
    public static final String STATUS_UNAVAILABLE = "Unavailable";
    public static final String STATUS_AVAILABLE = "Available";
    public static final String STATUS_ERROR = "Error";
    public static final String STATUS_SHUTTING_DOWN = "Shutting Down";
    public static final String STATUS_SHUTDOWN = "Shutdown";
    public static final String STATUS_READY = "Ready";
    public static final String STATUS_TIMED_OUT = "Timed Out";
    public static final String STATUS_DISABLED = "Disabled";
    public static final String STATUS_UNKNOWN = "Unknown";

    public static final String CMD = "cmd";

    public static String getStatusCodeMsg(int status){
        switch(status){
            case NoteConstants.READY:
                return STATUS_READY;
            case NoteConstants.WARNING:
                return STATUS_TIMED_OUT;
            case NoteConstants.STARTING:
                return STATUS_STARTING;
            case NoteConstants.DISABLED:
                return STATUS_DISABLED;
            case NoteConstants.STOPPING:
                return STATUS_SHUTTING_DOWN;
            case NoteConstants.SHUTDOWN:
                return STATUS_SHUTDOWN;
            case NoteConstants.STOPPED:
                return STATUS_STOPPED;
            case NoteConstants.STARTED:
                return STATUS_STARTED;
            default:
                return STATUS_UNKNOWN;
        }
    }

    public static JsonObject getLongConstant(String propertyName, long value){
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", value);
        json.addProperty("type", ParamTypes.LONG_TYPE);
        return json;
    }

    public static JsonObject getIntContractProperty(String propertyName, int value){
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", value);
        json.addProperty("type", ParamTypes.INT_TYPE);
        return json;
    }

    public static JsonObject getBigIntegerContractProperty(String propertyName, BigInteger value){
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", value);
        json.addProperty("type", ParamTypes.BIG_INT_TYPE);
        return json;
    }

    public static JsonObject getBooleanContractProperty(String propertyName, boolean value){
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", value);
        json.addProperty("type", ParamTypes.BOOLEAN_TYPE);
        return json;
    }

    public static JsonObject getAddressPropBytesContractProperty(String propertyName, String base58Address)throws Exception{
        String b58Test = base58Address.replaceAll("[^A-HJ-NP-Za-km-z0-9]", "");
        if(!b58Test.equals(base58Address)){
        throw new Exception("Cannot add contract property because it is not base58");
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", base58Address);
        json.addProperty("type", ParamTypes.ADDRESS_PROP_BYTES);
        return json;
    }

    public static JsonObject getAddressPublicKeyContractProperty(String propertyName, String base58Address) throws Exception{
        String b58Test = base58Address.replaceAll("[^A-HJ-NP-Za-km-z0-9]", "");
        if(!b58Test.equals(base58Address)){
        throw new Exception("Cannot add contract property because it is not base58");
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", base58Address);
        json.addProperty("type", ParamTypes.ADDRESS_PUBLIC_KEY);
        return json;
    }

    public static boolean isBase16(String base16){
        String b16Test = base16.replaceAll("[^A-Fa-f0-9]", "");
        if(b16Test.equals(base16)){
            return true;
        }
        return false;
    }

    public static JsonObject getHexBytesContractProperty(String propertyName, String base16) throws Exception{

        if(!isBase16(base16)){
            throw new Exception("Cannot add contract property because it is not base16");
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", base16);
        json.addProperty("type", ParamTypes.HEX_BYTES);
        return json;
    }

    public static JsonObject getByteContractProperty(String propertyName, byte b){
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", b);
        json.addProperty("type", ParamTypes.BYTE_TYPE);
        return json;
    }

    public static JsonObject getTokenIdBytesConstant(String propertyName, String tokenId)throws Exception{
        String b58Test = tokenId.replaceAll("[^A-HJ-NP-Za-km-z0-9]", "");
        if(!b58Test.equals(tokenId)){
        throw new Exception("Cannot add contract property because tokenId it is not base58");
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", tokenId);
        json.addProperty("type", ParamTypes.TOKEN_ID_BYTES);
        return json;
    }



    public static JsonObject getLongContractProperty(String propertyName, long value, int index){
        JsonObject json = getLongConstant(propertyName, value);
        json.addProperty("index", index);
        return json;
    }

    public static JsonObject getIntContractProperty(String propertyName, int value, int index){
        JsonObject json = getIntContractProperty(propertyName, value);
        json.addProperty("index", index);
        return json;
    }
    public static JsonObject getBigIntegerContractProperty(String propertyName, BigInteger value, int index){
        JsonObject json = getBigIntegerContractProperty(propertyName, value);
        json.addProperty("index", index);
        return json;
    }

    public static JsonObject getBooleanContractProperty(String propertyName, boolean value, int index){
        JsonObject json = getBooleanContractProperty(propertyName, value);
        json.addProperty("index", index);
        return json;
    }


    public static JsonObject getAddressPropBytesContractProperty(String propertyName, String address, int index)throws Exception{
        JsonObject json =  getAddressPropBytesContractProperty(propertyName, address);
        json.addProperty("index", index);
        return json;
    }

    public static JsonObject getAddressPublicKeyContractProperty(String propertyName, String address, int index)throws Exception{
        JsonObject json =  getAddressPublicKeyContractProperty(propertyName, address);
        json.addProperty("index", index);
        return json;
    }

    public static JsonObject getHexBytesContractProperty(String propertyName, String base16, int index) throws Exception{
        JsonObject json =  getHexBytesContractProperty(propertyName, base16);
        json.addProperty("index", index);
        return json;
    }

    public static JsonObject getByteContractProperty(String propertyName, byte b, int index){
        JsonObject json =  getByteContractProperty(propertyName, b);
        json.addProperty("index", index);
        return json;
    }

    public static JsonObject getTokenIdBytesContractProperty(String propertyName, String base58, int index)throws Exception{
        JsonObject json =  getTokenIdBytesConstant(propertyName, base58);
        json.addProperty("index", index);
        return json;
    }

        public static JsonObject replaceConstantObject(JsonElement indexElement, JsonElement nameElement, JsonElement valueElement, String newType){
            JsonObject json = new JsonObject();
            json.add("name", nameElement);
            if(indexElement != null){
                json.add("index", indexElement);
            }
            json.addProperty("type", newType);
            json.add("value", valueElement);
            return json;   
        }

        public static void addConstant(String name, String constantType, JsonElement valueElement, ConstantsBuilder builder) {
                
            switch(constantType){

                case ParamTypes.LONG_TYPE:
                    builder.item(name, valueElement.getAsLong());
                    break;
                case ParamTypes.BIG_INT_TYPE:
                    builder.item(name, valueElement.getAsBigInteger());
                    break;
                case ParamTypes.BYTE_TYPE:
                    builder.item(name, valueElement.getAsByte());
                    break;
                case ParamTypes.INT_TYPE:
                    builder.item(name, valueElement.getAsInt());
                    break;
                case ParamTypes.BOOLEAN_TYPE:
                    builder.item(name,  valueElement.getAsBoolean());
                    break;
                case ParamTypes.ADDRESS_PUBLIC_KEY:
                    builder.item(name,   Address.create(valueElement.getAsString()).getPublicKey());
                    break;
                case ParamTypes.ADDRESS_PROP_BYTES:
                    builder.item(name, Address.create(valueElement.getAsString()).toPropositionBytes());
                    break;
                case ParamTypes.HEX_BYTES: 
                    builder.item(name, Base16.decode(valueElement.getAsString()).get());
                    break;
                case ParamTypes.TOKEN_ID_BYTES:
                    ErgoToken token = new ErgoToken(valueElement.getAsString(), 0L);
                    builder.item(name, token.getId().getBytes());
                    break;

            }
        }


        public static Constants parseConstants(JsonArray constantsArray) {
            
            ConstantsBuilder builder = ConstantsBuilder.create();
            int size = constantsArray.size();

            for(int i = 0; i < size ; i++){
                JsonElement element = constantsArray.get(i);

                JsonObject json = element.getAsJsonObject();
                JsonElement nameElement = json.get("name");
                JsonElement valueElement = json.get("value");
                JsonElement typeElement = json.get("type");

                String constantType = typeElement.getAsString();
                String name = nameElement.getAsString();
                addConstant(name, constantType, valueElement, builder);
            }

            return builder.build();
        }

        public static ErgoValue<?>[] parseConstantsToErgoValue(JsonArray constantsArray){
            ErgoValue<?>[] values = new ErgoValue[constantsArray.size()];
            int size = constantsArray.size();
            for(int i = 0; i < size ; i++){
                JsonElement element = constantsArray.get(i);

                JsonObject json = element.getAsJsonObject();
                JsonElement valueElement = json.get("value");
                JsonElement typeElement = json.get("type");

                String constantType = typeElement.getAsString();
                
                values[i] = getErgoValue(constantType, valueElement);
            }
            return values;
        }

        public static ErgoValue<?> getErgoValue(String constantType, JsonElement valueElement) {
                
            switch(constantType){

                case ParamTypes.LONG_TYPE:
                    return ErgoValue.of(valueElement.getAsLong());
                    
                case ParamTypes.BIG_INT_TYPE:
                    return ErgoValue.of(valueElement.getAsBigInteger());
                    
                case ParamTypes.BYTE_TYPE:
                    return ErgoValue.of(valueElement.getAsByte());
                    
                case ParamTypes.INT_TYPE:
                    return ErgoValue.of(valueElement.getAsInt());
                    
                case ParamTypes.BOOLEAN_TYPE:
                    return ErgoValue.of( valueElement.getAsBoolean());
                    
                case ParamTypes.ADDRESS_PUBLIC_KEY:

                    return ErgoValue.of(Address.create(valueElement.getAsString()).getPublicKey());
                    
                case ParamTypes.ADDRESS_PROP_BYTES:
                    return ErgoValue.of(Address.create(valueElement.getAsString()).toPropositionBytes());
                    
                case ParamTypes.HEX_BYTES: 
                    return ErgoValue.of(Base16.decode(valueElement.getAsString()).get());
                    
                case ParamTypes.TOKEN_ID_BYTES:
                    ErgoToken token = new ErgoToken(valueElement.getAsString(), 0L);
                    return ErgoValue.of(token.getId().getBytes());
                    

            }
            return null;
        }

        public static byte[] getBlankBytes(byte b){
            byte[] poolNFT = new byte[32];
            Arrays.fill(poolNFT, b);
            return poolNFT;
        }

        public static String getBlankPoolIdHex(){
            return Base16.encode(getBlankBytes((byte) 2));
        }

        public static String getBlankTokenIdHex(){
            return Base16.encode(getBlankBytes((byte) 4));
        }

        public static String getBlankSpectrumIdHex(){
            return Base16.encode(getBlankBytes((byte) 3));
        }

        public static String getBlankP2PKPropBytesHex(){
            return Base16.encode(getBlankBytes((byte) 1));
        }



        public static boolean addFeeAmountToDataObject(long nanoErgs, long minAmount, JsonObject dataObject){
            
            JsonObject nanoErgsObject = createNanoErgsObject(nanoErgs);
            nanoErgsObject.addProperty("ergs", PriceAmount.calculateLongToBigDecimal(nanoErgs, ErgoCurrency.DECIMALS));
            dataObject.add("feeAmount", nanoErgsObject);
            return nanoErgs >= minAmount;
        }


        public static void addNanoErgAmountToDataObject(long nanoErgs, JsonObject dataObject){
            JsonObject nanoErgsObject = createNanoErgsObject(nanoErgs);
            nanoErgsObject.addProperty("ergs", PriceAmount.calculateLongToBigDecimal(nanoErgs, ErgoCurrency.DECIMALS));
            dataObject.add("ergAmount", nanoErgsObject);
        }



        public static void addTokensToDataObject(PriceAmount[] priceAmounts, JsonObject dataObject){
            priceAmounts = priceAmounts == null ? new PriceAmount[0] : priceAmounts;
            JsonArray tokenArray = new JsonArray();
            for(int i = 0 ; i < priceAmounts.length ; i++){
                PriceAmount token = priceAmounts[i];
                tokenArray.add(token.getAmountObject());
            }
            dataObject.add("tokens", tokenArray);
        }

        public static void addTokensToDataObject(ErgoToken[] tokens, JsonObject dataObject){
            tokens = tokens == null ? new ErgoToken[0] : tokens;
            JsonArray tokenArray = new JsonArray();
            for(int i = 0 ; i < tokens.length ; i++){
                ErgoToken token = tokens[i];
                JsonObject tokenJson = new JsonObject();
                tokenJson.addProperty("id", token.getId().toString());
                tokenJson.addProperty("value", token.getValue());
                tokenArray.add(tokenJson);
            }
            dataObject.add("tokens", tokenArray);
        }



        public static JsonObject createNanoErgsObject(long nanoErg){
            JsonObject ergoObject = new JsonObject();
            ergoObject.addProperty("nanoErgs", nanoErg);
            return ergoObject;
        }

        public static boolean addFeeAmountToDataObject(PriceAmount ergoAmount, long minAmount, JsonObject dataObject){
            if(!ergoAmount.getTokenId().equals(ErgoCurrency.TOKEN_ID)){
                return false;
            }
            long nanoErgs = ergoAmount.getLongAmount();
            JsonObject nanoErgsObject = createNanoErgsObject(nanoErgs);
            nanoErgsObject.addProperty("ergs", ergoAmount.getBigDecimalAmount());
            dataObject.add("feeAmount", nanoErgsObject);
            return nanoErgs >= minAmount;
        }

        public static void addRegistersToDataObject(JsonObject dataObject, JsonObject... registers){
            JsonArray registersArray = new JsonArray();
            for(int i = 0; i < registers.length ; i++){
                registersArray.add(registers[i]);
            }
            dataObject.add("registers",registersArray);
        }


        
        public static void addNodeUrlToDataObject(JsonObject dataObject, JsonObject nodeJson){
       
            dataObject.add("node", nodeJson);
        }

        public static NamedNodeUrl getNamedNodeUrlFromDataObject(JsonObject dataObject){

            JsonElement nodeElement = dataObject.get("node");

            JsonObject nodeObject = nodeElement != null && nodeElement.isJsonObject() ? nodeElement.getAsJsonObject() :null;
            
            if(nodeObject != null){
                JsonElement namedNodeElement = nodeObject.get("namedNode");
                if( namedNodeElement != null && namedNodeElement.isJsonObject()){
                    JsonObject namedNodeJson = namedNodeElement.getAsJsonObject();
                    
                    NamedNodeUrl namedNode = null;
                    try {
                        namedNode = new NamedNodeUrl(namedNodeJson);
                        return namedNode;
                    }catch(Exception e1){
                        
                    }
                }
            }
        
            return null;
        }

        
        public static ErgoNetworkUrl getExplorerUrl(JsonObject dataObject){
            JsonElement explorerElement = dataObject.get("explorer");

            JsonObject explorerObject = explorerElement != null && explorerElement.isJsonObject() ? explorerElement.getAsJsonObject() : null;
            
            if(explorerObject != null){
        
                JsonElement namedExplorerElement = explorerObject.get("ergoNetworkUrl");

                JsonObject namedExplorerJson = namedExplorerElement.getAsJsonObject();
                
                try {
                    
                    ErgoNetworkUrl explorerUrl = new ErgoNetworkUrl(namedExplorerJson);

                    return explorerUrl;

                } catch (Exception e1) {
                
                }

                
            }
        
            return null;
        }

        public static List<ErgoInputData> getInputsFromDataObject(JsonObject dataObject) throws Exception{
            JsonElement inputsElement = dataObject.get("inputs");
            if(inputsElement == null){
                throw new Exception("Inputs element not found in transaction");
            }
            if(inputsElement.isJsonNull()){
                throw new Exception("Inputs element is JsonNull");
            }
            if(!inputsElement.isJsonArray()){
                throw new Exception("Inputs element is not a JsonArray");
            }
            JsonArray inputJsonArray = inputsElement.getAsJsonArray();
            ErgoInputData[] inputData = new ErgoInputData[inputJsonArray.size()];
            for(int i = 0; i < inputJsonArray.size(); i++){
                JsonElement element = inputJsonArray.get(i);
                if(element.isJsonNull() || !element.isJsonObject()){
                    throw new Exception("InputData element is not a JsonObject");
                }
                inputData[i] = new ErgoInputData(element.getAsJsonObject());
            }

            return List.of(inputData);
        }


        public static JsonObject getChangeAddressObjectFromDataObject(JsonObject dataObject){
            return getAddressObjectFromDataObject("changeAddress", dataObject);
        }
        
        public static JsonObject getAddressObjectFromDataObject(String property, JsonObject dataObject){
            JsonElement walletElement = dataObject != null ? dataObject.get(property) : null;
            JsonObject walletJson = walletElement != null && walletElement.isJsonObject() ? walletElement.getAsJsonObject() : null;
            return walletJson;
        }

        

        public static String getStringPropertyFromObject(String property, JsonObject json){
            JsonElement element = json.get(property);
            if(element != null && !element.isJsonNull()){
                return element.getAsString();
            }
            return null;
        }

        public static String getNameFromObject(JsonObject json){
            return getStringPropertyFromObject("name", json);
        }

        public static String getAddressFromObject(JsonObject json){
            return getStringPropertyFromObject("address", json);
        }

        public static String getWalletTypeFromObject(JsonObject json){
            return getStringPropertyFromObject("walletType", json);
        }

        public static JsonObject createWalletAddressObject(String address, String name, String walletType){
            JsonObject ergoObject = new JsonObject();
            ergoObject.addProperty("address", address);
            ergoObject.addProperty("name", name);
            ergoObject.addProperty("walletType", walletType);
            return ergoObject;
        }
        
        public static void addWalletChangeAddressToDataObject(String address, String walletName, String walletType, JsonObject dataObject){
            addWalletAddressToDataObject("changeAddress", address, walletName, walletType, dataObject);
        }

        public static void addWalletAddressToDataObject(String property, String address, String walletName, String walletType, JsonObject dataObject){
            JsonObject addressObject = createWalletAddressObject(address, walletName, walletType);
            dataObject.add(property, addressObject);
        }


        public static void addWalletAddressToAddressInput(String address, String walletName, String walletType, long nanoErgs, PriceAmount[] tokens, JsonArray addressInputs){
            JsonObject addressObject = createWalletAddressObject(address, walletName, walletType);
            addNanoErgAmountToDataObject(nanoErgs, addressObject);
            addTokensToDataObject(tokens, addressObject);
            addressInputs.add(addressObject);
        }

        public static void addSingleInputToDataObject(ErgoInputData inputData, JsonObject dataObject){

            JsonObject inputDataObject = inputData.getJsonObject();

            JsonArray inputsArray = new JsonArray();
            inputsArray.add(inputDataObject);

            addInputsToDataObject(inputsArray, dataObject);
        }

        public static void addInputsToDataObject(JsonArray inputsArray, JsonObject dataObject){
            dataObject.add("inputs", inputsArray);
        }



        public static boolean checkNetworkType(JsonObject dataObject, NetworkType networkType){
            JsonElement networkElement = dataObject != null ? dataObject.get("network") : null;
            JsonObject networkObject = networkElement != null && networkElement.isJsonObject() ? networkElement.getAsJsonObject() : null;
            JsonElement networkTypeElement = networkObject != null ? networkObject.get("networkType") : null;
            String networkTypeString = networkTypeElement != null ? networkTypeElement.getAsString() : null;
            return networkTypeString != null &&  networkType.toString().toLowerCase().equals(networkTypeString.toLowerCase());
        }

        public static JsonObject getNetworkTypeObject(NetworkType networkType){
            JsonObject ergoObject = new JsonObject();
            ergoObject.addProperty("networkType", networkType.toString());
            return ergoObject;
        }

        public static NetworkType getNetworkTypeFromJson(JsonObject json){
            JsonElement networkTypeElement = json.get("networkType");
            return networkTypeElement != null && networkTypeElement.isJsonNull() &&  networkTypeElement.getAsString().toUpperCase().equals(NetworkType.TESTNET.toString().toUpperCase())  ? NetworkType.TESTNET  : NetworkType.MAINNET;
        }

        public static void addNetworkTypeToDataObject(NetworkType networkType, JsonObject dataObject){
            JsonObject networkTypeObject = getNetworkTypeObject(networkType);
            dataObject.add("network", networkTypeObject);
        }


        public static long getFeeAmountFromDataObject(JsonObject dataObject){
            JsonElement feeElement = dataObject != null ? dataObject.get("feeAmount") : null;
            JsonObject feeObject = feeElement != null && feeElement.isJsonObject() ? feeElement.getAsJsonObject() : null;
            JsonElement nanoErgsElement = feeObject != null ? feeObject.get("nanoErgs") : null;
            return nanoErgsElement != null ? nanoErgsElement.getAsLong() : -1;
        }

        public static long getErgAmountFromDataObject(JsonObject dataObject){
            JsonElement ergAmountElement = dataObject != null ? dataObject.get("ergAmount") : null;
            JsonObject nanoErgObject = ergAmountElement != null && ergAmountElement.isJsonObject() ? ergAmountElement.getAsJsonObject() : null;
            JsonElement nanoErgsElement = nanoErgObject != null ? nanoErgObject.get("nanoErgs") : null;
            return nanoErgsElement != null ? nanoErgsElement.getAsLong() : -1;
        }

        public static ErgoToken[] getTokensFromDataObject(JsonObject dataObject) throws NullPointerException{
            JsonElement assetsElement = dataObject != null ? dataObject.get("tokens") : null;
            JsonArray assetsArray = assetsElement != null && assetsElement.isJsonArray() ? assetsElement.getAsJsonArray() : null;

            if(assetsArray != null){
                ErgoToken[] tokenArray = new ErgoToken[assetsArray.size()];
                
                for(int i = 0; i < assetsArray.size() ; i++ ){
                    JsonElement element = assetsArray.get(i);
                    if(element != null && !element.isJsonNull() && element.isJsonObject()){
                        JsonObject assetJson = element.getAsJsonObject();
                        ErgoToken ergoToken = PriceAmount.getErgoToken(assetJson);
                        if(ergoToken == null){
                            throw new NullPointerException("Provided asset is missing token information. \n(index: "+i+")");
                        }
                        tokenArray[i] = ergoToken;
                    }else{
                        throw new NullPointerException("Provided asset is not a valid json object. (Index: "+i+")");
                    }
                
                }

                return tokenArray;
            }
            return new ErgoToken[0];
        }

        public static PriceAmount[] getAmountsFromDataObject(JsonObject dataObject) throws NullPointerException{
            JsonElement assetsElement = dataObject != null ? dataObject.get("assets") : null;
            JsonArray assetsArray = assetsElement != null && assetsElement.isJsonArray() ? assetsElement.getAsJsonArray() : null;

            if(assetsArray != null){
                PriceAmount[] priceAmountsArray = new PriceAmount[assetsArray.size()];
                
                for(int i = 0; i < assetsArray.size() ; i++ ){
                    JsonElement element = assetsArray.get(i);
                    if(element != null && !element.isJsonNull() && element.isJsonObject()){
                        JsonObject assetJson = element.getAsJsonObject();
                        PriceAmount priceAmount = PriceAmount.getAmountFromObject(assetJson);
                        if(priceAmount == null){
                            throw new NullPointerException("Provided asset is missing token information. \n(index: "+i+")");
                        }
                        priceAmountsArray[i] = priceAmount;
                    }else{
                        throw new NullPointerException("Provided asset is not a valid json object. (Index: "+i+")");
                    }
                
                }

                return priceAmountsArray;
            }
            return new PriceAmount[0];
        }


        public static void testAddressInformation(AddressInformation addressInformation, int addressIntType, String name, int index)throws Exception{
            int adrIntType = addressInformation.getAddressIntType();

            if(adrIntType != addressIntType){
                throw new Exception("Address is incorrect type: " + AddressInformation.getAddressTypeString(adrIntType) + ", expected: " + AddressInformation.getAddressTypeString(addressIntType) + "\n(property: " + name + " index: " + index + ")");
            } 
        }


        
        public static void checkElementNull(JsonElement element ) throws NullPointerException{
            if(element == null){
                throw new NullPointerException("Element is null");
            }
            if(element.isJsonNull()){
                throw new NullPointerException("JsonNull not supported");
            }
        }

        public static BigInteger testValueAsBigInteger(int i, String name, JsonElement valueElement) throws Exception{
            try{
                checkElementNull(valueElement);
            }catch(NullPointerException e){
                throw new Exception("Contract constants error: Value " + e.toString() + "\n(property: " + name + " index: " + i+ ")" );
            }
            try{  
                BigInteger bigIntegerValue = valueElement.getAsBigInteger();
            
                return bigIntegerValue;
            }catch(Exception e){
                throw new Exception("Contract constants error: array contains invalid BigInt. \n(property: " + name + " index: " + i+ ")" );
            }
        }

        public static short testValueAsShort(int i, String name, JsonElement valueElement) throws Exception{
            try{
                checkElementNull(valueElement);
            }catch(NullPointerException e){
                throw new Exception("Contract constants error: Value " + e.toString() + " \n(property: " + name + " index: " + i+ ")" );
            }
            try{
                short sh = valueElement.getAsShort();
            
                return sh;
            }catch(Exception e){
                throw new Exception("Contract constants error: array contains invalid short. \n(property: " + name + " index: " + i+ ")" );
            }
        }

        public static byte testValueAsByte(int i, String name, JsonElement valueElement) throws Exception{
            try{
                checkElementNull(valueElement);
            }catch(NullPointerException e){
                throw new Exception("Contract constants error: Value " + e.toString() + " \n(property: " + name + " index: " + i+ ")" );
            }
            try{
                byte b = valueElement.getAsByte();
                
                return b;
            }catch(Exception e){
                throw new Exception("Contract constants error: array contains invalid hex value. \n(property: " + name + " index: " + i+ ")" );
            }
        }
        
        public static int testValueAsInteger(int i, String name, JsonElement valueElement) throws Exception{
            try{
                checkElementNull(valueElement);
            }catch(NullPointerException e){
                throw new Exception("Contract constants error: Value " + e.toString() + " \n(property: " + name + " index: " + i+ ")" );
            }
            try{
                int integerValue = valueElement.getAsInt();
                int parseValue = Integer.parseInt(valueElement.getAsString());
                if(integerValue != parseValue){
                    throw new Exception("Contract constants error: Value returns inconsitent results \n(property: " + name + " index: " + i+ ")" );
                }
            
                return integerValue;
            }catch(Exception e){
                throw new Exception("Contract constants error: array contains invalid integer. \n(property: " + name + " index: " + i+ ")" );
            }
        }

        public static boolean testValueAsBoolean(int i, String name, JsonElement valueElement) throws Exception{
            try{
                checkElementNull(valueElement);
            }catch(NullPointerException e){
                throw new Exception("Contract constants error: Value " + e.toString() + " \n(property: " + name + " index: " + i+ ")" );
            }
            try{
                boolean boolValue = valueElement.getAsBoolean();
            
                return boolValue;
            }catch(Exception e){
                throw new Exception("Contract constants error: array contains invalid boolean. \n(property: " + name + " index: " + i+ ")" );
            }
        }


        public static boolean testValueAsHex(int i, String name, JsonElement valueElement) throws Exception{
            try{
                checkElementNull(valueElement);
            }catch(NullPointerException e){
                throw new Exception("Contract constants error: Value " + e.toString() + "\n(property: " + name + " index: " + i+ ")" );
            }
            
            try{
                String str = valueElement.getAsString();
                String b16Test = str.replaceAll("[^A-Fa-f0-9]", "");
                if(!b16Test.equals(str)){
                    throw new Exception("Contract constants error: value contains invalid hex characters\n(property: " + name + " index: " + i+ ")" );
                }
                
                return true;
            }catch(Exception e){
                throw new Exception("Contract constants error: array contains invalid hex value. \n(property: " + name + " index: " + i+ ")" );
            }
        }

        public static long testValueAsLong(int i, String name, JsonElement valueElement) throws Exception{
            try{
                checkElementNull(valueElement);
            }catch(NullPointerException e){
                throw new Exception("Contract constants error: Value " + e.toString() + "\n(property: " + name + " index: " + i+ ")" );
            }
            try{
                long longValue = valueElement.getAsLong();
            
                return longValue;
            }catch(Exception e){
                throw new Exception("Contract constants error: array contains invalid long.\n(property: " + name + " index: "+ i+ ")" );
            }
        }

        public static Address testValueAsAddress(int i, JsonElement valueElement, String name, int networkIntType) throws Exception{
            try{
                checkElementNull(valueElement);
            }catch(NullPointerException e){
                throw new Exception("Contract constants error: Value " + e.toString() + "\n(property: " + name + " index: " + i+ ")" );
            }
            try{
                String str = valueElement.getAsString();
                byte[] bytes = AddressInformation.convertAddressToBytes(str);
                
                if(bytes == null){
                    throw new Exception("Contract constants error: Address could not be converted to bytes. \n(property: " + name + " index: " + i+ ")" );
                }
                int[] ints = AddressInformation.getAddressIntType(bytes[0]);
                if(ints == null){
                    throw new Exception("Contract constants error: Address is invalid type.\n(property: " + name + " index: " + i+ ")" );
                }
                if(ints[1] != networkIntType){
                    throw new Exception("Contract constants error: Address is not " +AddressInformation.getAddressTypeNetworkType(networkIntType) + ". \n(property: " + name + " index: " + i+ ")" );
                } 
                
                Address address = Address.create(str);
            
                return address;
            }catch(Exception e){
                throw new Exception("Contract constants error: array contains invalid address value. \n(property: " + name + " index: " + i+ ")" );
            }
        }

        public static ErgoId testValueAsTokenId(int i, String name, JsonElement valueElement) throws Exception{
            try{
                checkElementNull(valueElement);
            }catch(NullPointerException e){
                throw new Exception("Contract constants error: Value " + e.toString() + "\n(property: " + name + " index: " + i+ ")" );
            }
            try{
                String str = valueElement.getAsString();
        
                String b58Test = str.replaceAll("[^A-HJ-NP-Za-km-z0-9]", "");
                if(!b58Test.equals(str)){
                    throw new Exception("Contract constants error: Value contains invalid base58 characters\n(property: " + name + " index: " + i+ ")" );
                }
                
                return getErgoIdFromString(str);
            }catch(Exception e){
                throw new Exception("Contract constants error: array contains invalid address value. \n(property: " + name + " index: " + i+ ")" );
            }
        }




        public static ErgoId getErgoIdFromString(String tokenId){
            ErgoToken token = new ErgoToken(tokenId, 0L);
            return token.getId();
        }

        public static byte[] getTokenIdBytes(String tokenId){
            ErgoId ergoId = getErgoIdFromString(tokenId);
            return ergoId.getBytes();
        }



        public static void logBlankDlogProvInput(int i, String name) throws Exception{
        
        }








        public static JsonObject getWalletPKConstant(String propertyName, String address){
        String b58Test = address.replaceAll("[^A-HJ-NP-Za-km-z0-9]", "");
        if(!b58Test.equals(address)){
            return null;
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", address);
        json.addProperty("type", WALLET_ADDRESS_PK);
        return json;
    }



    public static JsonObject getWalletP2PKPropBytesContractProperty(String propertyName, String address){
        String b58Test = address.replaceAll("[^A-HJ-NP-Za-km-z0-9]", "");
        if(!b58Test.equals(address)){
            return null;
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", address);
        json.addProperty("type", WALLET_ADDRESS_PROP_BYTES);
        return json;
    }


    public static JsonObject getPKContractProperty(String propertyName, String address, int index){
        JsonObject json = getWalletPKConstant(propertyName, address);
        json.addProperty("index", index);
        return json;
    }

    public static JsonObject getDummyPKConstant(String propertyName){
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", "");
        json.addProperty("type", ParamTypes.DUMMY_PK);
        return json;
    }



    public static JsonObject getDummyPKProperty(String propertyName, int index){
        JsonObject json = getDummyPKConstant(propertyName);
        json.addProperty("index", index);
        return json;
    }


    public static JsonObject getBlankPKConstant(String propertyName){
        JsonObject json = new JsonObject();
        json.addProperty("name", propertyName);
        json.addProperty("value", "");
        json.addProperty("type", ParamTypes.BLANK_PK);
        return json;
    }



    public static JsonObject getBlankPKProperty(String propertyName, int index){
        JsonObject json = getBlankPKConstant(propertyName);
        json.addProperty("index", index);
        return json;
    }

    public static JsonObject getWalletP2PKPropBytesContractProperty(String propertyName, String address, int index){
        JsonObject json = getWalletP2PKPropBytesContractProperty(propertyName, address);
        json.addProperty("index", index);
        return json;
    }

    public static JsonObject getJsonObject(String name, String property){
        JsonObject json = new JsonObject();
        json.addProperty(name, property);
        return json;
    }


    public static JsonObject getJsonObject(String name, int property){
        JsonObject json = new JsonObject();
        json.addProperty(name, property);
        return json;
    }

    public static JsonObject getCmdObject(String subject) {
        JsonObject cmdObject = new JsonObject();
        cmdObject.addProperty(NoteConstants.CMD, subject);
        cmdObject.addProperty("timeStamp", System.currentTimeMillis());
        return cmdObject;
    }

    public static JsonObject getMsgObject(int code, long timeStamp, String networkId){
        JsonObject json = new JsonObject();
        json.addProperty("timeStamp", timeStamp);
        json.addProperty("networkId", networkId);
        json.addProperty("code", code);
        return json;
    }

    public static JsonObject getMsgObject(int code, String msg){
        JsonObject json = new JsonObject();
        json.addProperty("code", code);
        json.addProperty("msg", msg);
        return json;
    }

    
    public static ArrayList<PriceAmount>  getBalanceList(JsonObject json, boolean confirmed, NetworkType networkType){

        ArrayList<PriceAmount> ballanceList = new ArrayList<>();

        JsonElement timeStampElement = json != null ? json.get("timeStamp") : null;
        JsonElement objElement = json != null ? json.get( confirmed ? "confirmed" : "unconfirmed") : null;

        long timeStamp = timeStampElement != null ? timeStampElement.getAsLong() : -1;
        if (objElement != null && timeStamp != -1) {

            JsonObject objObject = objElement.getAsJsonObject();
            JsonElement nanoErgElement = objObject.get("nanoErgs");
            JsonElement ergoQuoteElement = objObject.get("ergoQuote");
    
            long nanoErg = nanoErgElement != null && nanoErgElement.isJsonPrimitive() ? nanoErgElement.getAsLong() : 0;
            PriceQuote ergoQuote = ergoQuoteElement != null && !ergoQuoteElement.isJsonNull() && ergoQuoteElement.isJsonObject() ? new PriceQuote(ergoQuoteElement.getAsJsonObject()) : null;
           
            ErgoAmount ergoAmount = new ErgoAmount(nanoErg, networkType);
            ergoAmount.setPriceQuote(ergoQuote);
        
            ballanceList.add(ergoAmount);
            
            JsonElement confirmedArrayElement = objObject.get("tokens");

            if(confirmedArrayElement != null && confirmedArrayElement.isJsonArray()){
                JsonArray confirmedTokenArray = confirmedArrayElement.getAsJsonArray();

                for (JsonElement tokenElement : confirmedTokenArray) {
                    JsonObject tokenObject = tokenElement.getAsJsonObject();
                    JsonElement tokenQuoteElement = tokenObject.get("tokenQuote");
                    ErgoBoxAsset asset = new ErgoBoxAsset(tokenObject);

                    PriceQuote tokenQuote = tokenQuoteElement != null && !tokenQuoteElement.isJsonNull() && tokenQuoteElement.isJsonObject() ? new PriceQuote(tokenQuoteElement.getAsJsonObject()) : null;

                    PriceAmount tokenAmount = new PriceAmount(asset, networkType.toString(), true);    
                    tokenAmount.setPriceQuote(tokenQuote);
                    ballanceList.add(tokenAmount);
                    
                }
            }
     
             
        } 

        return ballanceList;
    
    }

    

    public static PriceAmount getPriceAmountFromList(ArrayList<PriceAmount> priceList, String tokenId){
        if(tokenId != null && priceList != null){
            int size = priceList.size();
            for(int i = 0; i < size ; i++){
                PriceAmount amount = priceList.get(i);
                if(amount != null && amount.getTokenId().equals(tokenId)){
                    return amount;
                }
            }
        }
        
        return null;
    }

    public static Future<?> getAppIconFromNetworkObject(JsonObject json, ExecutorService execService, EventHandler<WorkerStateEvent> onImage, EventHandler<WorkerStateEvent> onFailed){
        if(json != null){
            JsonElement nameElement = json.get("appIcon");
            
            String imgStr = nameElement != null && nameElement.isJsonPrimitive() ? nameElement.getAsString() : null;
            
            if(imgStr != null){
                return Drawing.convertHexStringToImg(imgStr, execService, onImage, onFailed);
            }
        }
        return null;
    }

    public static String getJsonName(JsonObject obj){
        if(obj != null){
            JsonElement nameElement = obj.get("name");
            if(nameElement != null && !nameElement.isJsonNull() && nameElement.isJsonPrimitive()){
                return nameElement.getAsString();
            }
        }
        return null;
    }

    public static String getJsonId(JsonObject obj){
        if(obj != null){
            JsonElement idElement = obj.get("id");
            if(idElement != null && !idElement.isJsonNull() && idElement.isJsonPrimitive()){
                return idElement.getAsString();
            }
        }
        return null;
    }


    public static JsonObject getCmdObject(String cmd, String locationId){        
        JsonObject note = NoteConstants.getCmdObject(cmd);
        note.addProperty("locationId", locationId);
        return note;
    }

    public static JsonObject getCmdObject(String cmd, String networkId, String locationId){        
        JsonObject note = NoteConstants.getCmdObject(cmd);
        note.addProperty("locationId", locationId);
        note.addProperty("networkId", networkId);
        return note;
    }

    public static Future<?> getNetworkObject(NoteInterface networkInteface, String locationId, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
    
        if(networkInteface != null){
            JsonObject note = NoteConstants.getCmdObject("getNetworkObject", locationId);
            return networkInteface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException("Network disabled", execService, onFailed);
        }
    }

    public static String getNameFromNetworkObject(JsonObject json){
      
        JsonElement nameElement = json != null ? json.get("name") : null;
        
        return nameElement != null && !nameElement.isJsonNull() && nameElement.isJsonPrimitive() ? nameElement.getAsString() : "(Unknown)";
    
    }


    public static Future<?> getInterfaceNetworkObjects(Iterator<NoteInterface> it, JsonArray jsonArray, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded,  EventHandler<WorkerStateEvent> onFailed ){
        if(it.hasNext()){
            NoteInterface noteInterface = it.next();
            return noteInterface.sendNote(NoteConstants.getCmdObject("getNetworkObject"),(onNetworkObject)->{
                Object obj = onNetworkObject.getSource().getValue();
                if(obj != null && obj instanceof JsonObject){
                    jsonArray.add((JsonObject) obj);
                    getInterfaceNetworkObjects(it, jsonArray, execService, onSucceeded, onFailed);
                }
            } , onFailed);
        }else{
            return Utils.returnObject(jsonArray, execService, onSucceeded);
        }
    }
}
