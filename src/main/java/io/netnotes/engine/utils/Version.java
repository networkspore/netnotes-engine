package io.netnotes.engine.utils;

public class Version implements Comparable<Version> {
    public final static String UKNOWN_VERSION = "0.0.0";
    private String m_version;

     public Version(){
        m_version = "0.0.0";
    }

    public Version(String version) {
       set(version);
    }

    public final String get() {
        return m_version;
    }

    private void set(String version){
         if (version == null) {
            this.m_version = UKNOWN_VERSION;
        }
        if (!version.matches("[0-9]+(\\.[0-9]+)*")) {
            this.m_version = UKNOWN_VERSION;
        }
        this.m_version = version;
    }
    

    @Override
    public int compareTo(Version that) {
        if (that == null) {
            throw new NullPointerException("Version.compareTo(Version that - is null)");
        }
        String[] thisParts = m_version.split("\\.");
        String[] thatParts = that.get().split("\\.");
        int length = Math.max(thisParts.length, thatParts.length);
        for (int i = 0; i < length; i++) {
            int thisPart = i < thisParts.length
                    ? Integer.parseInt(thisParts[i]) : 0;
            int thatPart = i < thatParts.length
                    ? Integer.parseInt(thatParts[i]) : 0;
            if (thisPart < thatPart) {
                return -1;
            }
            if (thisPart > thatPart) {
                return 1;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object that) {
        if (that == null) {
            throw new NullPointerException("Version.equals(Version that - is null)");
        }
        if (this == that) {
            return true;
        }
        if (!(that instanceof Version)) {
            return false;
        }
        return this.compareTo((Version) that) == 0;
    }

    @Override
    public String toString(){
        return get();
    }

}
