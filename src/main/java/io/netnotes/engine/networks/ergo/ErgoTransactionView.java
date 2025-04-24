package io.netnotes.engine.networks.ergo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

import io.netnotes.engine.PriceAmount;
import io.netnotes.engine.networks.ergo.ErgoTransactionPartner.PartnerType;
import io.netnotes.ergo.TreeHelper;

import org.ergoplatform.appkit.NetworkType;

import com.google.gson.JsonArray;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;

public class ErgoTransactionView {

    public static class TransactionFlag {
        public final static String EXPLORER_UPDATED = "Updated";
        public final static String REQURES_UPDATE = "Minimal Info.";
        public final static String UNKNOWN = "Unknown";
    }

    public static class TransactionStatus {
        public final static String CREATED = "Created";
        public final static String PENDING = "Pending";
        public final static String CONFIRMED = "Confirmed";
        public final static String INVALID = "Invalid";
    }

    public final static String FEE_ERGOTREE_START = "1005040004000e36100204a00b08cd0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798ea02d";
    public final static long REQUIRED_CONFIRMATIONS = 100;

    private long m_requiredConfirmations = REQUIRED_CONFIRMATIONS;

    private String m_partnerType = PartnerType.UNKNOWN;
    private String m_flag = TransactionFlag.UNKNOWN;
    private String m_status = TransactionStatus.PENDING;

    private String m_id;
    private final AddressInformation m_parentAddressInformation;
    private long m_timeStamp = -1;
    private String m_blockId = "";
    private int m_inclusionHeight = -1;
    private int m_index = -1;
    private long m_globalIndex = -1;
    private int m_size = -1;
    private long m_numConfirmations = -1;
    private SimpleObjectProperty<LocalDateTime> m_lastUpdated = new SimpleObjectProperty<>(null);
    private ChangeListener<LocalDateTime> m_changeListener = null;

    private ErgoTransactionPartner[] m_txPartners = new ErgoTransactionPartner[0];
    private ErgoDataInputBox[] m_dataInputs = new ErgoDataInputBox[0];

    private String m_txLink = null;
    private String m_apiLink = null;
    private ErgoTxInfo m_txInfo = null;

    private final NetworkType m_networkType;


    public ErgoTransactionView(String txId, String parentAddress) {
        m_id = txId;
        m_parentAddressInformation = new AddressInformation(parentAddress);
        m_networkType = m_parentAddressInformation.getNetworkType();
    }

    public ErgoTransactionView(String txId, String parentAddress,  ErgoTransactionPartner[] txPartners) {
        m_id = txId;
        m_parentAddressInformation = new AddressInformation(parentAddress);
        m_networkType = m_parentAddressInformation.getNetworkType();
        m_txPartners = txPartners;
    }

    public ErgoTransactionView(String parentAddress, JsonObject json, boolean isExplorer) throws NullPointerException, UnsupportedOperationException  {
        m_parentAddressInformation = new AddressInformation(parentAddress);
        m_networkType = m_parentAddressInformation.getNetworkType();
        updateJson(json, false);
    }

    public ErgoTransactionView(String parentAddress, JsonReader reader, boolean isExplorer) throws IOException {
        m_parentAddressInformation = new AddressInformation(parentAddress);
        m_networkType = m_parentAddressInformation.getNetworkType();
        update(reader, isExplorer, true);
    }

    public void updateJson(JsonObject json, boolean updated) throws NullPointerException, UnsupportedOperationException {
        JsonElement idElement = json.get("id");
        if(idElement == null || (idElement != null && idElement.isJsonNull())){
            throw new NullPointerException("Transaction json id is null");
        }
        String id = idElement.getAsString();
        if(m_id != null && !m_id.equals(id)){
            throw new UnsupportedOperationException("Json id does not match transaction");
        }else if(m_id == null){
            m_id = id;
        }
    
        JsonElement flagElement = json.get("flag");
        JsonElement timeStampElement = json.get("timeStamp");
        JsonElement statusElement = json.get("status");
        JsonElement partnerTypeElement = json.get("type");
        JsonElement blockIdElement = json.get("blockId");
        JsonElement numConfirmationsElement = json.get("numConfirmations");
        JsonElement inclusionHeightElement = json.get("inclusionHeight");
        JsonElement indexElement = json.get("index");
        JsonElement globalIndexElement = json.get("globalIndex");
        JsonElement sizeElement = json.get("size");
        JsonElement urlElement = json.get("url");
        JsonElement apiUrlElement = json.get("apiUrl");
        JsonElement txInfoElement = json.get("txInfo");


        setPartnerType(partnerTypeElement.getAsString());
        setBlockId(blockIdElement.getAsString());
        setNumConfirmations( numConfirmationsElement.getAsLong());
        setInclusionHeight(inclusionHeightElement != null ? inclusionHeightElement.getAsInt() : m_inclusionHeight);
        setIndex(indexElement != null ? indexElement.getAsInt() : m_index);
        setGlobalIndex(globalIndexElement != null ? globalIndexElement.getAsLong() : m_globalIndex);
        setSize(sizeElement != null ? sizeElement.getAsInt() : m_size);
        setTxFlag(flagElement.getAsString());
        setTimeStamp(timeStampElement != null ? timeStampElement.getAsLong() : m_timeStamp);
        setStatus(statusElement != null && !statusElement.isJsonNull() ? statusElement.getAsString() : m_status);
        if(urlElement != null && !urlElement.isJsonNull()){
            setTxLink(urlElement.getAsString());
        }
        if(apiUrlElement != null && !apiUrlElement.isJsonNull()){
            setApiLink(apiUrlElement.getAsString());
        }
        if(txInfoElement != null && txInfoElement.isJsonObject()){
            m_txInfo = new ErgoTxInfo(txInfoElement.getAsJsonObject());
        }

        JsonElement txPartnersElement = json.get("addresses");
        JsonArray txPartners = txPartnersElement.getAsJsonArray();
        ErgoTransactionPartner[] partners = new ErgoTransactionPartner[txPartners.size()];
        for (int i = 0; i < txPartners.size(); i++) {
            partners[i] = new ErgoTransactionPartner(txPartners.get(i).getAsJsonObject());
        }
        setTxPartnerArray(partners);

        JsonElement dataInputsElement = json.get("dataInputs");
        JsonArray dataInputsArray = dataInputsElement != null ? dataInputsElement.getAsJsonArray() : null;
        if(dataInputsArray != null){
            ErgoDataInputBox[] dataInputs = new ErgoDataInputBox[dataInputsArray.size()];
            for (int i = 0; i < dataInputsArray.size(); i++) {
                dataInputs[i] = new ErgoDataInputBox(dataInputsArray.get(i).getAsJsonObject());
            }
            setDataInputs(dataInputs);
        }
        
        if(updated){
            setTxUpdated();
        }
    }

    

    public void setTxLink(String url){
        m_txLink = url;
    }

    public String getTxLink(){
        return m_txLink;
    }

    public void setApiLink(String url){
        m_apiLink = url;
    }

    public String getApiLink(){
        return m_apiLink;
    }

    protected void setTxPartnerArray(ErgoTransactionPartner[] partners) {
        m_txPartners = partners;
    }

    public ErgoTransactionPartner[] getTxPartners() {
        return m_txPartners;
    }

    public ErgoTransactionPartner[] getRecipientPartners(){
        ArrayList<ErgoTransactionPartner> recipientsList = new ArrayList<>();
        for(int i = 0; i < m_txPartners.length ; i++){
            ErgoTransactionPartner partner = m_txPartners[i];
            if(partner.getPartnerType().equals(PartnerType.RECEIVER)){
                recipientsList.add(partner);
            }
        }
        return recipientsList.toArray(new ErgoTransactionPartner[recipientsList.size()]);
    }

    public ErgoBox[] getSentOutputBoxes(){
        if(getPartnerType().equals(PartnerType.SENDER)){
            ErgoTransactionPartner[] txPartners = getRecipientPartners();
            ArrayList<ErgoBox> sentBoxList = new ArrayList<>();

            for(ErgoTransactionPartner recipient : txPartners){

                ErgoBox[] outputBoxes = recipient.getOutputBoxes();
                for(ErgoBox outBox : outputBoxes){
                    sentBoxList.add(outBox);
                }
            }
            return sentBoxList.toArray(new ErgoBox[sentBoxList.size()]);
        }
        return null;
    }

    public static boolean boxUnconfirmed(ErgoBoxInfo boxInfo){
        if(boxInfo == null){
            return true;
        }
        if(boxInfo.getStatus().equals(TransactionStatus.CONFIRMED)){
            return false;
        }
        return (System.currentTimeMillis() - boxInfo.getTimeStamp()) > (15 * 60 * 1000);
    }

    public String[] getReclaimableOutBoxIds(){
        ErgoBox[] outputBoxes = getSentOutputBoxes();
        ErgoTxInfo txInfo = m_txInfo;
        
        if(outputBoxes != null){
            ArrayList<String> reclaimableOutBoxIds = new ArrayList<>();
            for(ErgoBox outBox : outputBoxes){
               
                ErgoBoxInfo boxInfo = txInfo != null ? txInfo.getBoxInfo(outBox.getBoxId()) : null;
                if(boxUnconfirmed(boxInfo)){
                    String constants = outBox.getErgoTreeConstants();
                    if(constants.length() > 0){
                        if(constants.split("\n").length > 0){
                            String boxErgoTree = outBox.getErgoTree();
                            TreeHelper boxHelper = new TreeHelper(boxErgoTree);
        
                            if(boxHelper.constantsContainsPK(getParentAddress(), getNetworkType().networkPrefix)){
                                reclaimableOutBoxIds.add(outBox.getBoxId());
                            }
                        
                        }
                    }
                }
            }
            return reclaimableOutBoxIds.size() > 0 ? reclaimableOutBoxIds.toArray(new String[reclaimableOutBoxIds.size()]) : null;
        }
        return null;
    }

    public AddressInformation getParentAddressInformation(){
        return m_parentAddressInformation;
    }

    public NetworkType getNetworkType(){
        return m_networkType;
    }

    public void setTxInfo(ErgoTxInfo txInfo){
        m_txInfo = txInfo;
        m_lastUpdated.set(LocalDateTime.now());
    }

    public ErgoTxInfo getTxInfo(){
        return m_txInfo;
    }

    public String getRecipientAddressCommaSeperated(){
        ErgoTransactionPartner[] recipients = getRecipientPartners();
        int len = recipients.length;
        if(len > 1){
            String addresses = "";
            for(int i = 0; i < len; i++){
                addresses += ((i + 1) < len ? recipients[i].getAddressString() + ", " : recipients[i]);
            }
            addresses += getOther() != null ? ", ..." : "";
               
            return addresses;
        }
        return len > 0 ? recipients[0].getAddressString() : "";
    }

    public JsonArray getTxPartnersJsonArray() {
        JsonArray jsonArray = new JsonArray();
        for (int i = 0; i < m_txPartners.length; i++) {
            jsonArray.add(m_txPartners[i].getJsonObject());
        }
        return jsonArray;
    }

    public ErgoTransactionPartner getTxPartner(String addressString) {
        if (addressString != null) {
            for (int i = 0; i < m_txPartners.length; i++) {
                ErgoTransactionPartner partner = m_txPartners[i];
                if (partner.getAddressString().equals(addressString)) {
                    return partner;
                }
            }
        }
        return null;
    }

    

    public ErgoTransactionPartner getSender() {
        for (int i = 0; i < m_txPartners.length; i++) {
            ErgoTransactionPartner partner = m_txPartners[i];
            if (partner.getPartnerType().equals(PartnerType.SENDER)) {
                return partner;
            }
        }
        return null;
    }

    public static ErgoTransactionPartner getSender(ArrayList<ErgoTransactionPartner> txPartners) {
        for (int i = 0; i < txPartners.size(); i++) {
            ErgoTransactionPartner partner = txPartners.get(i);
            if (partner.getPartnerType().equals(PartnerType.SENDER)) {
                return partner;
            }
        }
        return null;
    }

    public static ErgoTransactionPartner getOther(ArrayList<ErgoTransactionPartner> txPartners) {
        for (int i = 0; i < txPartners.size(); i++) {
            ErgoTransactionPartner partner = txPartners.get(i);
            if (partner.getPartnerType().equals(PartnerType.OTHER)) {
                return partner;
            }
        }
        return null;
    }

    public ErgoTransactionPartner getOther() {
        for (int i = 0; i < m_txPartners.length; i++) {
            ErgoTransactionPartner partner = m_txPartners[i];
            if (partner.getPartnerType().equals(PartnerType.OTHER)) {
                return partner;
            }
        }
        return null;
    }

    public ErgoTransactionPartner getMiner() {
        for (int i = 0; i < m_txPartners.length; i++) {
            ErgoTransactionPartner partner = m_txPartners[i];
            if (partner.getPartnerType().equals(PartnerType.MINER)) {
                return partner;
            }
        }
        return null;
    }

    public ErgoTransactionPartner getFirstReceipient() {
        for (int i = 0; i < m_txPartners.length; i++) {
            ErgoTransactionPartner partner = m_txPartners[i];
            if (partner.getPartnerType().equals(PartnerType.RECEIVER)) {
                return partner;
            }
        }
        return null;
    }

    public String getFirstReceipientAddress() {
        ErgoTransactionPartner partner = getFirstReceipient();
        return partner != null ? partner.getAddressString() : null;
    }

    public ErgoDataInputBox[] getDataInputs(){
        return m_dataInputs;
    }

    protected void setDataInputs(ErgoDataInputBox[] dataInputs){
        m_dataInputs = dataInputs;
    }

    public JsonArray getDataInputsJsonArray() {
        JsonArray jsonArray = new JsonArray();
        for (int i = 0; i < m_dataInputs.length; i++) {
            jsonArray.add(m_dataInputs[i].getJsonObject());
        }
        return jsonArray;
    }

    public int getSize() {
        return m_size;
    }

    public String getBlockId() {
        return m_blockId;
    }

    public int getInclusionHeight() {
        return m_inclusionHeight;
    }

    public int getIndex() {
        return m_index;
    }

    public long getGlobalIndex() {
        return m_globalIndex;
    }

    public String getTxFlag() {
        return m_flag;
    }

    protected void setSize(int size) {
        m_size = size;
    }

    protected void setBlockId(String blockId) {
        m_blockId = blockId;
    }

    protected void setInclusionHeight(int height) {
        m_inclusionHeight = height;
    }

    protected void setIndex(int index) {
        m_index = index;
    }

    protected void setGlobalIndex(long globalIndex) {
        m_globalIndex = globalIndex;
    }

    public void setTxFlag(String flag) {
        if (flag.equals(TransactionFlag.UNKNOWN)) {
            setStatus(TransactionStatus.INVALID);
        }
        m_flag = flag;
    }

    public String getId() {
        return m_id;
    }

    public long getTimeStamp() {
        return m_timeStamp;
    }

    protected void setTimeStamp(long timeStamp) {
        m_timeStamp = timeStamp;
    }

    protected void setStatus(String status) {
        m_status = status;
    }

    public String getStatus() {
        return m_status;
    }

    public long getNumConfirmations() {
        return m_numConfirmations;
    }

    public boolean isRequriedConfirmations(){
        return m_numConfirmations >= m_requiredConfirmations;
    }

    public void setRequiredConfirmations(long numConfirmations){
        m_requiredConfirmations = numConfirmations;
    }

    public long getRequiredConfirmations(){
        return m_requiredConfirmations;
    }

    protected void setNumConfirmations(long numConfirmations) {
        m_numConfirmations = numConfirmations;
        if (m_numConfirmations >= 1) {
            setStatus(TransactionStatus.CONFIRMED);
        }
    }

    public long getFeeNanoErgs() {
        ErgoTransactionPartner partner = getMiner();
        return partner != null ? partner.getNanoErgs() : 0;
    }

    public long getParentNanoErgs() {
        ErgoTransactionPartner partner = getTxPartner(getParentAddress());
        return partner != null ? partner.getNanoErgs() : 0;
    }

    public ErgoAmount getParentErgoAmount(){
        return new ErgoAmount(getParentNanoErgs());
    }

    public PriceAmount[] getParentTokens() {
        ErgoTransactionPartner partner = isDebit() ? getSender() : getTxPartner(getParentAddress());
        return partner != null ? partner.getTokens() : new PriceAmount[0];
    }

    public static PriceAmount getToken(String tokenId, ArrayList<PriceAmount> tokensList) {
        if (tokenId != null) {
            for (int i = 0; i < tokensList.size(); i++) {
                PriceAmount token = tokensList.get(i);
                if (token.getTokenId().equals(tokenId)) {
                    return token;
                }
            }
        }
        return null;
    }

    public static int getTokenIndex(String tokenId, ArrayList<PriceAmount> tokensList) {
        if (tokenId != null) {
            for (int i = 0; i < tokensList.size(); i++) {
                PriceAmount token = tokensList.get(i);
                if (token.getTokenId().equals(tokenId)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static JsonArray getTokenJsonArray(PriceAmount[] tokens) {
        JsonArray jsonArray = new JsonArray();
        if (tokens != null) {
            for (int i = 0; i < tokens.length; i++) {
                jsonArray.add(tokens[i].getJsonObject());
            }
        }
        return jsonArray;
    }

    public String getParentAddress() {
        return m_parentAddressInformation.getAddressString();
    }

    public String getPartnerType() {
        return m_partnerType;
    }

    protected void setPartnerType(String partnerType) {
        m_partnerType = partnerType;
    }

    public ReadOnlyObjectProperty<LocalDateTime> getLastUpdated() {
        return m_lastUpdated;
    }

    protected void updated() {
        m_lastUpdated.set(LocalDateTime.now());
    }

    private boolean isDebit() {
        return m_partnerType != null && m_partnerType.equals(PartnerType.SENDER);
    }

    private void readDataInputs(JsonReader reader) throws IOException{
        reader.beginArray();
        ArrayList<ErgoDataInputBox> dataInputList = new ArrayList<>();
        while(reader.hasNext()){
            dataInputList.add(new ErgoDataInputBox(reader));
        }
        reader.endArray();
    }

    private void readInputs(JsonReader reader, ArrayList<ErgoTransactionPartner> txPartners) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {

            ErgoBox inputBox = new ErgoBox(reader);

            ErgoTransactionPartner partner = findTxPartner(inputBox.getAddress(), txPartners);

            if (partner == null) {
                ErgoTransactionPartner newTxPartner = new ErgoTransactionPartner(PartnerType.SENDER, inputBox);
                txPartners.add(newTxPartner);
            } else {
                partner.addInputBox(inputBox);
            }
        }
        reader.endArray();
        m_partnerType = findTxPartner(getParentAddress(), txPartners) != null ? PartnerType.SENDER : PartnerType.RECEIVER;

    }

    private void readOutputs(JsonReader reader, ArrayList<ErgoTransactionPartner> txPartners) throws IOException {
        reader.beginArray();
        ErgoTransactionPartner senderPartner = getSender(txPartners);
        String senderAddress = senderPartner.getAddressString();

        int i = 0;
        while (reader.hasNext()) {
            ErgoBox outputBox = new ErgoBox(reader);

            boolean isMiner = outputBox.getErgoTree().startsWith(FEE_ERGOTREE_START);
            boolean isParent = !isMiner ? outputBox.getAddress().equals(getParentAddress()) : false;
            boolean isSender = outputBox.getAddress().equals(senderAddress);

            if(!isSender){
                senderPartner.addNanoErgs(outputBox.getValue());
                senderPartner.addTokens(outputBox.getAssetsAsPriceAmounts());
            }
            
            ErgoTransactionPartner partner = isSender ? senderPartner
                    : findTxPartner(outputBox.getAddress(), txPartners);
        
            if (isMiner || isParent || isSender || partner != null || i < 20) {
               
                if (partner == null) {
                    ErgoTransactionPartner newTxPartner = new ErgoTransactionPartner(
                            isMiner ? PartnerType.MINER : PartnerType.RECEIVER, outputBox);
                    txPartners.add(newTxPartner);
                } else {
                    if (partner.getPartnerType().equals(PartnerType.SENDER)) {
                        partner.addOutputBoxToRemaining(outputBox);
                    } else {
                        partner.addOutputBoxToValue(outputBox);
                    }

                }
            } else {

                ErgoTransactionPartner otherPartners = getOther(txPartners);
                if (otherPartners == null) {
                    ErgoTransactionPartner newTxPartner = new ErgoTransactionPartner(PartnerType.OTHER, outputBox);
                    txPartners.add(newTxPartner);
                } else {
                    otherPartners.addOutputBoxToValue(outputBox);
                }

            }

            i++;
        }
        reader.endArray();
        m_txPartners = txPartners.toArray(new ErgoTransactionPartner[txPartners.size()]);
    }

    public void update(JsonReader reader, boolean isExplorer) throws IOException {
        update(reader, isExplorer, false);
    }

    private void update(JsonReader reader, boolean isExplorer, boolean onCreate) throws IOException {
        ArrayList<ErgoTransactionPartner> txPartners = new ArrayList<>();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "id":
                    if (onCreate) {
                        m_id = reader.nextString();
                    } else {
                        if (!m_id.equals(reader.nextString())) {
                            throw new IOException("Transaction id does not match");
                        }
                    }
                    break;
                case "blockId":
                    m_blockId = reader.nextString();
                    break;
                case "inclusionHeight":
                    m_inclusionHeight = reader.nextInt();
                    break;
                case "index":
                    m_index = reader.nextInt();
                    break;
                case "globalIndex":
                    m_globalIndex = reader.nextLong();
                    break;
                case "numConfirmations":
                    setNumConfirmations(reader.nextInt());
                    break;
                case "timestamp":
                    m_timeStamp = reader.nextLong();
                    break;
                case "dataInputs":
                    readDataInputs(reader);
                    break;    
                case "inputs":
                    readInputs(reader, txPartners);
                    break;
                case "outputs":
                    readOutputs(reader, txPartners);
                    break;
                case "size":
                    m_size = reader.nextInt();
                    break;
                default:
                    reader.skipValue();
            }
        }
        m_txPartners = txPartners.toArray(new ErgoTransactionPartner[txPartners.size()]);
        reader.endObject();
        if(m_id == null){
            throw new IOException("Transaction id is not available");
        }
        if(isExplorer){
            m_flag = TransactionFlag.EXPLORER_UPDATED;
        }
        if(isExplorer && !onCreate){
            setTxUpdated();
        }
    }

    protected void setTxUpdated() {
        setTxFlag(TransactionFlag.EXPLORER_UPDATED);

        m_lastUpdated.set(LocalDateTime.now());
    }

    public static ErgoTransactionPartner findTxPartner(String addressString,
            ArrayList<ErgoTransactionPartner> txPartners) {
        if (addressString != null) {
            for (int i = 0; i < txPartners.size(); i++) {
                ErgoTransactionPartner partner = txPartners.get(i);
                if (partner.getAddressString().equals(addressString)) {
                    return partner;
                }
            }
        }
        return null;
    }

    public static PriceAmount[] getJsonTokenAmounts(JsonArray jsonArray) throws Exception {
        PriceAmount[] priceAmounts = new PriceAmount[jsonArray.size()];

        for (int i = 0; i < jsonArray.size(); i++) {
            JsonElement tokenElement = jsonArray.get(i);

            priceAmounts[i] = new PriceAmount(tokenElement.getAsJsonObject());

        }

        return priceAmounts;
    }

    public void addUpdateListener(ChangeListener<LocalDateTime> changeListener) {
        m_changeListener = changeListener;
        if (m_changeListener != null) {
            m_lastUpdated.addListener(m_changeListener);

        }
        // m_lastUpdated.addListener();
    }

    public void removeUpdateListener() {
        if (m_changeListener != null) {
            m_lastUpdated.removeListener(m_changeListener);
            m_changeListener = null;
        }
    }



    public JsonObject getJsonObject() {

        JsonObject json = new JsonObject();
        json.addProperty("id", getId());
        json.addProperty("flag", getTxFlag());
        json.addProperty("status", getStatus());
        json.addProperty("type", getPartnerType());
        json.addProperty("numConfirmations", getNumConfirmations());
        json.addProperty("blockId", getBlockId());
        json.addProperty("inclusionHeight", getInclusionHeight());
        json.addProperty("index", getIndex());
        json.addProperty("globalIndex", getGlobalIndex());
        if(m_txInfo != null){
            json.add("txInfo", m_txInfo.getJsonObject());
        }
        if(m_txLink != null){
            json.addProperty("url", m_txLink);
        }
        if(m_apiLink != null){
            json.addProperty("apiUrl", m_apiLink);
        }
        json.addProperty("size", getSize());
        json.add("addresses", getTxPartnersJsonArray());
        if(m_dataInputs != null && m_dataInputs.length > 0){
            json.add("dataInputs", getDataInputsJsonArray());
        }
        json.addProperty("timeStamp", getTimeStamp());
        return json;

    }
}
