package io.netnotes.engine;

import java.time.LocalDateTime;

import org.ergoplatform.appkit.NetworkType;

import javafx.beans.property.SimpleObjectProperty;

public class NamedNodeUrl {

    //networkType
    public final static String TESTNET_STRING = NetworkType.TESTNET.toString();
    public final static String MAINNET_STRING = NetworkType.MAINNET.toString();
    public final static String DEFAULT_NODE_IP = "213.239.193.208";
    public final static int DEFAULT_MAINNET_PORT = 9053;
    public final static NoteBytes PULIC_NODE_1 = new NoteBytes( "PUBLIC_NODE_1");
    //type
    private int m_port = DEFAULT_MAINNET_PORT;
    private NoteBytes m_id = null;
    private String m_name = null;
    private String m_protocol = "http";
    private String m_ip = DEFAULT_NODE_IP;
    private NetworkType m_networkType = NetworkType.MAINNET;
    private String m_apiKey = "";
   // private boolean m_rememberKey = true;

    private SimpleObjectProperty<LocalDateTime> m_lastUpdated = new SimpleObjectProperty<>(null);

    public NamedNodeUrl(){
        this(PULIC_NODE_1, "Public Node #1");
    }

    public NamedNodeUrl(NoteBytes id, String name) {
        m_id = id;
        m_name = name;
    }

    public NamedNodeUrl(NoteBytesObject json) throws Exception {
        if (json != null) {

            NoteBytesPair idElement = json.get("id");
            NoteBytesPair nameElement = json.get("name");
            NoteBytesPair ipElement = json.get("ip");
            NoteBytesPair portElement = json.get("port");
            NoteBytesPair networkTypeElement = json.get("networkType");
          //  JsonElement nodeTypeElement = json.get("nodeType");
            NoteBytesPair apiKeyElement = json.get("apiKey");

            if(!(idElement != null && networkTypeElement != null &&  nameElement != null)){
                throw new Exception("Null data");
            }
            m_id =  idElement.getValue();

            String networkTypeString = networkTypeElement.getValue().getAsString();
            m_networkType = networkTypeString.equals(TESTNET_STRING) ? NetworkType.TESTNET : NetworkType.MAINNET;
        

            m_name = nameElement.getValue().getAsString();
            m_ip = ipElement.getValue().getAsString();
            m_port = portElement.getValue().getAsInt();
            m_apiKey = apiKeyElement != null ? apiKeyElement.getValue().getAsString() : "";
                
            

        }else{
            throw new Exception("Null data");
        }
    }

    public NamedNodeUrl(NoteBytes id, String name, String ip, int port, String apiKey, NetworkType networkType) {
        m_id = id;
        m_name = name;
        m_ip = ip;
        m_port = port;
        m_networkType = networkType;
        m_apiKey = apiKey;
    }

    public NoteBytes getId() {
        return m_id;
    }

    public String getName() {
        return m_name;
    }

    public void setName(String value) {
        m_name = value;
    }

    public String getIP() {
        return m_ip;
    }

    public void setIp(String ip) {
        m_ip = ip;
    }

    public NetworkType getNetworkType() {
        return m_networkType;
    }

    public void setNetworkType(NetworkType networkType) {
        m_networkType = networkType;
    }

    public int getPort() {
        return m_port;
    }

    public void setPort(int port) {
        m_port = port;
    }

    public String getProtocol() {
        return m_protocol;
    }

    public void setProtocol(String value) {
        m_protocol = value;
    }

    public String getApiKey() {

        return m_apiKey;
    }

    public void setApiKey(String apiKey) {
        m_apiKey = apiKey;
    }

    public NoteBytesObject getJsonObject() {
        NoteBytesObject nbo = new NoteBytesObject();
        nbo.add("id", m_id);
        nbo.add("name", m_name);
        nbo.add("protocol", m_protocol);
        nbo.add("ip", m_ip);
        nbo.add("port", m_port);
        if (m_apiKey != null && m_apiKey.length() > 0) {
            nbo.add("apiKey", m_apiKey);
        }
        nbo.add("networkType", m_networkType == null ? MAINNET_STRING : m_networkType.toString());
        return nbo;
    }


    public SimpleObjectProperty<LocalDateTime> lastUpdatedProperty() {
        return m_lastUpdated;
    }

    public String getUrlString() {
        return m_protocol + "://" + m_ip + ":" + m_port ;
    }

    public String getRowString() {
        String formattedName = String.format("%-28s", m_name);
        String formattedUrl = String.format("%-30s", "(" + getUrlString() + ")");

        return formattedName + " " + formattedUrl;
    }

    @Override
    public String toString() {
        return m_name;
    }
}
