package io.netnotes.engine.core.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.MenuNavigator;
import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.TerminalInputReader;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.PackageInfo;
import io.netnotes.engine.core.system.control.nodes.PackageManifest;
import io.netnotes.engine.core.system.control.nodes.ProcessConfig;
import io.netnotes.engine.core.system.control.nodes.security.PolicyManifest;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.utils.TimeHelpers;

/**
 * BrowsePackagesScreen - Browse and install packages from repositories
 * 
 * Features:
 * - Update package cache from repositories
 * - Browse packages by category
 * - View package details
 * - Install packages (with password confirmation)
 * - Configure installation (namespace, autoload, etc.)
 * 
 * Uses RuntimeAccess to interact with system services
 */
class BrowsePackagesScreen extends TerminalScreen {
    
    private enum View {
        LOADING,
        CATEGORY_LIST,
        PACKAGE_LIST,
        PACKAGE_DETAILS,
        CONFIGURE_INSTALL,
        CONFIRM_INSTALL
    }
    
    
    private View currentView = View.LOADING;
    private List<PackageInfo> availablePackages;
    private List<InstalledPackage> installedPackages;
    private String selectedCategory;
    private PackageInfo selectedPackage;
    private ProcessConfig installConfig;
    private boolean loadImmediately = false;
    private Runnable onBack;
    
    private MenuNavigator menuNavigator;
    private PasswordReader passwordReader;
    private TerminalInputReader inputReader;
    private final NodeCommands nodeCommands;
    
    public BrowsePackagesScreen(
        String name, 
        SystemTerminalContainer terminal, 
        InputDevice keyboard,
        NodeCommands nodeCommands
    ) {
        super(name, terminal, keyboard);
        this.availablePackages = new ArrayList<>();
        this.installedPackages = new ArrayList<>();
        this.nodeCommands = nodeCommands;
    }
    
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentView = View.LOADING;
        selectedCategory = null;
        selectedPackage = null;
        installConfig = null;
        loadImmediately = false;
        
        return render().thenRun(this::updatePackageCache);
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    @Override
    public CompletableFuture<Void> render() {
        switch (currentView) {
            case LOADING:
                return renderLoading();
            case CATEGORY_LIST:
                return renderCategoryList();
            case PACKAGE_LIST:
                return renderPackageList();
            case PACKAGE_DETAILS:
                return renderPackageDetails();
            case CONFIGURE_INSTALL:
                return renderConfigureInstall();
            case CONFIRM_INSTALL:
                return renderConfirmInstall();
            default:
                return CompletableFuture.completedFuture(null);
        }
    }
    
    // ===== LOADING =====
    
    private CompletableFuture<Void> renderLoading() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Browse Packages"))
            .thenCompose(v -> terminal.printAt(7, 10, "Updating package lists from repositories..."))
            .thenCompose(v -> terminal.printAt(9, 10, "This may take a moment..."));
    }
    
   private void updatePackageCache() {
        CompletableFuture<List<PackageInfo>> availableFuture = 
            nodeCommands.browseAvailablePackages();
        CompletableFuture<List<InstalledPackage>> installedFuture = 
            nodeCommands.getInstalledPackages();
        
        CompletableFuture.allOf(availableFuture, installedFuture)
            .thenAccept(v -> {
                availablePackages = availableFuture.join();
                installedPackages = installedFuture.join();
                currentView = View.CATEGORY_LIST;
                render();
            })
            .exceptionally(ex -> {
                terminal.printError("Failed to update packages: " + ex.getMessage())
                    .thenCompose(v -> terminal.printAt(11, 10, "Press any key to go back..."))
                    .thenRun(() -> waitForKeyPress(keyboard, this::goBack));
                return null;
            });
    }
    
    // ===== CATEGORY LIST =====
    
    private CompletableFuture<Void> renderCategoryList() {
        if (availablePackages.isEmpty()) {
            return terminal.clear()
                .thenCompose(v -> terminal.printTitle("Browse Packages"))
                .thenCompose(v -> terminal.printAt(5, 10, "No packages available"))
                .thenCompose(v -> terminal.printAt(7, 10, "Check repository configuration"))
                .thenCompose(v -> terminal.printAt(9, 10, "Press ESC to go back"))
                .thenRun(() -> waitForKeyPress(keyboard, this::goBack));
        }
        
        // Group by category
        Map<String, List<PackageInfo>> byCategory = availablePackages.stream()
            .collect(Collectors.groupingBy(PackageInfo::getCategory));
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Browse Packages"))
            .thenCompose(v -> terminal.printAt(5, 10, "Found " + availablePackages.size() + " packages"))
            .thenCompose(v -> terminal.printAt(7, 10, "Select a category:"))
            .thenRun(() -> showCategoryMenu(byCategory));
    }
    
    private void showCategoryMenu(Map<String, List<PackageInfo>> categories) {
        ContextPath menuPath = terminal.getSessionPath().append("menu", "categories");
        MenuContext menu = new MenuContext(menuPath, "Package Categories");
        
        // Add category items
        for (Map.Entry<String, List<PackageInfo>> entry : categories.entrySet()) {
            String category = entry.getKey();
            int count = entry.getValue().size();
            String description = category + " (" + count + " package" + (count != 1 ? "s" : "") + ")";
            
            menu.addItem(category, description, "Browse " + category + " packages",
                () -> showCategory(category));
        }
        
        menu.addSeparator("Package-navigation");
        menu.addItem("all", "All Packages", "View all packages", () -> showCategory(null));
        menu.addItem("refresh", "Refresh Package List", "Update from repositories", this::onShow);
        menu.addItem("back", "Back to Node Manager", this::goBack);
        
        menuNavigator = new MenuNavigator(terminal, keyboard);
        menuNavigator.showMenu(menu);
    }
    
    // ===== PACKAGE LIST =====
    
    private void showCategory(String category) {
        cleanupMenuNavigator();
        selectedCategory = category;
        currentView = View.PACKAGE_LIST;
        render();
    }
    
    private CompletableFuture<Void> renderPackageList() {
        List<PackageInfo> packages;
        
        if (selectedCategory == null) {
            packages = availablePackages;
        } else {
            packages = availablePackages.stream()
                .filter(p -> p.getCategory().equals(selectedCategory))
                .collect(Collectors.toList());
        }
        
        String title = selectedCategory != null ? 
            "Category: " + selectedCategory : "All Packages";
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle(title))
            .thenCompose(v -> renderPackageTable(packages, 5))
            .thenRun(() -> showPackageListMenu(packages));
    }
    
    private CompletableFuture<Void> renderPackageTable(List<PackageInfo> packages, int startRow) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        
        // Header
        future = future
            .thenCompose(v -> terminal.printAt(startRow, 10, "Package"))
            .thenCompose(v -> terminal.printAt(startRow, 35, "Version"))
            .thenCompose(v -> terminal.printAt(startRow, 45, "Repository"))
            .thenCompose(v -> terminal.printAt(startRow + 1, 10, "─".repeat(60)));
        
        int row = startRow + 2;
        int index = 1;
        
        for (PackageInfo pkg : packages) {
            final int currentRow = row;
            final int currentIndex = index;
            
            // Check if already installed
            boolean installed = isPackageInstalled(pkg.getPackageId());
            String marker = installed ? "[INSTALLED] " : "";
            
            future = future
                .thenCompose(v -> terminal.printAt(currentRow, 8, currentIndex + "."))
                .thenCompose(v -> terminal.printAt(currentRow, 10, 
                    marker + truncate(pkg.getName(), 20)))
                .thenCompose(v -> terminal.printAt(currentRow, 35, pkg.getVersion()))
                .thenCompose(v -> terminal.printAt(currentRow, 45, 
                    truncate(pkg.getRepository(), 24)));
            
            row++;
            index++;
        }
        
        return future;
    }
    
    private void showPackageListMenu(List<PackageInfo> packages) {
        ContextPath menuPath = terminal.getSessionPath().append("menu", "packages");
        MenuContext menu = new MenuContext(menuPath, "Package Selection");
        
        // Add menu item for each package
        int index = 1;
        for (PackageInfo pkg : packages) {
            final PackageInfo p = pkg;
            String itemId = "package_" + index;
            String description = pkg.getName() + " v" + pkg.getVersion();
            String help = truncate(pkg.getDescription(), 50);
            
            menu.addItem(itemId, description, help, () -> showPackageDetails(p));
            index++;
        }
        
        menu.addSeparator("Category-navigation");
        menu.addItem("back", "Back to Categories", () -> {
            cleanupMenuNavigator();
            selectedCategory = null;
            currentView = View.CATEGORY_LIST;
            render();
        });
        
        menuNavigator = new MenuNavigator(terminal, keyboard);
        menuNavigator.showMenu(menu);
    }
    
    // ===== PACKAGE DETAILS =====
    
    private void showPackageDetails(PackageInfo pkg) {
        cleanupMenuNavigator();
        selectedPackage = pkg;
        currentView = View.PACKAGE_DETAILS;
        render();
    }
    
    private CompletableFuture<Void> renderPackageDetails() {
        if (selectedPackage == null) {
            currentView = View.CATEGORY_LIST;
            return render();
        }
        
        boolean isInstalled = isPackageInstalled(selectedPackage.getPackageId());
        PackageManifest manifest = selectedPackage.getManifest();
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Package Details"))
            .thenCompose(v -> terminal.printAt(5, 10, "Name: " + selectedPackage.getName()))
            .thenCompose(v -> terminal.printAt(6, 10, "Version: " + selectedPackage.getVersion()))
            .thenCompose(v -> terminal.printAt(7, 10, "Category: " + selectedPackage.getCategory()))
            .thenCompose(v -> terminal.printAt(8, 10, "Repository: " + selectedPackage.getRepository()))
            .thenCompose(v -> terminal.printAt(9, 10, "Size: " + formatSize(selectedPackage.getSize())))
            .thenCompose(v -> terminal.printAt(11, 10, "Description:"))
            .thenCompose(v -> terminal.printAt(12, 12, wrapText(selectedPackage.getDescription(), 60)))
            .thenCompose(v -> terminal.printAt(14, 10, "Type: " + manifest.getType()))
            .thenCompose(v -> terminal.printAt(15, 10, "Entry: " + manifest.getEntry()))
            .thenCompose(v -> {
                if (!manifest.getDependencies().isEmpty()) {
                    return terminal.printAt(16, 10, "Dependencies: " + 
                        String.join(", ", manifest.getDependencies()));
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenCompose(v -> {
                if (isInstalled) {
                    return terminal.printAt(18, 10, "[ALREADY INSTALLED]");
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenCompose(v -> terminal.printAt(20, 10, "Choose an action:"))
            .thenRun(() -> showPackageDetailsMenu(isInstalled));
    }
    
    private void showPackageDetailsMenu(boolean isInstalled) {
        ContextPath menuPath = terminal.getSessionPath().append("menu", "package-details");
        MenuContext menu = new MenuContext(menuPath, "Package Actions");
        
        if (!isInstalled) {
            menu.addItem("install", "Install Package", 
                "Install this package (requires password)",
                this::startInstallFlow);
        }
        
        menu.addSeparator("Package-details-navigation");
        menu.addItem("back", "Back to Package List", () -> {
            cleanupMenuNavigator();
            selectedPackage = null;
            currentView = View.PACKAGE_LIST;
            render();
        });
        
        menuNavigator = new MenuNavigator(terminal, keyboard);
        menuNavigator.showMenu(menu);
    }
    
    // ===== CONFIGURE INSTALL =====
    
    private void startInstallFlow() {
        cleanupMenuNavigator();
        
        PackageManifest manifest = selectedPackage.getManifest();
        
        // If package requires specific namespace, skip configuration
        if (manifest.requiresSpecificNamespace()) {
            installConfig = ProcessConfig.create(manifest.getNamespace());
            loadImmediately = manifest.isAutoload();
            currentView = View.CONFIRM_INSTALL;
            render();
        } else {
            // Allow user to configure
            currentView = View.CONFIGURE_INSTALL;
            render();
        }
    }
    
    private CompletableFuture<Void> renderConfigureInstall() {
        if (selectedPackage == null) {
            currentView = View.CATEGORY_LIST;
            return render();
        }
        
        PackageManifest manifest = selectedPackage.getManifest();
        NoteBytesReadOnly defaultNamespace = manifest.getNamespace() != null ? 
            manifest.getNamespace() : selectedPackage.getPackageId();
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Configure Installation"))
            .thenCompose(v -> terminal.printAt(5, 10, "Package: " + selectedPackage.getName()))
            .thenCompose(v -> terminal.printAt(7, 10, "Process Namespace:"))
            .thenCompose(v -> terminal.printAt(8, 12, "Default: " + defaultNamespace))
            .thenCompose(v -> terminal.printAt(9, 12, "Leave blank to use default"))
            .thenCompose(v -> terminal.printAt(11, 10, "Custom namespace (or press Enter):"))
            .thenCompose(v -> terminal.moveCursor(11, 46))
            .thenRun(this::readCustomNamespace);
    }
    
    private void readCustomNamespace() {
        inputReader = new TerminalInputReader(terminal, 11, 46, 20);
        
        inputReader.setOnComplete(input -> {
            keyboard.setEventConsumer(null);
            inputReader.close();
            inputReader = null;
            
            // Determine namespace
            NoteBytesReadOnly namespace;
            if (input == null || input.trim().isEmpty()) {
                PackageManifest manifest = selectedPackage.getManifest();
                namespace = manifest.getNamespace() != null ? 
                    manifest.getNamespace() : selectedPackage.getPackageId();
            } else {
                namespace = new NoteBytesReadOnly(input.trim());
            }
            
            installConfig = ProcessConfig.create(namespace);
            
            // Ask about autoload
            askAutoload();
        });
        
        keyboard.setEventConsumer(inputReader.getEventConsumer());
    }
    
    private void askAutoload() {
        terminal.printAt(13, 10, "Load immediately after installation? (y/N):")
            .thenCompose(v -> terminal.moveCursor(13, 54))
            .thenRun(this::readAutoloadChoice);
    }
    
    private void readAutoloadChoice() {
        inputReader = new TerminalInputReader(terminal, 13, 54, 1);
        
        inputReader.setOnComplete(input -> {
            keyboard.setEventConsumer(null);
            inputReader.close();
            inputReader = null;
            
            loadImmediately = "y".equalsIgnoreCase(input) || "yes".equalsIgnoreCase(input);
            
            currentView = View.CONFIRM_INSTALL;
            render();
        });
        
        keyboard.setEventConsumer(inputReader.getEventConsumer());
    }
    
    // ===== CONFIRM INSTALL =====
    
    private CompletableFuture<Void> renderConfirmInstall() {
        if (selectedPackage == null || installConfig == null) {
            currentView = View.CATEGORY_LIST;
            return render();
        }
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Confirm Installation"))
            .thenCompose(v -> terminal.printAt(5, 10, "Package: " + selectedPackage.getName() + 
                " v" + selectedPackage.getVersion()))
            .thenCompose(v -> terminal.printAt(6, 10, "Repository: " + selectedPackage.getRepository()))
            .thenCompose(v -> terminal.printAt(7, 10, "Size: " + formatSize(selectedPackage.getSize())))
            .thenCompose(v -> terminal.printAt(9, 10, "Installation Configuration:"))
            .thenCompose(v -> terminal.printAt(10, 12, "• Process Namespace: " + 
                installConfig.getProcessId()))
            .thenCompose(v -> terminal.printAt(11, 12, "• Load Immediately: " + 
                (loadImmediately ? "Yes" : "No")))
            .thenCompose(v -> terminal.printAt(13, 10, "This will download and install the package."))
            .thenCompose(v -> terminal.printAt(15, 10, "Enter password to confirm:"))
            .thenCompose(v -> terminal.moveCursor(15, 36))
            .thenRun(this::verifyPasswordAndInstall);
    }
    
  
    private void verifyPasswordAndInstall() {
        terminal.printAt(17, 10, "Type 'INSTALL' to confirm:")
            .thenCompose(v -> terminal.moveCursor(17, 38))
            .thenRun(this::startInstallConfirmation);
    }

    private void startInstallConfirmation() {
        inputReader = new TerminalInputReader(terminal, 17, 38, 20);
        keyboard.setEventConsumer(inputReader.getEventConsumer());
        
        inputReader.setOnComplete(input -> {
            keyboard.setEventConsumer(null);
            inputReader.close();
            inputReader = null;
            
            if ("INSTALL".equals(input)) {
                performInstallation();
            } else {
                terminal.printError("Installation cancelled")
                    .thenCompose(x -> terminal.printAt(19, 10, "Press any key..."))
                    .thenRun(() -> waitForKeyPress(keyboard, () -> {
                        currentView = View.CONFIRM_INSTALL;
                        render();
                    }));
            }
        });
        
        inputReader.setOnEscape(text -> {
            keyboard.setEventConsumer(null);
            inputReader.close();
            inputReader = null;
            selectedPackage = null;
            installConfig = null;
            currentView = View.CATEGORY_LIST;
            render();
        });
    }
    
    private CompletableFuture<Void> performInstallation() {
        PolicyManifest policy = PolicyManifest.fromNoteBytes(
            selectedPackage.getManifest().getMetadata()
        );
        
        return terminal.printAt(17, 10, "Installing package...                  ")
            .thenCompose(v -> nodeCommands.installPackage(
                selectedPackage,
                installConfig,
                policy,
                loadImmediately
            ))
            .thenCompose(installedPkg -> {
                if (loadImmediately) {
                    return nodeCommands.loadNode(installedPkg.getPackageId().getId())
                        .thenCompose(instance -> 
                            terminal.printSuccess("Package installed and loaded successfully!")
                                .thenCompose(x -> terminal.printAt(19, 10, "Instance running at: " + 
                                    instance.getProcessId()))
                        );
                } else {
                    return terminal.printSuccess("Package installed successfully!")
                        .thenCompose(x -> terminal.printAt(19, 10, 
                            "Use 'Running Instances' to load the package"));
                }
            })
            .thenCompose(v -> terminal.printAt(21, 10, "Returning to package list..."))
            .thenRunAsync(() -> TimeHelpers.timeDelay(3))
            .thenRun(() -> {
                selectedPackage = null;
                installConfig = null;
                currentView = View.PACKAGE_LIST;
                render();
            })
            .exceptionally(ex -> {
                terminal.printError("Installation failed: " + ex.getMessage())
                    .thenCompose(x -> terminal.printAt(21, 10, "Press any key..."))
                    .thenRun(() -> waitForKeyPress(keyboard, () -> {
                        selectedPackage = null;
                        installConfig = null;
                        currentView = View.CATEGORY_LIST;
                        render();
                    }));
                return null;
            });
    }
    
    // ===== UTILITY =====
    
    private boolean isPackageInstalled(NoteBytesReadOnly packageId) {
        return installedPackages.stream()
            .anyMatch(pkg -> pkg.getPackageId().getId().equals(packageId));
    }
    
    private void goBack() {
        cleanup();
        if (onBack != null) {
            onBack.run();
        }
    }
    
    private void cleanupMenuNavigator() {
        if (menuNavigator != null) {
            menuNavigator.cleanup();
            menuNavigator = null;
        }
    }
    
    private void cleanup() {
        cleanupMenuNavigator();
        
        if (passwordReader != null) {
            keyboard.setEventConsumer(null);
            passwordReader.close();
            passwordReader = null;
        }
        
        if (inputReader != null) {
            keyboard.setEventConsumer(null);
            inputReader.close();
            inputReader = null;
        }
        
        keyboard.setEventConsumer(null);
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    private String wrapText(String text, int maxWidth) {
        if (text == null || text.length() <= maxWidth) return text;
        
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        
        for (String word : words) {
            if (line.length() + word.length() + 1 > maxWidth) {
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder();
                }
            }
            if (line.length() > 0) line.append(" ");
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        
        return String.join("\n" + " ".repeat(12), lines);
    }
}