package io.netnotes.engine;

import io.netnotes.engine.core.NetworksData;

public class NetworkLocation{

    private final NoteUUID m_localUUID;
    private final Network m_network;
    private final int m_hashCode;
    private final String m_type;

    public NetworkLocation(Network network, String locationType){
        m_type = locationType;
        m_network = network;
        m_localUUID = NoteUUID.createLocalUUID128();
        m_hashCode = m_localUUID.getAsBigInteger().hashCode();
    }

    public String getUUID(){
        return m_localUUID.getAsUrlSafeString();
    }

    public NoteUUID getNoteUUID(){
        return m_localUUID;
    }

    public String getLocationType(){
        return m_type;
    }

    public boolean isApp(){
        return m_type.equals(NetworksData.APPS);
    }

    public boolean isNetwork(){
        return m_type.equals(NetworksData.NETWORKS);
    }

    public NetworkInformation getNetworkInformation(){
        return m_network.getNetworkInformation();
    }

    @Override
    public int hashCode(){
        return m_hashCode;
    }

    public String getNetworkId(){
        return m_network.getNetworkId();
    }

    public Network getNetwork(){
        return m_network;
    }
}

