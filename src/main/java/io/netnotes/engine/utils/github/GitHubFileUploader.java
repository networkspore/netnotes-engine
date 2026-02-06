package io.netnotes.engine.utils.github;

import java.io.File;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.files.FileStreamUtils;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;

public class GitHubFileUploader {

    private final String token; // Personal Access Token (PAT)
    private final GitHubInfo gitHubInfo;
    private final ExecutorService execService;

    public GitHubFileUploader(GitHubInfo gitHubInfo, String token, ExecutorService executorService) {
        this.token = token;
        this.gitHubInfo = gitHubInfo;
        this.execService = executorService;
    }
    
    public CompletableFuture<Boolean> uploadFile(String path, File file, String commitMessage) {
        return uploadFile(gitHubInfo, token, path, commitMessage, file, execService);
    }

    public static CompletableFuture<Boolean> uploadFile(GitHubInfo gitHubInfo, String token, String path,String commitMessage, File file,  ExecutorService execService) {
        PipedOutputStream outputStream = new PipedOutputStream();
        CompletableFuture<Void> transferFuture = FileStreamUtils.fileToPipe(file, outputStream, execService);

        CompletableFuture<Boolean> writeFuture = uploadFile(gitHubInfo, token, path, commitMessage, outputStream,  execService);

        return CompletableFuture.allOf(transferFuture, writeFuture).thenCompose(v -> writeFuture);
    }

    public CompletableFuture<Boolean> uploadFile(String path, String commitMessage, PipedOutputStream pipedOutputStream) {
        return uploadFile(gitHubInfo, token, path,commitMessage, pipedOutputStream,  execService);
    }

    public static CompletableFuture<Boolean> uploadFile(GitHubInfo gitHubInfo, String token, String path, String commitMessage, PipedOutputStream pipedOutput,  ExecutorService execService)  {
        return CompletableFuture.supplyAsync(()->{
             String apiUrl = GitHubAPI.getUrlRepoContentsPath(gitHubInfo, path);

            try( PipedInputStream inputStream = new PipedInputStream(pipedOutput, StreamUtils.PIPE_BUFFER_SIZE)) {
        
                String sha = GitHubAPI.getFileSha(apiUrl, token);

                HttpURLConnection conn = UrlStreamHelpers.getHttpUrlConnection(apiUrl, null, UrlStreamHelpers.HTTP_PUT);
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                
                try (
                    OutputStream os = conn.getOutputStream();
                    OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                    JsonWriter writer = new JsonWriter(osw);
                
                ) {
                    writer.beginObject();
                    writer.name("message").value(commitMessage);
                    writer.name("content");
                    
                    // Manually write the opening quote for the JSON string
                    osw.write('"');
                    // Stream the file through Base64 encoding directly to the output stream
                    try (OutputStream base64Stream = Base64.getEncoder().wrap(os)) {
                        inputStream.transferTo(base64Stream);
                    }
                    // Manually write the closing quote
                    osw.write('"');
                    
                    if (sha != null) {
                        writer.name("sha").value(sha);
                    }
                    writer.endObject();
                    writer.flush();
                }

                // Step 4 — read response
                int status = conn.getResponseCode();
                String responseBody = GitHubAPI.readResponse(conn);

                if (status >= 200 && status < 300) {
                    Log.logMsg("Upload successful!");
                    return true;
                } else {
                    Log.logError("Upload failed: " + status);
                    Log.logError(responseBody);
                    return false;
                }

            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }, execService);
       
    }
}

