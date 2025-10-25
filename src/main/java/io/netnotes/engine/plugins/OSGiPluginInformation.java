package io.netnotes.engine.plugins;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.utils.github.GitHubFileInfo;

public class OSGiPluginInformation {

    private NoteBytes m_appId;
    private String m_pluginName;
    private String m_iconUrl;
    private String m_smallIconUrl;
    private String m_description;
    private GitHubFileInfo[] m_gitHubFiles;

    public OSGiPluginInformation(NoteBytes appId, String pluginName, String iconUrl, String smallIconUrl, String description, GitHubFileInfo... gitHubFiles){
        m_appId = appId;
        m_pluginName = pluginName;
        m_iconUrl = iconUrl;
        m_smallIconUrl = smallIconUrl;
        m_description = description;
        m_gitHubFiles = gitHubFiles;
    }

    public String getDescription(){
        return m_description;
    }

    public NoteBytes getAppId(){
        return m_appId;
    }

    public String getName(){
        return m_pluginName;
    }

    public String getIcon(){
        return m_iconUrl;
    }

    public String getSmallIcon(){
        return m_smallIconUrl;
    }

    public GitHubFileInfo[] getGitHubFiles(){
        return m_gitHubFiles;
    }


}