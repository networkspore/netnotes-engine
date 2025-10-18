package io.netnotes.engine.utils.github;

public class GitHubInfo {
    private final String m_user;
    private final  String m_project;

    public GitHubInfo(String user, String project){
        m_user = user;
        m_project = project;
    }

    public String getUser() {
        return m_user;
    }


    public String getProject() {
        return m_project;
    }

}
