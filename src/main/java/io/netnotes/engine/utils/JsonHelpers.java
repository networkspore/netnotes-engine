package io.netnotes.engine.utils;

import java.io.IOException;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class JsonHelpers {
    public static String parseMsgForJsonId(String msg){
        if(msg != null){
            JsonParser jsonParser = new JsonParser();

            JsonElement jsonElement = jsonParser.parse(msg);

            if(jsonElement != null && jsonElement.isJsonObject()){
                JsonObject json = jsonElement.getAsJsonObject();
                JsonElement idElement = json.get("id");
                if(idElement != null && !idElement.isJsonNull()){
                    return idElement.getAsString();
                }
            }
        }

        return null;
    }

    public static int getJsonElementType(JsonElement jsonElement){
        return jsonElement.isJsonNull() ? -1 : jsonElement.isJsonObject() ? 1 : jsonElement.isJsonArray() ? 2 : jsonElement.isJsonPrimitive() ? 3 : 0;
    }


    public static void copyJsonValue(JsonReader reader, JsonWriter writer) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_ARRAY:
                writer.beginArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    copyJsonValue(reader, writer);
                }
                reader.endArray();
                writer.endArray();
                break;
            case BEGIN_OBJECT:
                writer.beginObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    writer.name(reader.nextName());
                    copyJsonValue(reader, writer);
                }
                reader.endObject();
                writer.endObject();
                break;
            case STRING:
                writer.value(reader.nextString());
                break;
            case NUMBER:
                String numStr = reader.nextString();
                // Try to parse as long first
                try {
                    writer.value(Long.parseLong(numStr));
                } catch (NumberFormatException e) {
                    // If not a long, write as double
                    writer.value(Double.parseDouble(numStr));
                }
                break;
            case BOOLEAN:
                writer.value(reader.nextBoolean());
                break;
            case NULL:
                reader.nextNull();
                writer.nullValue();
                break;
            default:
                throw new IllegalStateException("Unexpected JSON token: " + token);
        }
    }

    public static void mergeJsonTrees(JsonReader reader, JsonWriter writer, JsonObject json) throws IOException {
        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
            writer.beginObject();
            reader.beginObject();
            
            while (reader.hasNext()) {
                String name = reader.nextName();
                writer.name(name);
                
                if (json.has(name)) {
                    JsonElement element = json.get(name);
                    if (element.isJsonObject()) {
                        mergeJsonTrees(reader, writer, element.getAsJsonObject());
                    } else {
                        copyJsonValue(reader, writer);
                    }
                } else {
                    copyJsonValue(reader, writer);
                }
            }
            
            // Write any remaining properties from json that weren't in reader
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String name = entry.getKey();
                JsonElement element = entry.getValue();
                if (!reader.hasNext()) {
                    writer.name(name);
                    writeJsonElement(writer, element);
                }
            }
            
            reader.endObject();
            writer.endObject();
        } else {
            copyJsonValue(reader, writer);
        }
    }

    public static void writeJsonElement(JsonWriter writer, JsonElement element) throws IOException {
        if (element.isJsonNull()) {
            writer.nullValue();
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                writer.value(primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                writer.value(primitive.getAsNumber());
            } else {
                writer.value(primitive.getAsString());
            }
        } else if (element.isJsonArray()) {
            writer.beginArray();
            for (JsonElement e : element.getAsJsonArray()) {
                writeJsonElement(writer, e);
            }
            writer.endArray();
        } else if (element.isJsonObject()) {
            writer.beginObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                writer.name(entry.getKey());
                writeJsonElement(writer, entry.getValue());
            }
            writer.endObject();
        }
    }
}
