package io.netnotes.engine.utils.github;

public class GitHubAsset{
    private String m_name;
    private String m_url;
    private String m_contentType;
    private long m_size;
    private String m_tagName;

    public GitHubAsset(String name, String url, String contentType, long size){
        m_name = name;
        m_url = url;
        m_contentType = contentType;
        m_size = size;
    }
    
    public GitHubAsset(String name, String url, String contentType, long size, String tagName){
        m_name = name;
        m_url = url;
        m_contentType = contentType;
        m_size = size;
        m_tagName = tagName;
    }

    public String getTagName(){
        return m_tagName;
    }

    public String getName(){
        return m_name;
    }

    public String getUrl(){
        return m_url;
    }

    public String getContentType(){
        return m_contentType;
    }

    public long getSize(){
        return m_size;
    }
}