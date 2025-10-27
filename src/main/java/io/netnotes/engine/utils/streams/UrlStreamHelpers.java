package io.netnotes.engine.utils.streams;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.netnotes.engine.utils.streams.StreamUtils.StreamProgressTracker;

public class UrlStreamHelpers {
    public final static String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36";
    public final static String HTTP_GET = "GET";
    public final static String HTTP_PUT = "PUT";
    public final static String HTTP_ACCEPT = "Accept";
    
    public final static int URL_BUFFER_SIZE = 1024;

    public static InputStream newUrlStream(String urlString) throws IOException, URISyntaxException{
        return getHttpUrlConnection(urlString).getInputStream();
    }
   
    public static HttpURLConnection getHttpUrlConnection(String urlString)throws IOException, URISyntaxException{
        HttpURLConnection connection = getHttpUrlConnection(urlString, USER_AGENT, HTTP_GET);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }
        return connection;
    }

    public static HttpURLConnection getHttpUrlConnection(String urlString, String userAgent, String method)throws IOException, URISyntaxException{
        HttpURLConnection connection = (HttpURLConnection) new URI(urlString)
            .toURL()
            .openConnection();
        if(userAgent != null){
            connection.setRequestProperty("User-Agent", userAgent);
        }
        if(method != null){
            connection.setRequestMethod(method);
        }
        return connection;
    }


    public static byte[] getUrlToBytesSync(String urlString) throws IOException, URISyntaxException{
        try(
            InputStream inputStream = getHttpUrlConnection(urlString).getInputStream();
        ){
            return StreamUtils.readInputStreamAsBytes(inputStream);
        }
    }
    

    public static CompletableFuture<JsonObject> getUrlJson(String urlString, ExecutorService execService) {
        return CompletableFuture.supplyAsync(()->{
            try(
                InputStream inputStream = getHttpUrlConnection(urlString).getInputStream();
                InputStreamReader reader = new InputStreamReader(inputStream);
            ) {
                return StreamUtils.readJson(reader);
            }catch(IOException e){
                throw new CompletionException("Stream failed to complete", e);
            } catch (URISyntaxException e) {
                throw new CompletionException("Invalid URL syntax", e);
            }
        }, execService);
    }
    


    public static CompletableFuture<JsonArray> getUrlJsonArray(String urlString, ExecutorService execService) {

        return CompletableFuture.supplyAsync(()->{
            try(
                InputStream inputStream = getHttpUrlConnection(urlString).getInputStream();
                InputStreamReader reader = new InputStreamReader(inputStream);
            ) {
                return StreamUtils.readJsonArray(reader);
            }catch(IOException e){
                throw new CompletionException("Stream failed to complete", e);
            } catch (URISyntaxException e) {
                throw new CompletionException("Invalid URL syntax", e);
            }
        }, execService);
    }


    public static CompletableFuture<Void> copyUrlStream(String urlString, PipedOutputStream pipedOutputStream,
        StreamProgressTracker progressTracker, ExecutorService execService
    ){
        return CompletableFuture.runAsync(()->{
            try(OutputStream outputStream = pipedOutputStream){

                try(
                    InputStream inputStream = getHttpUrlConnection(urlString).getInputStream()
                ){
                    StreamUtils.streamCopy(inputStream, outputStream, progressTracker);
                }
            }catch(IOException e){
                throw new CompletionException("Stream did not finish", e);
            } catch (URISyntaxException e) {
                throw new CompletionException("Malformed url", e);
            }
        }, execService);
    }
}
