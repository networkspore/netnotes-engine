package io.netnotes.engine.core.system;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.NodeInstance;
import io.netnotes.engine.core.system.control.nodes.NodeState;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderState.TerminalStateBuilder;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.input.TerminalInputReader;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.utils.TimeHelpers;

/**
 * RunningInstancesScreen - View and manage running node instances
 * 
 * Features:
 * - List all running instances with status
 * - View instance details (uptime, crash count, etc.)
 * - Stop running instances (with password confirmation)
 * 
 * Uses RuntimeAccess to interact with system services
 */
class RunningInstancesScreen extends TerminalScreen {
    
    private enum View {
        INSTANCE_LIST,
        INSTANCE_DETAILS,
        CONFIRM_STOP
    }
    
    private View currentView = View.INSTANCE_LIST;
    private NodeInstance selectedInstance;
    private Runnable onBack;
    
    private MenuNavigator menuNavigator;
    private PasswordReader passwordReader;
    private final NodeCommands nodeCommands;
    private List<NodeInstance> cachedInstances; // Cache for rendering
    
    public RunningInstancesScreen(
        String name, 
        SystemApplication systemApplication, 
        NodeCommands nodeCommands
    ){
        super(name, systemApplication);
        this.nodeCommands = nodeCommands;
    }
    
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public TerminalRenderState getRenderState() {
        TerminalStateBuilder builder = TerminalRenderState.builder();
        
        // Clear screen
        builder.add((term) -> term.clear());
        
        // Build base content based on current view
        switch (currentView) {
            case INSTANCE_LIST:
                buildInstanceListState(builder);
                break;
            case INSTANCE_DETAILS:
                buildInstanceDetailsState(builder);
                break;
            case CONFIRM_STOP:
                buildConfirmStopState(builder);
                break;
        }
        
        // If menu is active, add its render state on top
        if (menuNavigator != null) {
            builder.addAll(menuNavigator.getRenderState().getElements());
        }
        
        return builder.build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentView = View.INSTANCE_LIST;
        selectedInstance = null;
        cachedInstances = null;
        
        // Load instances and show
        return loadInstancesAndShow();
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    // ===== INSTANCE LIST =====
    
    private CompletableFuture<Void> loadInstancesAndShow() {
        return nodeCommands.getRunningInstances()
            .thenAccept(instances -> {
                cachedInstances = instances;
                if (instances.isEmpty()) {
                    // No instances - show message and wait for key
                    invalidate();
                    systemApplication.waitForKeyPress(this::goBack);
                } else {
                    // Show instances and menu
                    invalidate();
                    showInstanceListMenu(instances);
                }
            })
            .exceptionally(ex -> {
                systemApplication.getTerminal().printError("Failed to load instances: " + ex.getMessage())
                    .thenCompose(v -> systemApplication.getTerminal().printAt(7, 10, "Press any key to go back..."))
                    .thenRun(() -> systemApplication.waitForKeyPress(this::goBack));
                return null;
            });
    }
    
    private void buildInstanceListState(TerminalStateBuilder builder) {
        // Title
        builder.add((term) -> 
            term.printAt(1, (RunningInstancesScreen.this.systemApplication.getWidth() - 23) / 2, "Running Node Instances", TextStyle.BOLD));
        
        if (cachedInstances == null || cachedInstances.isEmpty()) {
            builder.add((term) -> {
                term.printAt(5, 10, "No instances currently running", TextStyle.NORMAL);
                term.printAt(7, 10, "Press ESC to go back", TextStyle.INFO);
            });
        } else {
            buildInstanceTableState(builder, cachedInstances, 5);
        }
    }
    
    private void buildInstanceTableState(TerminalStateBuilder builder, List<NodeInstance> instances, int startRow) {
        // Header
        builder.add((term) -> {
            term.printAt(startRow, 10, "Package", TextStyle.BOLD);
            term.printAt(startRow, 35, "Process ID", TextStyle.BOLD);
            term.printAt(startRow, 55, "State", TextStyle.BOLD);
            term.printAt(startRow, 70, "Uptime", TextStyle.BOLD);
            term.printAt(startRow + 1, 10, "─".repeat(70), TextStyle.NORMAL);
        });
        
        int row = startRow + 2;
        int index = 1;
        
        for (NodeInstance instance : instances) {
            InstalledPackage pkg = instance.getPackage();
            String packageName = pkg.getName() + " v" + pkg.getVersion();
            String processId = instance.getProcessId().toString();
            String state = instance.getState().toString();
            String uptime = formatUptime(instance.getUptime());
            
            final int currentRow = row;
            final int currentIndex = index;
            
            builder.add((term) -> {
                term.printAt(currentRow, 8, currentIndex + ".", TextStyle.NORMAL);
                term.printAt(currentRow, 10, truncate(packageName, 23), TextStyle.NORMAL);
                term.printAt(currentRow, 35, truncate(processId, 18), TextStyle.NORMAL);
                term.printAt(currentRow, 55, state, TextStyle.NORMAL);
                term.printAt(currentRow, 70, uptime, TextStyle.NORMAL);
            });
            
            row++;
            index++;
        }
    }
    
    private void showInstanceListMenu(List<NodeInstance> instances) {
        ContextPath menuPath = systemApplication.getTerminal().getContextPath().append("menu", "instances");
        MenuContext menu = new MenuContext(menuPath, "Instance Actions");
        
        // Add menu item for each instance
        int index = 1;
        for (NodeInstance instance : instances) {
            final NodeInstance inst = instance;
            String itemId = "instance_" + index;
            String description = instance.getPackage().getName();
            String help = "View details and manage this instance";
            
            menu.addItem(itemId, description, help, () -> showInstanceDetails(inst));
            index++;
        }
        
        menu.addSeparator("Instance Navigation");
        menu.addItem("refresh", "Refresh List", "Reload instance list", () -> {
            cleanupMenuNavigator();
            onShow();
        });
        menu.addItem("back", "Back to Node Manager", this::goBack);
        
        menuNavigator = new MenuNavigator(systemApplication);
        menuNavigator.showMenu(menu);
    }
    
    // ===== INSTANCE DETAILS =====
    
    private void showInstanceDetails(NodeInstance instance) {
        cleanupMenuNavigator();
        selectedInstance = instance;
        currentView = View.INSTANCE_DETAILS;
        invalidate();
        showInstanceDetailsMenu();
    }
    
    private void buildInstanceDetailsState(TerminalStateBuilder builder) {
        if (selectedInstance == null) {
            currentView = View.INSTANCE_LIST;
            buildInstanceListState(builder);
            return;
        }
        
        InstalledPackage pkg = selectedInstance.getPackage();
        
        builder.add((term) -> {
            term.printAt(1, (RunningInstancesScreen.this.systemApplication.getWidth() - 16) / 2, "Instance Details", TextStyle.BOLD);
            term.printAt(5, 10, "Package: " + pkg.getName(), TextStyle.NORMAL);
            term.printAt(6, 10, "Version: " + pkg.getVersion(), TextStyle.NORMAL);
            term.printAt(7, 10, "Description: " + pkg.getDescription(), TextStyle.NORMAL);
            term.printAt(9, 10, "Instance ID: " + selectedInstance.getInstanceId(), TextStyle.NORMAL);
            term.printAt(10, 10, "Process ID: " + selectedInstance.getProcessId(), TextStyle.NORMAL);
            term.printAt(11, 10, "State: " + selectedInstance.getState(), TextStyle.NORMAL);
            term.printAt(12, 10, "Uptime: " + formatUptime(selectedInstance.getUptime()), TextStyle.NORMAL);
            term.printAt(13, 10, "Crash Count: " + selectedInstance.getCrashCount(), TextStyle.NORMAL);
            term.printAt(15, 10, "Data Path: " + selectedInstance.getDataRootPath(), TextStyle.NORMAL);
            term.printAt(16, 10, "Flow Path: " + selectedInstance.getFlowBasePath(), TextStyle.NORMAL);
            term.printAt(18, 10, "Choose an action:", TextStyle.NORMAL);
        });
    }
    
    private void showInstanceDetailsMenu() {
        ContextPath menuPath = systemApplication.getTerminal().getContextPath().append("menu", "instance-details");
        MenuContext menu = new MenuContext(menuPath, "Instance Actions");
        
        // Only allow stopping if instance is running
        if (selectedInstance.getState() == NodeState.RUNNING) {
            menu.addItem("stop", "Stop Instance", 
                "Stop this running instance (requires password)",
                this::confirmStopInstance);
        }
        
        menu.addItem("refresh", "Refresh Details", 
            "Reload instance information",
            () -> invalidate());
        
        menu.addSeparator("Navigation");
        menu.addItem("back", "Back to Instance List", () -> {
            cleanupMenuNavigator();
            selectedInstance = null;
            currentView = View.INSTANCE_LIST;
            invalidate();
            if (cachedInstances != null && !cachedInstances.isEmpty()) {
                showInstanceListMenu(cachedInstances);
            }
        });
        
        menuNavigator = new MenuNavigator(systemApplication);
        menuNavigator.showMenu(menu);
    }
    
    // ===== CONFIRM STOP =====
    
    private void confirmStopInstance() {
        cleanupMenuNavigator();
        currentView = View.CONFIRM_STOP;
        invalidate();
        startStopConfirmation();
    }
    
    private void buildConfirmStopState(TerminalStateBuilder builder) {
        if (selectedInstance == null) {
            currentView = View.INSTANCE_LIST;
            buildInstanceListState(builder);
            return;
        }
        
        builder.add((term) -> {
            term.printAt(1, (RunningInstancesScreen.this.systemApplication.getWidth() - 20) / 2, "Confirm Stop Instance", TextStyle.BOLD);
            term.printAt(5, 10, "⚠ WARNING ⚠", TextStyle.WARNING);
            term.printAt(7, 10, "Stop instance: " + selectedInstance.getPackage().getName(), TextStyle.NORMAL);
            term.printAt(8, 10, "Process ID: " + selectedInstance.getProcessId(), TextStyle.NORMAL);
            term.printAt(10, 10, "This will:", TextStyle.NORMAL);
            term.printAt(11, 12, "• Gracefully shutdown the node", TextStyle.NORMAL);
            term.printAt(12, 12, "• Close all connections", TextStyle.NORMAL);
            term.printAt(13, 12, "• Unload from runtime", TextStyle.NORMAL);
            term.printAt(15, 10, "Type 'STOP' to confirm:", TextStyle.NORMAL);
            term.moveCursor(15, 30);
        });
    }

    private void startStopConfirmation() {
        TerminalInputReader inputReader = new TerminalInputReader(systemApplication, 15, 30, 20);

        
        inputReader.setOnComplete(input -> {
            inputReader.close();
            
            if ("STOP".equals(input)) {
                performStop();
            } else {
                systemApplication.getTerminal().printError("Confirmation failed")
                    .thenCompose(x -> systemApplication.getTerminal().printAt(17, 10, "Press any key to try again..."))
                    .thenRun(() -> systemApplication.waitForKeyPress( () -> {
                        currentView = View.CONFIRM_STOP;
                        invalidate();
                    }));
            }
        });
        
        inputReader.setOnEscape(text -> {
            inputReader.close();
            selectedInstance = null;
            currentView = View.INSTANCE_LIST;
            invalidate();
            if (cachedInstances != null && !cachedInstances.isEmpty()) {
                showInstanceListMenu(cachedInstances);
            }
        });
    }
        
    
    private CompletableFuture<Void> performStop() {
        return systemApplication.getTerminal().printAt(17, 10, "Stopping instance...                    ")
            .thenCompose(v -> nodeCommands.unloadNode(selectedInstance.getInstanceId()))
            .thenCompose(v -> systemApplication.getTerminal().printSuccess("Instance stopped successfully"))
            .thenCompose(v -> systemApplication.getTerminal().printAt(19, 10, "Returning to instance list..."))
            .thenRunAsync(() -> TimeHelpers.timeDelay(2))
            .thenRun(() -> {
                selectedInstance = null;
                currentView = View.INSTANCE_LIST;
                invalidate();
                // Reload instances after stopping
                loadInstancesAndShow();
            })
            .exceptionally(ex -> {
                systemApplication.getTerminal().printError("Failed to stop: " + ex.getMessage())
                .thenCompose(x -> systemApplication.getTerminal().printAt(21, 10, "Press any key..."))
                .thenRun(() -> systemApplication.waitForKeyPress( () -> {
                    selectedInstance = null;
                    currentView = View.INSTANCE_LIST;
                    invalidate();
                    if (cachedInstances != null && !cachedInstances.isEmpty()) {
                        showInstanceListMenu(cachedInstances);
                    }
                }));
            return null;
        });
    }
    // ===== UTILITY =====
    
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
            passwordReader.close();
            passwordReader = null;
        }
    }
    
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh", days, hours % 24);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}