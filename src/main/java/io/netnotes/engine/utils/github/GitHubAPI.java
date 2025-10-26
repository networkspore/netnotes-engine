package io.netnotes.engine.utils.github;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import io.netnotes.engine.utils.streams.UrlStreamHelpers;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;


public class GitHubAPI {

    public final static String GITHUB_API_URL = "https://api.github.com";
    public final static String GITHUB_USER_CONTENT = "https://raw.githubusercontent.com";

    public final static String REPOS = "repos";
    public final static String RELEASES = "releases";
    public final static String LATEST = "latest";
    public final static String CONTENTS = "contents";

    private final GitHubInfo gitHubInfo;


    public GitHubAPI(GitHubInfo info){
        gitHubInfo = info;
    }

    public static String getUrlLatestRelease(GitHubInfo gitHubInfo){
        return GITHUB_API_URL + "/" +REPOS+"/" + gitHubInfo.getUser() + "/" + gitHubInfo.getProject() + "/" +RELEASES + "/" + LATEST;
    }

    public String getUrlLatestRelease(){
        return getUrlLatestRelease(gitHubInfo);
    }
    
    public String getUrlAllReleases(){
        return getUrlAllReleases(gitHubInfo);
    }
    public static String getUrlAllReleases(GitHubInfo gitHubInfo){
        return GITHUB_API_URL + "/" + REPOS + "/" + gitHubInfo.getUser() + "/" + gitHubInfo.getProject() + "/" + RELEASES;
    }
    
    public  String getUrlContentsPath( String path){
        return getUrlRepoContentsPath(gitHubInfo, path);
    }

    public static String encodePathString(String path){
        return URLEncoder.encode(path, StandardCharsets.UTF_8)
                                  .replace("+", "%20");
    }

    public static String getUrlRepoContentsPath(GitHubInfo gitHubInfo, String path){
        return GitHubAPI.GITHUB_API_URL + "/"+REPOS +"/" + gitHubInfo.getUser() + "/" + gitHubInfo.getProject() + 
            "/"+CONTENTS+"/" + encodePathString(path);
    }

    public static String getUrlUserContentPath(GitHubInfo gitHubInfo, String branch, String path){
        return  GitHubAPI.GITHUB_USER_CONTENT + "/" + gitHubInfo.getUser() + "/" + gitHubInfo.getProject() + 
            "/"+ branch+ "/" + encodePathString(path);
    }

    public CompletableFuture<GitHubAsset[]> getAssetsAllLatestRelease(ExecutorService execService){
        return getAssets(true, execService);
    }

    public CompletableFuture<GitHubAsset[]> getAssetsLatestRelease(ExecutorService execService){
         return getAssets(false, execService);
    }

    public CompletableFuture<GitHubAsset[]> getAssets(boolean isAllReleases, ExecutorService execService) {
        String urlString = isAllReleases ? getUrlAllReleases() : getUrlLatestRelease();

        return CompletableFuture.supplyAsync(() -> {
            List<GitHubAsset> assets = new ArrayList<>();

            try {
                HttpURLConnection conn = UrlStreamHelpers.getHttpUrlConnection(urlString);

                try (JsonReader reader = new JsonReader(new InputStreamReader(conn.getInputStream()))) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        switch (name) {
                            case "assets":
                                reader.beginArray();
                                while (reader.hasNext()) {
                                    GitHubAsset asset = GitHubAsset.read(reader);
                                    if (asset != null) {
                                        assets.add(asset);
                                    }
                                }
                                reader.endArray();
                            break;
                            default:
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch assets from latest release", e);
            }

            return assets.toArray(new GitHubAsset[0]);
        }, execService);
    }


   
 

    public static String getFileSha(String apiUrl, String token) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(apiUrl).toURL().openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            if (conn.getResponseCode() == 200) {
                String response = readResponse(conn);
                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                return json.get("sha").getAsString();
            }
        } catch (Exception e) {
            // File not found or unauthorized
        }
        return null;
    }

    public static String readResponse(HttpURLConnection conn) throws IOException {
        try (InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}