package io.netnotes.engine.core.system;

import org.bouncycastle.crypto.signers.Ed25519Signer;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.system.control.nodes.*;
import io.netnotes.engine.crypto.AsymmetricPairs;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * NodeCommands - Terminal-side node management with Ed25519 signing
 * 
 * 
 * LIFECYCLE:
 * 1. User authenticates with password
 * 2. Terminal gets AsymmetricPairs from RuntimeAccess
 * 3. Commands signed automatically
 * 4. NodeController verifies signatures
 * 6. Instance is lost, must re-authenticate
 */
class NodeCommands {
    
    private final SystemTerminalContainer terminal;
    private final AsymmetricPairs pairs;
    
    NodeCommands(SystemTerminalContainer terminal, AsymmetricPairs pairs) {
        this.terminal = terminal;
        this.pairs = pairs;

    }
    

    
    // ═══════════════════════════════════════════════════════════════════════
    // SIGNING INFRASTRUCTURE
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Sign a command with Ed25519 private key
     * 
     * Signature covers: COMMAND_NAME + CANONICAL_PAYLOAD
     * This prevents tampering with either the command or its parameters
     */
    private NoteBytesReadOnly signCommand(
        NoteBytes payload
    ) throws IllegalStateException {
     
        // Sign with Ed25519
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, pairs.getSigningPrivateKey());
        byte[] dataBytes = payload.getBytes();
        signer.update(dataBytes, 0, dataBytes.length);
        byte[] signature = signer.generateSignature();
        
        // Build signed command message
        
        NoteBytesObject signaturePacket = new NoteBytesObject(
            new NoteBytesPair(Keys.SIGNATURE, new NoteBytesReadOnly(signature)),
            new NoteBytesPair(Keys.PAYLOAD, payload),
            new NoteBytesPair(Keys.TIMESTAMP, System.currentTimeMillis())
        );
       
        return signaturePacket.readOnly();
    } 
    

        /**
         * Send message to a system process and wait for response
         */
        CompletableFuture<RoutedPacket> sendMessageToProcess(
            ContextPath targetPath,
            NoteBytesMap command
        ) {
      
            try {
                return terminal.request(targetPath, signCommand(command.toNoteBytes()), Duration.ofSeconds(1));
            } catch (IllegalStateException e) {
                return CompletableFuture.failedFuture(e);
            }
        } 

        private NoteBytesMap buildCommand(NoteBytes commandName) {
            NoteBytesMap command = new NoteBytesMap();
            command.put(Keys.CMD, commandName);
            return command;
        }

        

        // ═══════════════════════════════════════════════════════════════════════
        // QUERY OPERATIONS 
        // ═══════════════════════════════════════════════════════════════════════
        
        /**
         * Get list of installed packages
         */
        CompletableFuture<List<InstalledPackage>> getInstalledPackages() {
            NoteBytesMap command = buildCommand(NodeConstants.LIST_INSTALLED);
            
            return sendMessageToProcess(CoreConstants.NODE_CONTROLLER_PATH, command)
                .thenApply(response -> {
                    NoteBytesMap result = response.getPayload().getAsMap();
                    NoteBytesArrayReadOnly packagesArray = result.get(NodeConstants.PACKAGES).getAsNoteBytesArrayReadOnly();
                    
                    List<InstalledPackage> packages = new ArrayList<>();
                    for (NoteBytes pkgBytes : packagesArray.getAsArray()) {
                        try {
                            packages.add(InstalledPackage.fromNoteBytes(
                                pkgBytes.getAsNoteBytesMap()));
                        } catch (Exception e) {
                            Log.logError("[NodeCommands] Failed to parse package: " + e.getMessage());
                        }
                    }
                    return packages;
                })
                .exceptionally(ex -> {
                    Log.logError("[NodeCommands] Failed to get installed packages: " + ex.getMessage());
                    return new ArrayList<>();
                });
        }

        /**
         * Get list of all running node instances
         */
        public CompletableFuture<List<NodeInstance>> getRunningInstances() {
            NoteBytesMap command = buildCommand(NodeConstants.LIST_INSTANCES);
            command.put(Keys.FILTER, ProtocolMesssages.ALL);
            
            return sendMessageToProcess(CoreConstants.NODE_CONTROLLER_PATH, command)
                .thenApply(response -> parseInstanceList(response.getPayload().getAsMap()))
                .exceptionally(ex -> {
                    Log.logError("[NodeCommands] Failed to get running instances: " + ex.getMessage());
                    return new ArrayList<>();
                });
        }

        /**
         * Get instances of a specific package
         */
        public CompletableFuture<List<NodeInstance>> getInstancesByPackage(PackageId packageId) {
            NoteBytesMap command = buildCommand(NodeConstants.LIST_INSTANCES);
            command.put(Keys.FILTER, NodeConstants.BY_PACKAGE);
            command.put(Keys.PACKAGE_ID, packageId.getId());
            
            return sendMessageToProcess(CoreConstants.NODE_CONTROLLER_PATH, command)
                .thenApply(response -> parseInstanceList(response.getPayload().getAsMap()))
                .exceptionally(ex -> {
                    Log.logError("[NodeCommands] Failed to get instances by package: " + ex.getMessage());
                    return new ArrayList<>();
                });
        }
        
        /**
         * Get instances in a specific process namespace
         */
        public CompletableFuture<List<NodeInstance>> getInstancesByProcess(String processId) {
            NoteBytesMap command = buildCommand(NodeConstants.LIST_INSTANCES);
            command.put(Keys.FILTER, NodeConstants.BY_PROCESS);
            command.put(Keys.PROCESS_ID, processId);
            
            return sendMessageToProcess(CoreConstants.NODE_CONTROLLER_PATH, command)
                .thenApply(response -> parseInstanceList(response.getPayload().getAsMap()))
                .exceptionally(ex -> {
                    Log.logError("[NodeCommands] Failed to get instances by process: " + ex.getMessage());
                    return new ArrayList<>();
                });
        }
        
        /**
         * Check if a package is installed
         */
        public CompletableFuture<Boolean> isPackageInstalled(PackageId packageId) {
            return getInstalledPackages()
                .thenApply(packages -> 
                    packages.stream().anyMatch(pkg -> pkg.getPackageId().equals(packageId)));
        }
        
        /**
         * Get specific installed package by ID
         */
        public CompletableFuture<InstalledPackage> getInstalledPackage(PackageId packageId) {
            return getInstalledPackages()
                .thenApply(packages -> 
                    packages.stream()
                        .filter(pkg -> pkg.getPackageId().equals(packageId))
                        .findFirst()
                        .orElse(null));
        }

        // ═══════════════════════════════════════════════════════════════════════
        // REPOSITORY OPERATIONS
        // ═══════════════════════════════════════════════════════════════════════
        
        /**
         * Browse available packages from repositories
         */
        public CompletableFuture<List<PackageInfo>> browseAvailablePackages() {
            NoteBytesMap command = buildCommand(NodeConstants.BROWSE_PACKAGES);
            
            return sendMessageToProcess(CoreConstants.REPOSITORIES_PATH, command)
                .thenApply(response -> {
                    NoteBytesMap result = response.getPayload().getAsMap();
                    NoteBytesArrayReadOnly packagesArray = result.get(NodeConstants.PACKAGES).getAsNoteBytesArrayReadOnly();
                    
                    List<PackageInfo> packages = new ArrayList<>();
                    for (NoteBytes pkgBytes : packagesArray.getAsArray()) {
                        try {
                            packages.add(PackageInfo.fromNoteBytes(
                                pkgBytes.getAsNoteBytesMap()));
                        } catch (Exception e) {
                            Log.logError("[NodeCommands] Failed to parse package info: " + e.getMessage());
                        }
                    }
                    return packages;
                })
                .exceptionally(ex -> {
                    Log.logError("[NodeCommands] Failed to browse packages: " + ex.getMessage());
                    return new ArrayList<>();
                });
        }
        
        /**
         * Update package cache from repositories (like apt-get update)
         */
        public CompletableFuture<Integer> updatePackageCache() {
            NoteBytesMap command = buildCommand(NodeConstants.UPDATE_CACHE);
            
            return sendMessageToProcess(CoreConstants.REPOSITORIES_PATH, command)
                .thenApply(response -> {
                    NoteBytesMap result = response.getPayload().getAsMap();
                    NoteBytes countBytes = result.get(NodeConstants.PACKAGE_COUNT);
                    return countBytes != null ? countBytes.getAsInt() : 0;
                })
                .exceptionally(ex -> {
                    Log.logError("[NodeCommands] Failed to update cache: " + ex.getMessage());
                    return 0;
                });
        }
        
        /**
         * Search packages by name or description
         */
        public CompletableFuture<List<PackageInfo>> searchPackages(String query) {
            return browseAvailablePackages()
                .thenApply(packages -> {
                    String lowerQuery = query.toLowerCase();
                    return packages.stream()
                        .filter(pkg -> 
                            pkg.getName().toLowerCase().contains(lowerQuery) ||
                            pkg.getDescription().toLowerCase().contains(lowerQuery))
                        .toList();
                });
        }

        // ═══════════════════════════════════════════════════════════════════════
        // INSTALLATION OPERATIONS (Password verified by caller)
        // ═══════════════════════════════════════════════════════════════════════
        
        /**
         * Install package (password already verified by caller)
         */
        public CompletableFuture<InstalledPackage> installPackage(
            PackageInfo packageInfo,
            ProcessConfig processConfig,
            io.netnotes.engine.core.system.control.nodes.security.PolicyManifest policyManifest,
            boolean loadImmediately
        ) {
            NoteBytesMap command = buildCommand(NodeConstants.INSTALL_PACKAGE);
            command.put(NodeConstants.PACKAGE_INFO, packageInfo.toNoteBytes());
            command.put(NodeConstants.PROCESS_CONFIG, processConfig.toNoteBytes());
            command.put(NodeConstants.POLICY_MANIFEST, policyManifest.toNoteBytes());
            command.put(NodeConstants.LOAD_IMMEDIATELY, loadImmediately);
            
            return sendMessageToProcess(CoreConstants.NODE_CONTROLLER_PATH, command)
                .thenApply(response -> {
                    NoteBytesMap result = response.getPayload().getAsMap();
                    NoteBytes status = result.get(Keys.STATUS);
                    
                    if (!ProtocolMesssages.SUCCESS.equals(status)) {
                   
                        throw new RuntimeException("Installation failed: " + ProtocolObjects.getErrMsg(result));
                    }
                    
                    return InstalledPackage.fromNoteBytes(
                        result.get(NodeConstants.INSTALLED_PACKAGE).getAsNoteBytesMap());
                });
        }

        /**
         * Uninstall package (password already verified by caller)
         */
        public CompletableFuture<Void> uninstallPackage(
            PackageId packageId,
            boolean deleteData
        ) {
            NoteBytesMap command = buildCommand(NodeConstants.UNINSTALL_PACKAGE);
            command.put(Keys.PACKAGE_ID, packageId.getId());
            command.put(NodeConstants.DELETE_DATA, deleteData);
            
            return sendMessageToProcess(CoreConstants.NODE_CONTROLLER_PATH, command)
                .thenAccept(response -> {
                    NoteBytesMap result = response.getPayload().getAsMap();
                    NoteBytes status = result.get(Keys.STATUS);
                    
                    if (!ProtocolMesssages.SUCCESS.equals(status)) {
                        throw new RuntimeException("Uninstall failed: " + ProtocolObjects.getErrMsg(result));
                    }
                });
        }
        
        /**
         * Update package configuration (password already verified by caller)
         */
        public CompletableFuture<Void> updatePackageConfiguration(
            PackageId packageId,
            ProcessConfig newProcessConfig
        ) {
            NoteBytesMap command = buildCommand(NodeConstants.UPDATE_CONFIG);
            command.put(Keys.PACKAGE_ID, packageId.getId());
            command.put(NodeConstants.PROCESS_CONFIG, newProcessConfig.toNoteBytes());
            
            return sendMessageToProcess(CoreConstants.NODE_CONTROLLER_PATH, command)
                .thenAccept(response -> {
                    NoteBytesMap result = response.getPayload().getAsMap();
                    NoteBytes status = result.get(Keys.STATUS);
                    
                    if (!ProtocolMesssages.SUCCESS.equals(status)) {
                        throw new RuntimeException("Config update failed: " + ProtocolObjects.getErrMsg(result));
                    }
                });
        }

        // ═══════════════════════════════════════════════════════════════════════
        // INSTANCE LIFECYCLE OPERATIONS
        // ═══════════════════════════════════════════════════════════════════════

        /**
         * Load node instance from installed package
         */
        public CompletableFuture<NodeInstance> loadNode(NoteBytesReadOnly packageId) {
            NoteBytesMap command = buildCommand(NodeConstants.LOAD_NODE);
            command.put(Keys.PACKAGE_ID, packageId);
            
            return sendMessageToProcess(CoreConstants.NODE_CONTROLLER_PATH, command)
                .thenApply(response -> {
                    NoteBytesMap result = response.getPayload().getAsMap();
                    NoteBytes status = result.get(Keys.STATUS);
                    
                    if (!ProtocolMesssages.SUCCESS.equals(status)) {
                        throw new RuntimeException("Load failed: " + ProtocolObjects.getErrMsg(result));
                    }
                    
                    return parseNodeInstance(result.get(Keys.INSTANCE).getAsNoteBytesMap());
                });
        }

        /**
         * Unload node instance
         */
        public CompletableFuture<Void> unloadNode(
            io.netnotes.engine.core.system.control.nodes.InstanceId instanceId
        ) {
            NoteBytesMap command = buildCommand(NodeConstants.UNLOAD_INSTANCE);
            command.put(Keys.INSTANCE_ID, instanceId.toString());
            
            return sendMessageToProcess(CoreConstants.NODE_CONTROLLER_PATH, command)
                .thenAccept(response -> {
                    NoteBytesMap result = response.getPayload().getAsMap();
                    NoteBytes status = result.get(Keys.STATUS);
                    
                    if (!ProtocolMesssages.SUCCESS.equals(status)) {
                        throw new RuntimeException("Unload failed: " + ProtocolObjects.getErrMsg(result));
                    }
                });
        }
        
        /**
         * Unload all instances of a package
         */
        public CompletableFuture<Void> unloadPackage(PackageId packageId) {
            return getInstancesByPackage(packageId)
                .thenCompose(instances -> {
                    if (instances.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    
                    List<CompletableFuture<Void>> unloadFutures = instances.stream()
                        .map(inst -> unloadNode(inst.getInstanceId()))
                        .toList();
                    
                    return CompletableFuture.allOf(
                        unloadFutures.toArray(new CompletableFuture[0]));
                });
        }
        
        /**
         * Restart node instance (unload then load)
         */
        public CompletableFuture<NodeInstance> restartNode(
            io.netnotes.engine.core.system.control.nodes.InstanceId instanceId
        ) {
            // Get instance to find package ID
            return getRunningInstances()
                .thenCompose(instances -> {
                    NodeInstance instance = instances.stream()
                        .filter(inst -> inst.getInstanceId().equals(instanceId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Instance not found: " + instanceId));
                    
                    PackageId packageId = instance.getPackageId();
                    
                    // Unload then load
                    return unloadNode(instanceId)
                        .thenCompose(v -> loadNode(packageId.getId()));
                });
        }

        // ═══════════════════════════════════════════════════════════════════════
        // PARSING HELPERS
        // ═══════════════════════════════════════════════════════════════════════
        
        /**
         * Parse list of instances from response
         */
        private List<NodeInstance> parseInstanceList(NoteBytesMap result) {
            List<NodeInstance> instances = new ArrayList<>();
            
            NoteBytes instancesBytes = result.get(Keys.INSTANCES);
            if (instancesBytes == null) {
                return instances;
            }
            
            try {
                NoteBytesArrayReadOnly instancesArray = instancesBytes.getAsNoteBytesArrayReadOnly();
                for (NoteBytes instBytes : instancesArray.getAsArray()) {
                    try {
                        NodeInstance instance = parseNodeInstance(instBytes.getAsNoteBytesMap());
                        if (instance != null) {
                            instances.add(instance);
                        }
                    } catch (Exception e) {
                        Log.logError("[NodeCommands] Failed to parse instance: " + 
                            e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.logError("[NodeCommands] Failed to parse instance list: " + 
                    e.getMessage());
            }
            
            return instances;
        }
        
        /**
         * Parse single NodeInstance from map
         * 
         * Note: This creates a lightweight representation since full INode
         * instance can't be serialized over the wire
         */
        private NodeInstance parseNodeInstance(NoteBytesMap data) {
            try {
                // Extract instance metadata
                String instanceIdStr = data.get(Keys.INSTANCE_ID).getAsString();
                io.netnotes.engine.core.system.control.nodes.InstanceId instanceId = 
                    io.netnotes.engine.core.system.control.nodes.InstanceId.fromString(instanceIdStr);
                
                // Parse installed package
                InstalledPackage pkg = InstalledPackage.fromNoteBytes(
                    data.get(NodeConstants.INSTALLED_PACKAGE).getAsNoteBytesMap());
                
                // Parse state
                String stateStr = data.get(Keys.STATE).getAsString();
                io.netnotes.engine.core.system.control.nodes.NodeState state = 
                    io.netnotes.engine.core.system.control.nodes.NodeState.valueOf(stateStr);
                
                // Parse timestamps
                long loadTime = data.get(NodeConstants.LOAD_TIME).getAsLong();
                int crashCount = data.get(NodeConstants.CRASH_COUNT).getAsInt();
                
                // Create instance (without actual INode - that stays in NodeController)
                // This is just a metadata representation for UI/queries
                NodeInstance instance = new NodeInstance(pkg, null); // INode is null for remote view
                instance.setState(state);
                // Note: Would need to set other fields via reflection or add constructor
                
                return instance;
                
            } catch (Exception e) {
                Log.logError("[NodeCommands] Failed to parse node instance: " + 
                    e.getMessage());
                return null;
            }
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // STATISTICS & DIAGNOSTICS
        // ═══════════════════════════════════════════════════════════════════════
        
        /**
         * Get node system statistics
         */
        public CompletableFuture<NodeSystemStats> getSystemStats() {
            CompletableFuture<List<InstalledPackage>> installedFuture = getInstalledPackages();
            CompletableFuture<List<NodeInstance>> instancesFuture = getRunningInstances();
            
            return CompletableFuture.allOf(installedFuture, instancesFuture)
                .thenApply(v -> {
                    List<InstalledPackage> installed = installedFuture.join();
                    List<NodeInstance> instances = instancesFuture.join();
                    
                    return new NodeSystemStats(
                        installed.size(),
                        instances.size(),
                        instances.stream()
                            .filter(inst -> inst.getState() == 
                                io.netnotes.engine.core.system.control.nodes.NodeState.RUNNING)
                            .count(),
                        instances.stream()
                            .filter(inst -> inst.getState() == 
                                io.netnotes.engine.core.system.control.nodes.NodeState.CRASHED)
                            .count()
                    );
                });
        }
        
        /**
         * Simple statistics container
         */
        public static class NodeSystemStats {
            public final int installedPackages;
            public final int totalInstances;
            public final long runningInstances;
            public final long crashedInstances;
            
            public NodeSystemStats(
                int installedPackages,
                int totalInstances,
                long runningInstances,
                long crashedInstances
            ) {
                this.installedPackages = installedPackages;
                this.totalInstances = totalInstances;
                this.runningInstances = runningInstances;
                this.crashedInstances = crashedInstances;
            }
            
            @Override
            public String toString() {
                return String.format(
                    "Packages: %d installed | Instances: %d total (%d running, %d crashed)",
                    installedPackages, totalInstances, runningInstances, crashedInstances
                );
            }
        }
}