package io.netnotes.engine;


public class NetworkLocation{

    private final NoteUUID m_localUUID;
    private final Network m_network;
    private final int m_hashCode;
    private final int m_type;

    public NetworkLocation(Network network, int locationType){
        m_type = locationType;
        m_network = network;
        m_localUUID = NoteUUID.createLocalUUID128();
        m_hashCode = m_localUUID.getAsBigInteger().hashCode();
    }

    public NoteBytes getId(){
        return m_localUUID;
    }

    public int getLocationType(){
        return m_type;
    }

    public boolean isApp(){
        return m_type == NoteConstants.APPS;
    }

    public boolean isNetwork(){
        return m_type == NoteConstants.NETWORKS;
    }

    public NetworkInformation getNetworkInformation(){
        return m_network.getNetworkInformation();
    }

    @Override
    public int hashCode(){
        return m_hashCode;
    }

    public NoteBytes getNetworkId(){
        return m_network.getNetworkId();
    }

    public Network getNetwork(){
        return m_network;
    }
}

