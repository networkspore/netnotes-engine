package io.netnotes.engine;

import javafx.scene.image.Image;

public class NetworkInformation {
    private String m_networkId;
    private String m_networkName;
    private Image m_icon;
    private Image m_smallIcon;
    private String m_description;

    public NetworkInformation(String networkId, String networkName, Image icon){
        m_networkId = networkId;
        m_networkName = networkName;
        m_icon = icon;
        m_smallIcon = icon;
    }

    public NetworkInformation(String networkId, String networkName, Image icon, Image smallIcon, String description){
        m_networkId = networkId;
        m_networkName = networkName;
        m_icon = icon;
        m_smallIcon = smallIcon;
        m_description = description;
    }

    public String getDescription(){
        return m_description;
    }

    public String getNetworkId(){
        return m_networkId;
    }

    public String getNetworkName(){
        return m_networkName;
    }

    public Image getIcon(){
        return m_icon;
    }

    public Image getSmallIcon(){
        return m_smallIcon;
    }

}