package io.netnotes.engine.utils.github;

public class GitHubFileInfo {
    private final GitHubInfo m_githubInfo;
    private final String m_fileName;
    private final String m_fileExt;

    public GitHubFileInfo(GitHubInfo gitHubInfo, String fileName, String fileExt){
        m_githubInfo = gitHubInfo;
        m_fileName = fileName;
        m_fileExt = fileExt;
    }

    public GitHubInfo getGitHubInfo(){
        return m_githubInfo;
    }

    public String getFileName() {
        return m_fileName;
    }


    public String getFileExt() {
        return m_fileExt;
    }


    
    
}
