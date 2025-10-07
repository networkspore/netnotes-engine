package io.netnotes.engine.utils.streams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class UrlStreamHelpers {
    public final static String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36";
    public final static int URL_BUFFER_SIZE = 1024;
    public static String getUrlOutputStringSync(String urlString) throws IOException, URISyntaxException{
        byte[] bytes = getUrlToBytesSync(urlString);
        
        return new String(bytes);
    }

    public static byte[] getUrlToBytesSync(String urlString) throws IOException, URISyntaxException{
        URI uri = new URI(urlString);
        URL url = uri.toURL();
        URLConnection con = url.openConnection();
        con.setRequestProperty("User-Agent", USER_AGENT);

        try(
            InputStream inputStream = con.getInputStream();
            UnsynchronizedByteArrayOutputStream outputStream = new UnsynchronizedByteArrayOutputStream();
        ){

            byte[] buffer = new byte[URL_BUFFER_SIZE];

            int length;

            while ((length = inputStream.read(buffer)) != -1) {

                outputStream.write(buffer, 0, length);
              
            }
            return outputStream.toByteArray();
        }
    }
    

    public static CompletableFuture<JsonObject> getUrlJson(String urlString, ExecutorService execService) {
        return CompletableFuture.supplyAsync(()->{
            try{
                return getJsonFromUrlSync(urlString);
            }catch(IOException e){
                throw new CompletionException("Stream failed to complete", e);
            } catch (URISyntaxException e) {
                throw new CompletionException("Invalid URL syntax", e);
            }
        }, execService);
    }
    


    public static CompletableFuture<JsonArray> getUrlJsonArray(String urlString, ExecutorService execService) {

        return CompletableFuture.supplyAsync(()->{
            try{
                return getJsonArrayFromUrlSync(urlString);
             }catch(IOException e){
                throw new CompletionException("Stream failed to complete", e);
            } catch (URISyntaxException e) {
                throw new CompletionException("Invalid URL syntax", e);
            }
        }, execService);
    }

    public static JsonObject getJsonFromUrlSync(String urlString) throws IOException, URISyntaxException{
                                             
        String outputString = getUrlOutputStringSync(urlString);

        JsonElement jsonElement = outputString != null ? new JsonParser().parse(outputString) : null;

        JsonObject jsonObject = jsonElement != null && jsonElement.isJsonObject() ? jsonElement.getAsJsonObject() : null;

        return jsonObject == null ? null : jsonObject;
           

   }

    public static JsonArray getJsonArrayFromUrlSync(String urlString) throws IOException, URISyntaxException{
        String outputString = getUrlOutputStringSync(urlString);

        JsonElement jsonElement = outputString != null ? new JsonParser().parse(outputString) : null;

        return jsonElement != null && jsonElement.isJsonArray() ? jsonElement.getAsJsonArray() : null;
    }

    public static CompletableFuture<Long> getUrlToStream(String urlString, PipedOutputStream pipedOutputStream, ExecutorService execService){
        return CompletableFuture.supplyAsync(()->{
            try(OutputStream outputStream = pipedOutputStream){
                URI uri = new URI(urlString);
                URL url = uri.toURL();
                URLConnection con = url.openConnection();
                con.setRequestProperty("User-Agent", USER_AGENT);

                try(
                    InputStream inputStream = con.getInputStream();
                ){

                    byte[] buffer = new byte[URL_BUFFER_SIZE];

                    int length;
                    long bytesRead = 0;
                    while ((length = inputStream.read(buffer)) != -1) {

                        outputStream.write(buffer, 0, length);
                        bytesRead += length;
                    }
                    
                    return bytesRead;
                }
            }catch(IOException e){
                throw new CompletionException("Stream did not finish", e);
            } catch (URISyntaxException e) {
                throw new CompletionException("Malformed url", e);
            }
        }, execService);
    }
}
