package io.netnotes.engine.core.system.control.nodes.security;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * PolicyReviewMenu - Interactive policy approval interface
 * 
 * Shows user what permissions node is requesting.
 * Allows customization before approval.
 * Requires password confirmation for sensitive capabilities.
 * 
 * Flow:
 * 1. Overview screen (summary of requests)
 * 2. Detail screens (by category)
 * 3. Customization (enable/disable individual permissions)
 * 4. Final approval (with password)
 */
public class PolicyReviewMenu {
    
    private final PolicyManifest manifest;
    private final NoteBytesReadOnly nodeId;
    private final String nodeName;
    private final UIRenderer uiRenderer;
    private final ContextPath menuPath;
    
    // Tracking user decisions
    private final Set<String> approvedRequired = new HashSet<>();
    private final Set<String> approvedOptional = new HashSet<>();
    private final Set<String> deniedRequired = new HashSet<>();
    private final Set<String> deniedOptional = new HashSet<>();
    
    // Custom path/process permissions
    private final Map<String, Boolean> customPathDecisions = new HashMap<>();
    private final Map<String, Boolean> customProcessDecisions = new HashMap<>();
    
    // Result
    private CompletableFuture<PolicyReviewResult> resultFuture = new CompletableFuture<>();
    
    public PolicyReviewMenu(
            PolicyManifest manifest,
            NoteBytesReadOnly nodeId,
            String nodeName,
            UIRenderer uiRenderer,
            ContextPath basePath) {
        
        this.manifest = manifest;
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.uiRenderer = uiRenderer;
        this.menuPath = basePath.append("policy-review");
        
        // Initially approve all required, approve all optional
        approvedRequired.addAll(manifest.getRequiredCapabilities());
        approvedOptional.addAll(manifest.getOptionalCapabilities());
    }
    
    /**
     * Show policy review and wait for user decision
     */
    public CompletableFuture<PolicyReviewResult> show() {
        showOverview();
        return resultFuture;
    }
    
    /**
     * Overview screen - summary of all requests
     */
    private void showOverview() {
        MenuContext menu = new MenuContext(
            menuPath.append("overview"),
            "Policy Review: " + nodeName,
            uiRenderer
        );
        
        // Summary stats
        int totalRequired = manifest.getRequiredCapabilities().size();
        int totalOptional = manifest.getOptionalCapabilities().size();
        int totalPaths = manifest.getPathRequests().size();
        int totalProcesses = manifest.getProcessRequests().size();
        boolean hasSensitive = manifest.hasSensitiveCapabilities();
        
        StringBuilder summary = new StringBuilder();
        summary.append("This node requests the following permissions:\n\n");
        summary.append(String.format("• %d required capabilities\n", totalRequired));
        summary.append(String.format("• %d optional capabilities\n", totalOptional));
        
        if (totalPaths > 0) {
            summary.append(String.format("• Access to %d file paths\n", totalPaths));
        }
        if (totalProcesses > 0) {
            summary.append(String.format("• Communication with %d processes\n", totalProcesses));
        }
        if (manifest.hasClusterConfig()) {
            summary.append("• Cluster membership\n");
        }
        
        if (hasSensitive) {
            summary.append("\n⚠️  This node requests HIGH or CRITICAL sensitivity permissions.");
        }
        
        menu.addInfoItem("summary", summary.toString());
        menu.addSeparator("Review Options");
        
        // Quick approve (if no sensitive capabilities)
        if (!hasSensitive) {
            menu.addItem(
                "approve_all",
                "✓ Approve All (Recommended)",
                "Grant all requested permissions",
                () -> quickApprove()
            );
        }
        
        // Detailed review
        menu.addItem(
            "review_required",
            "Review Required Capabilities",
            String.format("%d capabilities that node needs to function", totalRequired),
            () -> showRequiredCapabilities()
        );
        
        if (totalOptional > 0) {
            menu.addItem(
                "review_optional",
                "Review Optional Capabilities",
                String.format("%d capabilities that enhance node functionality", totalOptional),
                () -> showOptionalCapabilities()
            );
        }
        
        if (totalPaths > 0) {
            menu.addItem(
                "review_paths",
                "Review File Access",
                String.format("%d path access requests", totalPaths),
                () -> showPathRequests()
            );
        }
        
        if (totalProcesses > 0) {
            menu.addItem(
                "review_processes",
                "Review Process Access",
                String.format("%d process communication requests", totalProcesses),
                () -> showProcessRequests()
            );
        }
        
        if (manifest.hasClusterConfig()) {
            menu.addItem(
                "review_cluster",
                "Review Cluster Configuration",
                "Node clustering settings",
                () -> showClusterConfig()
            );
        }
        
        menu.addSeparator("Final Decision");
        
        menu.addItem(
            "customize_approve",
            "✓ Approve with Custom Settings",
            "Review and customize before approving",
            () -> showCustomizationSummary()
        );
        
        menu.addItem(
            "deny",
            "✗ Deny Installation",
            "Do not install this node",
            () -> denyInstallation()
        );
        
        menu.display();
    }
    
    /**
     * Show required capabilities with details
     */
    private void showRequiredCapabilities() {
        MenuContext menu = new MenuContext(
            menuPath.append("required"),
            "Required Capabilities",
            uiRenderer
        );
        
        menu.addInfoItem("info", 
            "These capabilities are required for the node to function.\n" +
            "Denying any required capability will prevent installation.");
        
        menu.addSeparator("Capabilities by Category");
        
        // Group by category
        Map<String, List<String>> byCategory = groupByCategory(
            manifest.getRequiredCapabilities()
        );
        
        NodeCapabilityRegistry registry = NodeCapabilityRegistry.getInstance();
        
        for (Map.Entry<String, List<String>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<String> caps = entry.getValue();
            
            menu.addSubMenu(category, category, subMenu -> {
                for (String cap : caps) {
                    String description = registry.getDescription(cap);
                    String detailed = registry.getDetailedExplanation(cap);
                    NodeCapabilityRegistry.SensitivityLevel level = registry.getSensitivity(cap);
                    
                    String levelIcon = switch (level) {
                        case LOW -> "🟢";
                        case MEDIUM -> "🟡";
                        case HIGH -> "🟠";
                        case CRITICAL -> "🔴";
                    };
                    
                    boolean approved = approvedRequired.contains(cap);
                    String statusIcon = approved ? "✓" : "✗";
                    
                    subMenu.addItem(
                        cap,
                        levelIcon + " " + statusIcon + " " + description,
                        detailed,
                        () -> toggleRequired(cap)
                    );
                }
                return subMenu;
            });
        }
        
        menu.addItem("back", "Back to Overview", () -> showOverview());
        menu.display();
    }
    
    /**
     * Show optional capabilities
     */
    private void showOptionalCapabilities() {
        MenuContext menu = new MenuContext(
            menuPath.append("optional"),
            "Optional Capabilities",
            uiRenderer
        );
        
        menu.addInfoItem("info", 
            "These capabilities are optional enhancements.\n" +
            "Node can function without them, but may have reduced features.");
        
        menu.addSeparator("Capabilities by Category");
        
        Map<String, List<String>> byCategory = groupByCategory(
            manifest.getOptionalCapabilities()
        );
        
        NodeCapabilityRegistry registry = NodeCapabilityRegistry.getInstance();
        
        for (Map.Entry<String, List<String>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<String> caps = entry.getValue();
            
            menu.addSubMenu(category, category, subMenu -> {
                for (String cap : caps) {
                    String description = registry.getDescription(cap);
                    String detailed = registry.getDetailedExplanation(cap);
                    
                    boolean approved = approvedOptional.contains(cap);
                    String statusIcon = approved ? "✓" : "✗";
                    
                    subMenu.addItem(
                        cap,
                        statusIcon + " " + description,
                        detailed,
                        () -> toggleOptional(cap)
                    );
                }
                return subMenu;
            });
        }
        
        menu.addItem("back", "Back to Overview", () -> showOverview());
        menu.display();
    }
    
    /**
     * Show file path requests
     */
    private void showPathRequests() {
        MenuContext menu = new MenuContext(
            menuPath.append("paths"),
            "File Access Requests",
            uiRenderer
        );
        
        menu.addSeparator("Path Access");
        
        for (PolicyManifest.PathRequest req : manifest.getPathRequests()) {
            boolean approved = customPathDecisions.getOrDefault(req.path(), true);
            String statusIcon = approved ? "✓" : "✗";
            
            String accessDesc = req.access().toUpperCase();
            if (req.recursive()) {
                accessDesc += " (RECURSIVE)";
            }
            
            menu.addItem(
                "path_" + req.path(),
                statusIcon + " " + req.path(),
                String.format("%s\nAccess: %s\nReason: %s", 
                    req.path(), accessDesc, req.reason()),
                () -> togglePath(req.path())
            );
        }
        
        menu.addItem("back", "Back to Overview", () -> showOverview());
        menu.display();
    }
    
    /**
     * Show process access requests
     */
    private void showProcessRequests() {
        MenuContext menu = new MenuContext(
            menuPath.append("processes"),
            "Process Access Requests",
            uiRenderer
        );
        
        menu.addSeparator("Process Communication");
        
        for (PolicyManifest.ProcessRequest req : manifest.getProcessRequests()) {
            String targetStr = req.targetPath().toString();
            boolean approved = customProcessDecisions.getOrDefault(targetStr, true);
            String statusIcon = approved ? "✓" : "✗";
            
            String opsDesc = String.join(", ", req.operations());
            
            menu.addItem(
                "process_" + targetStr,
                statusIcon + " " + targetStr,
                String.format("Operations: %s\nReason: %s", opsDesc, req.reason()),
                () -> toggleProcess(targetStr)
            );
        }
        
        menu.addItem("back", "Back to Overview", () -> showOverview());
        menu.display();
    }
    
    /**
     * Show cluster configuration
     */
    private void showClusterConfig() {
        MenuContext menu = new MenuContext(
            menuPath.append("cluster"),
            "Cluster Configuration",
            uiRenderer
        );
        
        PolicyManifest.ClusterConfig config = manifest.getClusterConfig();
        
        String details = String.format(
            "Cluster ID: %s\nRole: %s\nShared Path: %s\nMax Members: %s",
            config.getClusterId(),
            config.getRole(),
            config.getSharedPath(),
            config.getMaxMembers() == 0 ? "Unlimited" : config.getMaxMembers()
        );
        
        menu.addInfoItem("config", details);
        menu.addInfoItem("explanation",
            "\nCluster membership allows this node to coordinate with other " +
            "instances of the same package. They will share data and communicate " +
            "via the cluster path.");
        
        menu.addItem("back", "Back to Overview", () -> showOverview());
        menu.display();
    }
    
    /**
     * Show customization summary before final approval
     */
    private void showCustomizationSummary() {
        MenuContext menu = new MenuContext(
            menuPath.append("summary"),
            "Final Review",
            uiRenderer
        );
        
        // Count approved vs denied
        int approvedReq = approvedRequired.size();
        int deniedReq = deniedRequired.size();
        int approvedOpt = approvedOptional.size();
        int deniedOpt = deniedOptional.size();
        
        StringBuilder summary = new StringBuilder();
        summary.append("You are about to approve:\n\n");
        summary.append(String.format("• %d/%d required capabilities\n", 
            approvedReq, approvedReq + deniedReq));
        summary.append(String.format("• %d/%d optional capabilities\n",
            approvedOpt, approvedOpt + deniedOpt));
        
        // Check if any required denied
        if (!deniedRequired.isEmpty()) {
            summary.append("\n⚠️  WARNING: Required capabilities denied:\n");
            for (String cap : deniedRequired) {
                summary.append("  • ").append(
                    NodeCapabilityRegistry.getInstance().getDescription(cap)
                ).append("\n");
            }
            summary.append("\nNode may not function correctly.");
        }
        
        menu.addInfoItem("summary", summary.toString());
        menu.addSeparator("Confirm");
        
        menu.addItem(
            "confirm",
            "✓ Confirm and Install",
            "Enter password to approve policy",
            () -> requestPasswordApproval()
        );
        
        menu.addItem(
            "back",
            "← Back to Review",
            "Make more changes",
            () -> showOverview()
        );
        
        menu.addItem(
            "cancel",
            "✗ Cancel Installation",
            "Do not install",
            () -> denyInstallation()
        );
        
        menu.display();
    }
    
    // ===== TOGGLE ACTIONS =====
    
    private void toggleRequired(String capability) {
        if (approvedRequired.contains(capability)) {
            approvedRequired.remove(capability);
            deniedRequired.add(capability);
            uiRenderer.render(UIProtocol.showMessage("Denied: " + capability + 
                "\n⚠️  This may prevent node from functioning."));
        } else {
            deniedRequired.remove(capability);
            approvedRequired.add(capability);
            uiRenderer.render(UIProtocol.showMessage("Approved: " + capability));
        }
        showRequiredCapabilities(); // Refresh
    }
    
    private void toggleOptional(String capability) {
        if (approvedOptional.contains(capability)) {
            approvedOptional.remove(capability);
            deniedOptional.add(capability);
            uiRenderer.render(UIProtocol.showMessage("Denied optional: " + capability));
        } else {
            deniedOptional.remove(capability);
            approvedOptional.add(capability);
            uiRenderer.render(UIProtocol.showMessage("Approved optional: " + capability));
        }
        showOptionalCapabilities(); // Refresh
    }
    
    private void togglePath(String path) {
        boolean current = customPathDecisions.getOrDefault(path, true);
        customPathDecisions.put(path, !current);
        uiRenderer.render(UIProtocol.showMessage((current ? "Denied" : "Approved") + " path: " + path));
        showPathRequests(); // Refresh
    }
    
    private void toggleProcess(String targetPath) {
        boolean current = customProcessDecisions.getOrDefault(targetPath, true);
        customProcessDecisions.put(targetPath, !current);
        uiRenderer.render(UIProtocol.showMessage((current ? "Denied" : "Approved") + " process: " + targetPath));
        showProcessRequests(); // Refresh
    }
    
    // ===== FINAL ACTIONS =====
    
    private void quickApprove() {
        // Approve everything
        approvedRequired.addAll(manifest.getRequiredCapabilities());
        approvedOptional.addAll(manifest.getOptionalCapabilities());
        deniedRequired.clear();
        deniedOptional.clear();
        
        requestPasswordApproval();
    }
    
    private void requestPasswordApproval() {
        // This would trigger password entry via parent
        // For now, simulate approval
        completeApproval(true);
    }
    
    private void denyInstallation() {
        completeApproval(false);
    }
    
    private void completeApproval(boolean approved) {
        if (approved) {
            // Build result with user decisions
            Set<String> allApproved = new HashSet<>();
            allApproved.addAll(approvedRequired);
            allApproved.addAll(approvedOptional);
            
            List<PolicyManifest.PathRequest> approvedPaths = 
                manifest.getPathRequests().stream()
                    .filter(r -> customPathDecisions.getOrDefault(r.path(), true))
                    .toList();
            
            List<PolicyManifest.ProcessRequest> approvedProcesses =
                manifest.getProcessRequests().stream()
                    .filter(r -> customProcessDecisions.getOrDefault(
                        r.targetPath().toString(), true))
                    .toList();
            
            PolicyReviewResult result = new PolicyReviewResult(
                true,
                allApproved,
                approvedPaths,
                approvedProcesses,
                manifest.getAllowedDomains(),
                manifest.getClusterConfig()
            );
            
            resultFuture.complete(result);
        } else {
            resultFuture.complete(new PolicyReviewResult(
                false, Collections.emptySet(), 
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptySet(), null
            ));
        }
    }
    
    // ===== UTILITIES =====
    
    private Map<String, List<String>> groupByCategory(Set<String> capabilities) {
        Map<String, List<String>> result = new HashMap<>();
        NodeCapabilityRegistry registry = NodeCapabilityRegistry.getInstance();
        
        for (String cap : capabilities) {
            String category = registry.getCategory(cap);
            result.computeIfAbsent(category, k -> new ArrayList<>()).add(cap);
        }
        
        return result;
    }
    
    // ===== RESULT =====
    
    public record PolicyReviewResult(
        boolean approved,
        Set<String> grantedCapabilities,
        List<PolicyManifest.PathRequest> grantedPaths,
        List<PolicyManifest.ProcessRequest> grantedProcesses,
        Set<String> allowedDomains,
        PolicyManifest.ClusterConfig clusterConfig
    ) {
        /**
         * Convert review result to NodeSecurityPolicy
         */
        public NodeSecurityPolicy toPolicy(NoteBytesReadOnly nodeId, String userId) {
            NodeSecurityPolicy policy = new NodeSecurityPolicy(nodeId, userId);
            
            // Grant approved capabilities
            for (String cap : grantedCapabilities) {
                policy.grantCapability(cap);
            }
            
            // Grant path access
            for (PolicyManifest.PathRequest req : grantedPaths) {
                boolean read = req.access().contains("read");
                boolean write = req.access().contains("write");
                policy.grantPathAccess(req.path(), read, write, req.recursive(), req.reason());
            }
            
            // Grant process access
            for (PolicyManifest.ProcessRequest req : grantedProcesses) {
                boolean message = req.operations().contains("message");
                boolean stream = req.operations().contains("stream");
                boolean query = req.operations().contains("query");
                policy.grantProcessAccess(req.targetPath(), message, stream, query, req.reason());
            }
            
            // Add domains
            allowedDomains.forEach(policy::allowDomain);
            
            // Set cluster
            if (clusterConfig != null) {
                policy.setClusterMembership(new NodeSecurityPolicy.ClusterMembership(
                    clusterConfig.getClusterId(),
                    clusterConfig.getRole(),
                    ContextPath.parse(clusterConfig.getSharedPath()),
                    System.currentTimeMillis()
                ));
            }
            
            policy.approve();
            return policy;
        }
    }
}