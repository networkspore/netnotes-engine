package io.netnotes.engine.noteBytes;

import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class NoteStatusMsg {
    private int m_code = -1;
    private long m_created = System.currentTimeMillis();
    private long m_timeStamp = -1;
    private String m_msg = null;
    private String m_header = null;


    public NoteStatusMsg(int code, long timeStamp,String header, String msg ){
        m_code = code;
        m_timeStamp = timeStamp;
        m_header = header;
        m_msg = msg;
    }

    public NoteStatusMsg(JsonReader reader) throws Exception{
        if(reader.peek() == JsonToken.BEGIN_OBJECT){
        
        reader.beginObject();
        while(reader.hasNext()){
          
            switch(reader.nextName()){
                case "code":
                    m_code = reader.nextInt();
                    break;
                case "timeStamp":
                case "timestamp":
                    m_timeStamp = reader.nextLong();
                    break;
                case "msg":
                    m_msg = reader.nextString();
                    break;
                case "header":
                    m_header = reader.nextString();
                    break;
                default:
                    reader.skipValue();
            }
            
        }
        reader.endObject();
        }else{
            throw new Exception(NoteConstants.ERROR_INVALID);
        }
    }

    public void writeJson(JsonWriter writer) throws IOException{
        writer.beginObject();
        writer.name("code");
        writer.value(m_code);
        writer.name("timeStamp");
        writer.value(m_timeStamp);
        writer.name("header");
        writer.value(m_header);
        writer.name("msg");
        writer.value(m_msg);
        writer.endObject();
    }
    
    public int getCode() {
        return m_code;
    }

    public long getCreated() {
        return m_created;
    }

    public long getTimeStamp() {
        return m_timeStamp;
    }

    public String getMsg() {
        return m_msg;
    }

    public String getHeader() {
        return m_header;
    }
}
