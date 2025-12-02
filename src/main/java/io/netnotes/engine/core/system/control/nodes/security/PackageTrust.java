package io.netnotes.engine.core.system.control.nodes.security;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.github.GitHubInfo;

/**
 * PackageTrust - Informational metadata about package source
 * 
 * NO TRUST SCORING. NO BLOCKING. Just facts.
 * 
 * Provides:
 * - Where it came from (GitHub, URL, local)
 * - Is code reviewable?
 * - Signature verification status
 * - Publisher information
 * 
 * User sees this info and decides for themselves.
 * Password required for all installations.
 */
public class PackageTrust {
    
    private final String sourceType;  // "github", "url", "local"
    private final GitHubInfo gitHubInfo;  // If from GitHub
    private final String sourceUrl;
    private final boolean codeReviewable;  // Can user review source?
    private final String publisherName;
    
    // Signature verification (factual, not prescriptive)
    private final boolean signaturePresent;
    private final boolean signatureValid;
    private final String signaturePublicKey;
    private final long verifiedAt;
    
    private PackageTrust(Builder builder) {
        this.sourceType = builder.sourceType;
        this.gitHubInfo = builder.gitHubInfo;
        this.sourceUrl = builder.sourceUrl;
        this.codeReviewable = builder.codeReviewable;
        this.publisherName = builder.publisherName;
        this.signaturePresent = builder.signaturePresent;
        this.signatureValid = builder.signatureValid;
        this.signaturePublicKey = builder.signaturePublicKey;
        this.verifiedAt = builder.verifiedAt;
    }
    
    // ===== GETTERS =====
    
    public String getSourceType() { return sourceType; }
    public GitHubInfo getGitHubInfo() { return gitHubInfo; }
    public String getSourceUrl() { return sourceUrl; }
    public boolean isCodeReviewable() { return codeReviewable; }
    public String getPublisherName() { return publisherName; }
    public boolean isSignaturePresent() { return signaturePresent; }
    public boolean isSignatureValid() { return signatureValid; }
    public String getSignaturePublicKey() { return signaturePublicKey; }
    public long getVerifiedAt() { return verifiedAt; }
    
    // ===== USER-FACING INFO =====
    
    /**
     * Get factual information summary for user review
     */
    public String getInfoSummary() {
        StringBuilder sb = new StringBuilder();
        
        // Source
        if (gitHubInfo != null) {
            sb.append("Source: GitHub\n");
            sb.append("  Repository: ")
              .append(gitHubInfo.getUser()).append("/")
              .append(gitHubInfo.getProject()).append("\n");
        } else {
            sb.append("Source: ").append(sourceType).append("\n");
            if (sourceUrl != null) {
                sb.append("  URL: ").append(sourceUrl).append("\n");
            }
        }
        
        // Publisher
        if (publisherName != null) {
            sb.append("Publisher: ").append(publisherName).append("\n");
        }
        
        // Code reviewability
        if (codeReviewable) {
            sb.append("✓ Source code is available for review\n");
        } else {
            sb.append("• Closed source (code not available for review)\n");
        }
        
        // Signature status (factual)
        if (signaturePresent) {
            if (signatureValid) {
                sb.append("✓ Package signature verified\n");
            } else {
                sb.append("⚠ Package signature present but invalid\n");
            }
        } else {
            sb.append("• No package signature\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Get list of facts for UI display
     */
    public List<String> getFactsList() {
        List<String> facts = new ArrayList<>();
        
        if (gitHubInfo != null) {
            facts.add("GitHub: " + gitHubInfo.getUser() + "/" + gitHubInfo.getProject());
        } else {
            facts.add("Source: " + sourceType);
        }
        
        if (publisherName != null) {
            facts.add("Publisher: " + publisherName);
        }
        
        facts.add(codeReviewable ? 
            "Code reviewable: Yes" : 
            "Code reviewable: No (closed source)");
        
        if (signaturePresent) {
            facts.add(signatureValid ? 
                "Signature: Valid" : 
                "Signature: Invalid");
        } else {
            facts.add("Signature: Not present");
        }
        
        return facts;
    }
    
    // ===== SERIALIZATION =====
    
    public NoteBytesObject toNoteBytes() {
        NoteBytesMap map = new NoteBytesMap();
        
        map.put("source_type", sourceType);
        
        if (gitHubInfo != null) {
            map.put("github_info", gitHubInfo.getNoteBytesObject());
        }
        
        if (sourceUrl != null) {
            map.put("source_url", sourceUrl);
        }
        
        map.put("code_reviewable", codeReviewable);
        
        if (publisherName != null) {
            map.put("publisher_name", publisherName);
        }
        
        map.put("signature_present", signaturePresent);
        map.put("signature_valid", signatureValid);
        
        if (signaturePublicKey != null) {
            map.put("signature_key", signaturePublicKey);
        }
        
        map.put("verified_at", verifiedAt);
        
        return map.getNoteBytesObject();
    }
    
    public static PackageTrust fromNoteBytes(NoteBytesObject obj) {
        NoteBytesMap map = obj.getAsNoteBytesMap();
        
        Builder builder = new Builder();
        
        builder.sourceType = map.get("source_type").getAsString();
        
        NoteBytes githubBytes = map.get("github_info");
        if (githubBytes != null) {
            builder.gitHubInfo = GitHubInfo.of(githubBytes.getAsNoteBytesMap());
        }
        
        NoteBytes urlBytes = map.get("source_url");
        if (urlBytes != null) {
            builder.sourceUrl = urlBytes.getAsString();
        }
        
        builder.codeReviewable = map.get("code_reviewable").getAsBoolean();
        
        NoteBytes publisherBytes = map.get("publisher_name");
        if (publisherBytes != null) {
            builder.publisherName = publisherBytes.getAsString();
        }
        
        builder.signaturePresent = map.get("signature_present").getAsBoolean();
        builder.signatureValid = map.get("signature_valid").getAsBoolean();
        
        NoteBytes keyBytes = map.get("signature_key");
        if (keyBytes != null) {
            builder.signaturePublicKey = keyBytes.getAsString();
        }
        
        builder.verifiedAt = map.get("verified_at").getAsLong();
        
        return builder.build();
    }
    
    // ===== BUILDER =====
    
    public static class Builder {
        private String sourceType;
        private GitHubInfo gitHubInfo;
        private String sourceUrl;
        private boolean codeReviewable;
        private String publisherName;
        private boolean signaturePresent;
        private boolean signatureValid;
        private String signaturePublicKey;
        private long verifiedAt = System.currentTimeMillis();
        
        public Builder sourceType(String type) {
            this.sourceType = type;
            return this;
        }
        
        public Builder gitHub(GitHubInfo info) {
            this.gitHubInfo = info;
            this.sourceType = "github";
            this.codeReviewable = true; // GitHub is always reviewable
            return this;
        }
        
        public Builder sourceUrl(String url) {
            this.sourceUrl = url;
            return this;
        }
        
        public Builder codeReviewable(boolean reviewable) {
            this.codeReviewable = reviewable;
            return this;
        }
        
        public Builder publisher(String name) {
            this.publisherName = name;
            return this;
        }
        
        public Builder signature(boolean present, boolean valid, String publicKey) {
            this.signaturePresent = present;
            this.signatureValid = valid;
            this.signaturePublicKey = publicKey;
            return this;
        }
        
        public PackageTrust build() {
            if (sourceType == null) {
                throw new IllegalStateException("Source type required");
            }
            return new PackageTrust(this);
        }
    }
    
    // ===== FACTORY METHODS =====
    
    /**
     * Create trust info for GitHub package
     */
    public static PackageTrust fromGitHub(GitHubInfo info, String publisherName, boolean signed) {
        return new Builder()
            .gitHub(info)
            .publisher(publisherName)
            .signature(signed, signed, null) // If signed, assume valid for now
            .build();
    }
    
    /**
     * Create trust info for direct URL
     */
    public static PackageTrust fromUrl(String url, boolean codeReviewable, boolean signed) {
        return new Builder()
            .sourceType("url")
            .sourceUrl(url)
            .codeReviewable(codeReviewable)
            .signature(signed, signed, null)
            .build();
    }
    
    /**
     * Create trust info for local file
     */
    public static PackageTrust fromLocal(String path, boolean codeReviewable) {
        return new Builder()
            .sourceType("local")
            .sourceUrl(path)
            .codeReviewable(codeReviewable)
            .signature(false, false, null)
            .build();
    }
}