package io.netnotes.engine.core.system;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.MenuNavigatorProcess;
import io.netnotes.engine.core.system.control.nodes.*;
import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;


import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.utils.TimeHelpers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * NodeManagerMenus - Static menu builders for node/package management
 * 
 * Separated from SystemSessionProcess for clarity.
 * All methods are static and take dependencies as parameters.
 */
public class NodeManagerMenus {
    
    /**
     * Show node manager main menu
     */
    public static void showNodeManagerMenu(
        SystemSessionProcess systemSessionProcess,
        ContextPath basePath,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        MenuNavigatorProcess menuNavigator,
        MenuContext parentMenu,
        Runnable onBack
    ) {
        // Load statistics
        systemAccess.getInstalledPackages()
            .thenCombine(systemAccess.getRunningInstances(), 
                (installed, running) -> {
                    
                    MenuContext menu = new MenuContext(
                        basePath.append("menu", "nodes"),
                        "Node Manager",
                        uiRenderer,
                        parentMenu
                    );
                    
                    menu.addInfoItem("stats", String.format(
                        "Installed Packages: %d | Running Instances: %d",
                        installed.size(),
                        running.size()
                    ));
                    
                    menu.addSeparator("Package Management");
                    
                    menu.addItem("browse",
                        "📦 Browse & Install Packages",
                        "View and install from repositories",
                        () -> showBrowsePackagesMenu(
                            systemSessionProcess,
                            basePath, systemAccess, uiRenderer, menuNavigator, 
                            menu, () -> showNodeManagerMenu( systemSessionProcess, basePath, systemAccess, 
                                uiRenderer, menuNavigator, parentMenu, onBack)
                        ));
                    
                    menu.addItem("installed",
                        "📋 Manage Installed Packages",
                        "View, configure, or remove packages",
                        () -> showInstalledPackagesMenu(
                            basePath, systemAccess, uiRenderer, menuNavigator,
                            menu, () -> showNodeManagerMenu(systemSessionProcess, basePath, systemAccess,
                                uiRenderer, menuNavigator, parentMenu, onBack)
                        ));
                    
                    menu.addSeparator("Instance Management");
                    
                    menu.addItem("running",
                        "📊 Running Instances",
                        "View and manage active nodes",
                        () -> showRunningInstancesMenu(
                            basePath, systemAccess, uiRenderer, menuNavigator,
                            menu, () -> showNodeManagerMenu(systemSessionProcess, basePath, systemAccess,
                                uiRenderer, menuNavigator, parentMenu, onBack)
                        ));
                    
                    menu.addSeparator("");
                    
                    menu.addItem("back", "← Back to Main Menu", onBack);
                    
                    menuNavigator.showMenu(menu);
                    return null;
                })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to load node statistics: " + ex.getMessage()));
                onBack.run();
                return null;
            });
    }
    
    /**
     * Browse available packages
     */
    public static void showBrowsePackagesMenu(
        SystemSessionProcess systemSessionProcess,
        ContextPath basePath,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        MenuNavigatorProcess menuNavigator,
        MenuContext parentMenu,
        Runnable onBack
    ) {
        uiRenderer.render(UIProtocol.showMessage("Loading available packages..."));

        systemAccess.browseAvailablePackages()
            .thenAccept(packages -> {
            
                if (packages.isEmpty()) {
                    uiRenderer.render(UIProtocol.showMessage(
                        "No packages available.\n\n" +
                        "Check repository configuration."));
                    onBack.run();
                    return;
                }
                
                MenuContext menu = new MenuContext(
                    basePath.append("menu", "browse-packages"),
                    "Available Packages",
                    uiRenderer,
                    parentMenu
                );
                
                menu.addInfoItem("info", packages.size() + " package(s) available");
                menu.addSeparator("Packages");
                
                // Group by category
                Map<String, List<PackageInfo>> byCategory = packages.stream()
                    .collect(Collectors.groupingBy(PackageInfo::getCategory));
                
                for (Map.Entry<String, List<PackageInfo>> entry : byCategory.entrySet()) {
                    String category = entry.getKey();
                    List<PackageInfo> pkgs = entry.getValue();
                    
                    menu.addSubMenu(category, category, subMenu -> {
                        for (PackageInfo pkg : pkgs) {
                            String displayName = String.format("%s (%s)",
                                pkg.getName(), pkg.getVersion());
                            
                            subMenu.addItem(
                                pkg.getPackageId().toString(),
                                displayName,
                                pkg.getDescription(),
                                () -> startPackageInstallation(
                                    systemSessionProcess,
                                    basePath, pkg, systemAccess, uiRenderer, 
                                    () -> showBrowsePackagesMenu(systemSessionProcess, basePath, systemAccess,
                                        uiRenderer, menuNavigator, parentMenu, onBack)
                                )
                            );
                        }
                        return subMenu;
                    });
                }
                
                menu.addItem("back", "← Back", onBack);
                
                menuNavigator.showMenu(menu);
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to load packages: " + ex.getMessage()));
                onBack.run();
                return null;
            });
    }
    
    /**
     * Start package installation with password confirmation
     */
    private static void startPackageInstallation(
        SystemSessionProcess systemSessionProcess,
        ContextPath basePath,
        PackageInfo pkg,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        Runnable onComplete
    ) {
        uiRenderer.render(UIProtocol.showMessage(
            "Starting installation: " + pkg.getName()));
        
        // Password supplier for installation flow
        Supplier<CompletableFuture<NoteBytesEphemeral>> passwordSupplier = 
            () -> PasswordMenus.requestPasswordForInstallation(
                systemSessionProcess,
                systemAccess, 
                uiRenderer
            );
        
        // Create installation flow coordinator
        InstallationFlowCoordinator flowCoordinator = new InstallationFlowCoordinator(
            uiRenderer,
            basePath.append("install"),
            systemAccess,
            passwordSupplier
        );
        
        // Run installation flow
        flowCoordinator.startInstallation(pkg)
            .thenCompose(request -> {
                if (request == null) {
                    // User cancelled
                    uiRenderer.render(UIProtocol.showMessage("Installation cancelled"));
                    return CompletableFuture.completedFuture(null);
                }
                
                // Execute installation via systemAccess
                return systemAccess.installPackage(request)
                    .thenCompose(installedPkg -> {
                        uiRenderer.render(UIProtocol.showMessage(
                            "✓ Package installed: " + pkg.getName()));
                        
                        // Load immediately if requested
                        if (request.shouldLoadImmediately()) {
                            NodeLoadRequest loadRequest = new NodeLoadRequest(installedPkg);
                            
                            return systemAccess.loadNode(loadRequest)
                                .thenApply(instance -> {
                                    uiRenderer.render(UIProtocol.showMessage(
                                        "✓ Node loaded: " + instance.getInstanceId()));
                                    return installedPkg;
                                });
                        }
                        
                        return CompletableFuture.completedFuture(installedPkg);
                    });
            })
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    uiRenderer.render(UIProtocol.showError(
                        "Installation failed: " + ex.getMessage()));
                }
                onComplete.run();
            });
    }
    
    /**
     * Show installed packages
     */
    public static void showInstalledPackagesMenu(
        ContextPath basePath,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        MenuNavigatorProcess menuNavigator,
        MenuContext parentMenu,
        Runnable onBack
    ) {
        systemAccess.getInstalledPackages()
            .thenAccept(installed -> {
                if (installed.isEmpty()) {
                    uiRenderer.render(UIProtocol.showMessage(
                        "No packages installed"));
                    onBack.run();
                    return;
                }
                
                MenuContext menu = new MenuContext(
                    basePath.append("menu", "installed-packages"),
                    "Installed Packages",
                    uiRenderer,
                    parentMenu
                );
                
                menu.addInfoItem("info", installed.size() + " package(s) installed");
                menu.addSeparator("Packages");
                
                for (InstalledPackage pkg : installed) {
                    String displayName = String.format("%s %s",
                        pkg.getName(), pkg.getVersion());
                    
                    menu.addItem(
                        pkg.getPackageId().toString(),
                        displayName,
                        pkg.getDescription(),
                        () -> showPackageDetailsMenu(
                            basePath, pkg, systemAccess, uiRenderer, menuNavigator,
                            menu, () -> showInstalledPackagesMenu(basePath, systemAccess,
                                uiRenderer, menuNavigator, parentMenu, onBack)
                        )
                    );
                }
                
                menu.addItem("back", "← Back", onBack);
                
                menuNavigator.showMenu(menu);
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to load installed packages: " + ex.getMessage()));
                onBack.run();
                return null;
            });
    }
    
    /**
     * Show package details with actions
     */
    public static void showPackageDetailsMenu(
        ContextPath basePath,
        InstalledPackage pkg,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        MenuNavigatorProcess menuNavigator,
        MenuContext parentMenu,
        Runnable onBack
    ) {
        MenuContext menu = new MenuContext(
            basePath.append("menu", "package-details"),
            pkg.getName(),
            uiRenderer,
            parentMenu
        );
        
        String details = String.format(
            "Name: %s\n" +
            "Version: %s\n" +
            "Process ID: %s\n" +
            "Repository: %s\n" +
            "Installed: %s\n\n" +
            "%s",
            pkg.getName(),
            pkg.getVersion(),
            pkg.getProcessId(),
            pkg.getRepository(),
            TimeHelpers.formatDate(pkg.getInstalledDate()),
            pkg.getDescription()
        );
        
        menu.addInfoItem("details", details);
        menu.addSeparator("Actions");
        
        menu.addItem("load",
            "▶️ Load Instance",
            "Start a node instance",
            () -> loadNodeInstance(basePath, pkg, systemAccess, uiRenderer,
                menuNavigator, menu, onBack));
        
        menu.addItem("uninstall",
            "🗑️ Uninstall",
            "Remove this package",
            () -> confirmPackageUninstall(basePath, pkg, systemAccess, uiRenderer,
                menuNavigator, menu, onBack));
        
        menu.addItem("back", "← Back", onBack);
        
        menuNavigator.showMenu(menu);
    }
    
    /**
     * Load a node instance from installed package
     */
    private static void loadNodeInstance(
        ContextPath basePath,
        InstalledPackage pkg,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        MenuNavigatorProcess menuNavigator,
        MenuContext parentMenu,
        Runnable onBack
    ) {
        uiRenderer.render(UIProtocol.showMessage(
            "Loading node: " + pkg.getName()));
        
        NodeLoadRequest loadRequest = new NodeLoadRequest(pkg);
        
        systemAccess.loadNode(loadRequest)
            .thenAccept(instance -> {
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Node loaded successfully\n\n" +
                    "Instance ID: " + instance.getInstanceId()));
                showPackageDetailsMenu(basePath, pkg, systemAccess, uiRenderer,
                    menuNavigator, parentMenu, onBack);
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to load node: " + ex.getMessage()));
                showPackageDetailsMenu(basePath, pkg, systemAccess, uiRenderer,
                    menuNavigator, parentMenu, onBack);
                return null;
            });
    }
    
    /**
     * Confirm package uninstall
     */
    private static void confirmPackageUninstall(
        ContextPath basePath,
        InstalledPackage pkg,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        MenuNavigatorProcess menuNavigator,
        MenuContext parentMenu,
        Runnable onBack
    ) {
        MenuContext menu = new MenuContext(
            basePath.append("menu", "confirm-uninstall"),
            "Confirm Uninstall",
            uiRenderer,
            parentMenu
        );
        
        menu.addInfoItem("warning",
            "⚠️ Uninstall " + pkg.getName() + "?\n\n" +
            "This will:\n" +
            "  • Stop any running instances\n" +
            "  • Delete package files\n" +
            "  • Remove from installed list\n\n" +
            "This action cannot be undone.");
        
        menu.addSeparator("Confirmation");
        
        menu.addItem("confirm",
            "✓ Yes, Uninstall",
            () -> uninstallPackage(pkg, systemAccess, uiRenderer, onBack));
        
        menu.addItem("cancel",
            "✗ Cancel",
            () -> showPackageDetailsMenu(basePath, pkg, systemAccess, uiRenderer,
                menuNavigator, parentMenu, onBack));
        
        menuNavigator.showMenu(menu);
    }
    
    /**
     * Uninstall a package
     */
    private static void uninstallPackage(
        InstalledPackage pkg,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        Runnable onComplete
    ) {
        uiRenderer.render(UIProtocol.showMessage(
            "Uninstalling " + pkg.getName() + "..."));
        
        systemAccess.uninstallPackage(pkg.getPackageId())
            .thenRun(() -> {
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Package uninstalled: " + pkg.getName()));
                onComplete.run();
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Uninstall failed: " + ex.getMessage()));
                onComplete.run();
                return null;
            });
    }
    
    /**
     * Show running instances
     */
    public static void showRunningInstancesMenu(
        ContextPath basePath,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        MenuNavigatorProcess menuNavigator,
        MenuContext parentMenu,
        Runnable onBack
    ) {
        systemAccess.getRunningInstances()
            .thenAccept(instances -> {
                if (instances.isEmpty()) {
                    uiRenderer.render(UIProtocol.showMessage(
                        "No nodes currently running"));
                    onBack.run();
                    return;
                }
                
                MenuContext menu = new MenuContext(
                    basePath.append("menu", "running-instances"),
                    "Running Node Instances",
                    uiRenderer,
                    parentMenu
                );
                
                menu.addInfoItem("info", instances.size() + " instance(s) running");
                menu.addSeparator("Instances");
                
                for (NodeInstance instance : instances) {
                    String displayName = String.format("%s [%s]",
                        instance.getPackage().getName(),
                        instance.getInstanceId());
                    
                    String status = String.format(
                        "State: %s | Uptime: %ds",
                        instance.getState(),
                        instance.getUptime() / 1000
                    );
                    
                    menu.addItem(
                        instance.getInstanceId().toString(),
                        displayName,
                        status,
                        () -> showInstanceDetailsMenu(
                            basePath, instance, systemAccess, uiRenderer, menuNavigator,
                            menu, () -> showRunningInstancesMenu(basePath, systemAccess,
                                uiRenderer, menuNavigator, parentMenu, onBack)
                        )
                    );
                }
                
                menu.addItem("back", "← Back", onBack);
                
                menuNavigator.showMenu(menu);
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to load instances: " + ex.getMessage()));
                onBack.run();
                return null;
            });
    }
    
    /**
     * Show instance details
     */
    public static void showInstanceDetailsMenu(
        ContextPath basePath,
        NodeInstance instance,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        MenuNavigatorProcess menuNavigator,
        MenuContext parentMenu,
        Runnable onBack
    ) {
        MenuContext menu = new MenuContext(
            basePath.append("menu", "instance-details"),
            "Instance: " + instance.getInstanceId(),
            uiRenderer,
            parentMenu
        );
        
        String details = String.format(
            "Package: %s %s\n" +
            "Instance ID: %s\n" +
            "Process ID: %s\n" +
            "State: %s\n" +
            "Uptime: %ds\n",
            instance.getPackage().getName(),
            instance.getPackage().getVersion(),
            instance.getInstanceId(),
            instance.getProcessId(),
            instance.getState(),
            instance.getUptime() / 1000
        );
        
        menu.addInfoItem("details", details);
        menu.addSeparator("Actions");
        
        menu.addItem("unload",
            "⏹️ Unload Instance",
            "Stop this node instance",
            () -> confirmInstanceUnload(basePath, instance, systemAccess, uiRenderer,
                menuNavigator, menu, onBack));
        
        menu.addItem("back", "← Back", onBack);
        
        menuNavigator.showMenu(menu);
    }
    
    /**
     * Confirm instance unload
     */
    private static void confirmInstanceUnload(
        ContextPath basePath,
        NodeInstance instance,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        MenuNavigatorProcess menuNavigator,
        MenuContext parentMenu,
        Runnable onBack
    ) {
        MenuContext menu = new MenuContext(
            basePath.append("menu", "confirm-unload"),
            "Confirm Unload",
            uiRenderer,
            parentMenu
        );
        
        menu.addInfoItem("warning",
            "⚠️ Unload instance?\n\n" +
            "Instance: " + instance.getInstanceId() + "\n" +
            "Package: " + instance.getPackage().getName() + "\n\n" +
            "This will stop the node process.");
        
        menu.addSeparator("Confirmation");
        
        menu.addItem("confirm",
            "✓ Yes, Unload",
            () -> unloadInstance(instance, systemAccess, uiRenderer,
                () -> showInstanceDetailsMenu(basePath, instance, systemAccess,
                    uiRenderer, menuNavigator, parentMenu, onBack)));
        
        menu.addItem("cancel",
            "✗ Cancel",
            () -> showInstanceDetailsMenu(basePath, instance, systemAccess,
                uiRenderer, menuNavigator, parentMenu, onBack));
        
        menuNavigator.showMenu(menu);
    }
    
    /**
     * Unload an instance
     */
    private static void unloadInstance(
        NodeInstance instance,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        Runnable onComplete
    ) {
        uiRenderer.render(UIProtocol.showMessage("Unloading instance..."));
        
        systemAccess.unloadNode(instance.getInstanceId())
            .thenRun(() -> {
                uiRenderer.render(UIProtocol.showMessage("✓ Instance unloaded"));
                onComplete.run();
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Unload failed: " + ex.getMessage()));
                onComplete.run();
                return null;
            });
    }
}