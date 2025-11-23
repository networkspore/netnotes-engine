package io.netnotes.engine.core.system;


import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.ui.*;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.IODaemon;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BaseSystemProcess - Bootstrap and service manager
 * 
 * Responsibilities:
 * - Load/save bootstrap config
 * - Manage IODaemon lifecycle
 * - Create SystemSession processes (local or remote)
 * - Provide secure input to sessions
 * 
 * Does NOT:
 * - Handle UI directly (delegated to SystemSession)
 * - Manage menus (delegated to SystemSession)
 * - Handle passwords (delegated to PasswordSessionProcess)
 */
public class BaseSystemProcess extends FlowProcess {
    
    private NoteBytesMap bootstrapConfig;
    private final Map<ContextPath, SystemSessionProcess> activeSessions = 
        new ConcurrentHashMap<>();
    
    // Services
    private IODaemon ioDaemon;
    
    private BaseSystemProcess() {
        super(ProcessType.BIDIRECTIONAL);
    }
    
    /**
     * Bootstrap entry point
     */
    public static CompletableFuture<BaseSystemProcess> bootstrap() {
        BaseSystemProcess process = new BaseSystemProcess();
        
        return process.initialize()
            .thenApply(v -> process);
    }
    
    private CompletableFuture<Void> initialize() {
        return registerSelfInRegistry()
            .thenCompose(v -> loadOrCreateBootstrapConfig())
            .thenCompose(config -> {
                this.bootstrapConfig = config;
                return startConfiguredServices();
            });
    }
    
    private CompletableFuture<Void> registerSelfInRegistry() {
        ContextPath basePath = ContextPath.of("system", "base");
        registry.registerProcess(this, basePath, null);
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<NoteBytesMap> loadOrCreateBootstrapConfig() {
        boolean isBootstrapData = false;
        try{
            isBootstrapData = SettingsData.isBootstrapData();
        }catch(IOException e){
            System.err.println("Error loading bootstrap config: " + e.toString());
            isBootstrapData = false;
        }
        return isBootstrapData ? SettingsData.loadBootStrapConfig() : CompletableFuture.completedFuture(BootstrapConfig.createDefault());
    }
    
    private CompletableFuture<Void> startConfiguredServices() {
        if (BootstrapConfig.isSecureInputInstalled(bootstrapConfig)) {
            return startIODaemon();
        }
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> startIODaemon() {
        String socketPath = BootstrapConfig.getSecureInputSocketPath(bootstrapConfig);
        ioDaemon = new IODaemon(socketPath);
        
        return spawnChild(ioDaemon, "io-daemon")
            .thenCompose(path -> registry.startProcess(path));
    }
    
    /**
     * Create a new system session (local or remote)
     */
    public CompletableFuture<ContextPath> createSession(
            String sessionId,
            SystemSessionProcess.SessionType type,
            UIRenderer uiRenderer) {
        
        SystemSessionProcess session = new SystemSessionProcess(
            sessionId, type, uiRenderer);
        
        ContextPath sessionPath = contextPath.append("sessions", sessionId);
        
        return CompletableFuture.supplyAsync(() -> {
            registry.registerProcess(session, sessionPath, contextPath);
            activeSessions.put(sessionPath, session);
            
            registry.startProcess(sessionPath);
            
            return sessionPath;
        });
    }
    
    @Override
    public CompletableFuture<Void> run() {
        return getCompletionFuture();
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Handle session requests (like get_secure_input_device)
        // TODO: Implement
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException();
    }
}