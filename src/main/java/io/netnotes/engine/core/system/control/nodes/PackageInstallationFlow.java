package io.netnotes.engine.core.system.control.nodes;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.nodes.security.*;
import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * PackageInstallationFlow - Install package with trust verification
 * 
 * Installation Steps:
 * 1. Assess package trust level
 * 2. Show trust summary
 * 3. For open source: Offer code review (GitHub navigator)
 * 4. Review capabilities (if any special ones requested)
 * 5. Confirm with password
 * 6. Install
 * 
 * Trust-Based Flow:
 * - OFFICIAL: Quick install, minimal prompts
 * - VERIFIED_OPEN_SOURCE: Show trust, offer code review
 * - OPEN_SOURCE: Show trust, encourage code review
 * - VERIFIED_CLOSED: Show trust warning, explain risks
 * - COMMUNITY: Show strong warning, require explicit acceptance
 * - UNTRUSTED: Block installation
 */
public class PackageInstallationFlow {
    
    private final PackageInfo packageInfo;
    private final PackageTrust trust;
    private final Set<NodeCapability> requestedCapabilities;
    private final UIRenderer uiRenderer;
    private final ContextPath menuPath;
    
    private CompletableFuture<InstallationResult> resultFuture = new CompletableFuture<>();
    
    // User decisions
    private boolean trustAccepted = false;
    private boolean codeReviewed = false;
    private Set<NodeCapability> approvedCapabilities = new HashSet<>();
    
    public PackageInstallationFlow(
            PackageInfo packageInfo,
            PackageTrust trust,
            Set<NodeCapability> requestedCapabilities,
            UIRenderer uiRenderer,
            ContextPath basePath) {
        
        this.packageInfo = packageInfo;
        this.trust = trust;
        this.requestedCapabilities = requestedCapabilities;
        this.uiRenderer = uiRenderer;
        this.menuPath = basePath.append("install").append(packageInfo.getName());
    }
    
    /**
     * Start installation flow
     */
    public CompletableFuture<InstallationResult> start() {
        // Block untrusted packages immediately
        if (trust.shouldBlock()) {
            showUntrustedWarning();
            return resultFuture;
        }
        
        // Show trust summary
        showTrustSummary();
        return resultFuture;
    }
    
    /**
     * Show trust summary and decision
     */
    private void showTrustSummary() {
        MenuContext menu = new MenuContext(
            menuPath.append("trust"),
            "Install: " + packageInfo.getName(),
            uiRenderer
        );
        
        // Package info
        String pkgInfo = String.format(
            "Name: %s\nVersion: %s\nCategory: %s\n\n%s",
            packageInfo.getName(),
            packageInfo.getVersion(),
            packageInfo.getCategory(),
            packageInfo.getDescription()
        );
        menu.addInfoItem("package", pkgInfo);
        
        menu.addSeparator("Trust Assessment");
        
        // Trust summary
        menu.addInfoItem("trust", trust.getTrustSummary());
        
        // Show warnings if needed
        if (trust.requiresWarning()) {
            String warning = getWarningMessage();
            menu.addInfoItem("warning", "⚠️  " + warning);
        }
        
        menu.addSeparator("Actions");
        
        // For open source, offer code review
        if (trust.isCodeReviewable() && trust.getGitHubInfo() != null) {
            menu.addItem(
                "review_code",
                "🔍 Review Source Code",
                "Browse repository on GitHub before installing",
                () -> reviewSourceCode()
            );
        }
        
        // Show capabilities if any special ones requested
        if (!requestedCapabilities.isEmpty() && 
            hasSpecialCapabilities(requestedCapabilities)) {
            menu.addItem(
                "review_caps",
                "🔐 Review Permissions",
                "See what access this package requests",
                () -> reviewCapabilities()
            );
        }
        
        // Continue or cancel
        if (trust.getLevel() == PackageTrust.TrustLevel.OFFICIAL) {
            menu.addItem(
                "install",
                "✓ Install (Official Package)",
                "Install trusted official package",
                () -> confirmInstallation()
            );
        } else {
            menu.addItem(
                "continue",
                "→ Continue Installation",
                "Proceed to final confirmation",
                () -> {
                    trustAccepted = true;
                    confirmInstallation();
                }
            );
        }
        
        menu.addItem(
            "cancel",
            "✗ Cancel",
            "Do not install this package",
            () -> cancelInstallation()
        );
        
        menu.display();
    }
    
    /**
     * Review source code via GitHub navigator
     */
    private void reviewSourceCode() {
        GitHubNavigator navigator = new GitHubNavigator(
            trust.getGitHubInfo(),
            packageInfo.getName(),
            packageInfo.getVersion(),
            uiRenderer,
            menuPath
        );
        
        navigator.navigate()
            .thenAccept(result -> {
                codeReviewed = result.userBrowsedCode();
                
                if (result.approved()) {
                    trustAccepted = true;
                    uiRenderer.render(
                        UIProtocol.showMessage(
                            "✓ Code review completed\n" +
                            (codeReviewed ? 
                                "You reviewed the source code" : 
                                "Approved without detailed review")
                        )
                    );
                    showTrustSummary();
                } else {
                    uiRenderer.render(UIProtocol.showMessage("Installation cancelled after code review"));
                    cancelInstallation();
                }
            });
    }
    
    /**
     * Review capabilities
     */
    private void reviewCapabilities() {
        MenuContext menu = new MenuContext(
            menuPath.append("capabilities"),
            "Requested Permissions",
            uiRenderer
        );
        
        menu.addInfoItem("info",
            "This package requests the following system permissions:");
        
        menu.addSeparator("Permissions");
        
        // Separate by trust level
        Map<NodeCapability.TrustLevel, List<NodeCapability>> byLevel = new HashMap<>();
        
        for (NodeCapability cap : requestedCapabilities) {
            byLevel.computeIfAbsent(cap.getTrustLevel(), k -> new ArrayList<>())
                .add(cap);
        }
        
        // Show safe ones first
        if (byLevel.containsKey(NodeCapability.TrustLevel.SAFE)) {
            menu.addSeparator("Safe Permissions (Auto-granted)");
            for (NodeCapability cap : byLevel.get(NodeCapability.TrustLevel.SAFE)) {
                menu.addInfoItem(
                    "cap_" + cap.name(),
                    "✓ " + cap.getDisplayName() + "\n  " + cap.getDescription()
                );
            }
        }
        
        // Show ones requiring approval
        if (byLevel.containsKey(NodeCapability.TrustLevel.REQUIRES_APPROVAL)) {
            menu.addSeparator("Permissions Requiring Approval");
            for (NodeCapability cap : byLevel.get(NodeCapability.TrustLevel.REQUIRES_APPROVAL)) {
                boolean approved = approvedCapabilities.contains(cap);
                String icon = approved ? "✓" : "○";
                
                menu.addItem(
                    "cap_" + cap.name(),
                    icon + " " + cap.getDisplayName(),
                    cap.getDescription(),
                    () -> toggleCapability(cap)
                );
            }
        }
        
        // Show dangerous ones
        if (byLevel.containsKey(NodeCapability.TrustLevel.DANGEROUS)) {
            menu.addSeparator("⚠️  Dangerous Permissions");
            for (NodeCapability cap : byLevel.get(NodeCapability.TrustLevel.DANGEROUS)) {
                boolean approved = approvedCapabilities.contains(cap);
                String icon = approved ? "✓" : "○";
                
                menu.addItem(
                    "cap_" + cap.name(),
                    icon + " " + cap.getDisplayName(),
                    "⚠️  " + cap.getDescription(),
                    () -> toggleCapability(cap)
                );
            }
        }
        
        menu.addSeparator("Navigation");
        
        menu.addItem(
            "approve_all",
            "✓ Approve All",
            "Grant all requested permissions",
            () -> {
                approvedCapabilities.addAll(requestedCapabilities);
                reviewCapabilities(); // Refresh
            }
        );
        
        menu.addItem(
            "back",
            "← Back",
            "Return to installation",
            () -> showTrustSummary()
        );
        
        menu.display();
    }
    
    /**
     * Toggle capability approval
     */
    private void toggleCapability(NodeCapability cap) {
        if (approvedCapabilities.contains(cap)) {
            approvedCapabilities.remove(cap);
        } else {
            approvedCapabilities.add(cap);
        }
        reviewCapabilities(); // Refresh display
    }
    
    /**
     * Final confirmation
     */
    private void confirmInstallation() {
        MenuContext menu = new MenuContext(
            menuPath.append("confirm"),
            "Confirm Installation",
            uiRenderer
        );
        
        StringBuilder summary = new StringBuilder();
        summary.append("Ready to install:\n\n");
        summary.append(packageInfo.getName()).append(" v").append(packageInfo.getVersion()).append("\n");
        summary.append(trust.getLevel().getIcon()).append(" ")
               .append(trust.getLevel().getDisplayName()).append("\n\n");
        
        if (codeReviewed) {
            summary.append("✓ You reviewed the source code\n");
        }
        
        if (!approvedCapabilities.isEmpty()) {
            summary.append("\nGranting ").append(approvedCapabilities.size())
                   .append(" permission(s)\n");
        }
        
        menu.addInfoItem("summary", summary.toString());
        
        menu.addSeparator("Final Confirmation");
        
        menu.addItem(
            "install",
            "✓ Install Now",
            "Enter password to confirm installation",
            () -> requestPasswordAndInstall()
        );
        
        menu.addItem(
            "back",
            "← Review Again",
            "Return to trust summary",
            () -> showTrustSummary()
        );
        
        menu.addItem(
            "cancel",
            "✗ Cancel",
            "Do not install",
            () -> cancelInstallation()
        );
        
        menu.display();
    }
    
    /**
     * Request password confirmation
     */
    private void requestPasswordAndInstall() {
        // This would trigger password entry
        // For now, simulate success
        completeInstallation(true);
    }
    
    /**
     * Show untrusted warning and block
     */
    private void showUntrustedWarning() {
        MenuContext menu = new MenuContext(
            menuPath.append("untrusted"),
            "⛔ Installation Blocked",
            uiRenderer
        );
        
        menu.addInfoItem("warning",
            "⛔ UNTRUSTED PACKAGE\n\n" +
            "This package failed trust verification:\n\n" +
            trust.getTrustSummary() + "\n\n" +
            "Installation has been blocked for your security.\n\n" +
            "If you believe this is an error, contact the package publisher."
        );
        
        menu.addItem(
            "ok",
            "OK",
            "Return to package browser",
            () -> cancelInstallation()
        );
        
        menu.display();
    }
    
    /**
     * Cancel installation
     */
    private void cancelInstallation() {
        completeInstallation(false);
    }
    
    /**
     * Complete installation flow
     */
    private void completeInstallation(boolean approved) {
        // Add default capabilities
        Set<NodeCapability> finalCapabilities = new HashSet<>(NodeCapability.getDefaultCapabilities());
        finalCapabilities.addAll(approvedCapabilities);
        
        InstallationResult result = new InstallationResult(
            approved,
            trust,
            codeReviewed,
            trustAccepted,
            finalCapabilities
        );
        
        resultFuture.complete(result);
    }
    
    // ===== UTILITIES =====
    
    /**
     * Check if capabilities include non-default ones
     */
    private boolean hasSpecialCapabilities(Set<NodeCapability> caps) {
        Set<NodeCapability> defaults = NodeCapability.getDefaultCapabilities();
        for (NodeCapability cap : caps) {
            if (!defaults.contains(cap)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get warning message based on trust level
     */
    private String getWarningMessage() {
        return switch (trust.getLevel()) {
            case VERIFIED_CLOSED -> 
                "This is closed source software from a verified publisher. " +
                "You cannot review the code. Installation requires trust in the publisher.";
            case COMMUNITY ->
                "This is a community package from an unknown source. " +
                "Code is not reviewable. Install only if you trust the source.";
            case UNTRUSTED ->
                "This package failed verification. Installation is not recommended.";
            default -> "";
        };
    }
    
    // ===== RESULT =====
    
    public record InstallationResult(
        boolean approved,
        PackageTrust trust,
        boolean codeReviewed,
        boolean trustAccepted,
        Set<NodeCapability> grantedCapabilities
    ) {
        /**
         * Create NodeSecurityPolicy from installation result
         */
        public NodeSecurityPolicy createPolicy(NoteBytesReadOnly nodeId, String userId) {
            NodeSecurityPolicy policy = new NodeSecurityPolicy(nodeId, userId);
            
            // Grant approved capabilities
            for (NodeCapability cap : grantedCapabilities) {
                // Map NodeCapability to capability name
                // (Simplified - would need proper mapping)
                policy.grantCapability(cap.name());
            }
            
            // Record installation context
            policy.approve();
            
            return policy;
        }
        
        /**
         * Get installation summary for logging
         */
        public String getSummary() {
            return String.format(
                "Installation %s: %s, Code reviewed: %s, Trust: %s, Capabilities: %d",
                approved ? "APPROVED" : "CANCELLED",
                trust.getLevel().getDisplayName(),
                codeReviewed,
                trustAccepted,
                grantedCapabilities.size()
            );
        }
    }
}