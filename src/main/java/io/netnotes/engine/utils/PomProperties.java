package io.netnotes.engine.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PomProperties {

    Properties m_props;

    public PomProperties(Properties props) {
        m_props = props;
    }

    public static PomProperties create(String groupId, String artifactId) {
        try (InputStream inputStream = PomProperties.class.getResourceAsStream(
                 "/META-INF/maven/" +groupId +"/" + artifactId + "/pom.properties");
        ) {
            if (inputStream == null) throw new IOException("Pom unavailable");
            Properties props = new Properties();
            props.load(inputStream);
            return new PomProperties(props);
        }catch(IOException e){
            return new PomProperties(null);
        }
    }

    public Properties getProperties(){
        return m_props;
    }

    public Version getVersion(){
        return m_props == null ? new Version() : new Version(m_props.getProperty("version", "0.0.0"));
    }


}