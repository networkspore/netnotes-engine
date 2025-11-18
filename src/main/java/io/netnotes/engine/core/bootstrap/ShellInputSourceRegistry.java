package io.netnotes.engine.core.bootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.DiscoveredDeviceRegistry;
import io.netnotes.engine.io.daemon.IODaemon;
import io.netnotes.engine.io.process.FlowProcessRegistry;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * ShellInputSourceRegistry - Manages input sources for the command shell
 * 
 * The shell needs to accept commands/passwords from various sources:
 * - GUI native input (keyboard events from OS)
 * - Secure input (IODaemon - USB devices)
 * - Network input (future - remote shells)
 * 
 * This registry is shell-specific - it's about getting typed input
 * into the shell for local command processing.
 */
class ShellInputSourceRegistry {
    private final Map<String, InputSource> sources = new ConcurrentHashMap<>();
    private String activeSourcePath = null;
    
    /**
     * Register an input source at a path
     */
    public void registerSource(String path, InputSource source) {
        sources.put(path, source);
        System.out.println("Registered input source: " + path);
    }
    
    /**
     * Set the active input source for the shell
     */
    public void setActiveSource(String path) {
        if (!sources.containsKey(path)) {
            throw new IllegalArgumentException("Input source not registered: " + path);
        }
        this.activeSourcePath = path;
        System.out.println("Shell input source: " + path);
    }
    
    /**
     * Get the currently active input source
     */
    public InputSource getActiveSource() {
        if (activeSourcePath == null) {
            throw new IllegalStateException("No active input source");
        }
        return sources.get(activeSourcePath);
    }
    
    /**
     * Read password from active input source
     */
    public CompletableFuture<NoteBytesEphemeral> readPassword(PasswordContext context) {
        InputSource source = getActiveSource();
        return source.readPassword(context);
    }
    
    /**
     * Read command from active input source
     */
    public CompletableFuture<String> readCommand() {
        InputSource source = getActiveSource();
        return source.readLine();
    }
    
    /**
     * Input source interface
     */
    interface InputSource {
        CompletableFuture<NoteBytesEphemeral> readPassword(PasswordContext context);
        CompletableFuture<String> readLine();
        String getSourcePath();
    }
    
    /**
     * GUI Native input source
     */
    static class GUINativeInputSource implements InputSource {
        private final BootstrapUI ui;
        
        public GUINativeInputSource(BootstrapUI ui) {
            this.ui = ui;
        }
        
        @Override
        public CompletableFuture<NoteBytesEphemeral> readPassword(PasswordContext context) {
            return ui.promptPassword(context, PasswordInputSource.GUI_NATIVE);
        }
        
        @Override
        public CompletableFuture<String> readLine() {
            return ui.promptCommand();
        }
        
        @Override
        public String getSourcePath() {
            return "system/gui/native";
        }
    }
    
    /**
     * Secure input source (IODaemon)
     */
   /**
     * Secure input source - communicates with IODaemon Process
     */
    static class SecureInputSource implements InputSource {
        private final ContextPath iosDaemonPath;
        private final FlowProcessRegistry registry;
        
        public SecureInputSource(ContextPath iosDaemonPath) {
            this.iosDaemonPath = iosDaemonPath;
            this.registry = FlowProcessRegistry.getInstance();
        }
        
        @Override
        public CompletableFuture<NoteBytesEphemeral> readPassword(PasswordContext context) {
            // Get IODaemon Process
            FlowProcess daemonProcess = registry.getProcess(iosDaemonPath);
            
            if (daemonProcess == null || !(daemonProcess instanceof IODaemon)) {
                return CompletableFuture.failedFuture(
                    new IllegalStateException("IODaemon not available at " + iosDaemonPath));
            }
            
            IODaemon daemon = (IODaemon) daemonProcess;
            
            // Find keyboard device
            return findKeyboardDevice(daemon)
                .thenCompose(keyboardId -> claimKeyboard(daemon, keyboardId))
                .thenCompose(keyboardPath -> subscribeToKeyboardEvents(keyboardPath))
                .thenApply(keystrokes -> convertToPassword(keystrokes));
        }
        
        private CompletableFuture<String> findKeyboardDevice(IODaemon daemon) {
            return CompletableFuture.supplyAsync(() -> {
                List<DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities> devices = 
                    daemon.getDiscoveredDevices();
                
                return devices.stream()
                    .filter(dev -> "keyboard".equals(dev.usbDevice().get_device_type()))
                    .map(dev -> dev.usbDevice().deviceId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No keyboard found"));
            });
        }
        
        private CompletableFuture<ContextPath> claimKeyboard(IODaemon daemon, String deviceId) {
            return daemon.claimDevice(deviceId, "keyboard_events");
        }
        
        private CompletableFuture<List<Keystroke>> subscribeToKeyboardEvents(ContextPath keyboardPath) {
            // Subscribe to keyboard events from IODaemon until Enter is pressed
            CompletableFuture<List<Keystroke>> result = new CompletableFuture<>();
            List<Keystroke> keystrokes = new ArrayList<>();
            
            // Create a subscriber that accumulates keystrokes
            Flow.Subscriber<RoutedPacket> keystrokeCollector = new Flow.Subscriber<>() {
                private Flow.Subscription subscription;
                
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(1);
                }
                
                @Override
                public void onNext(RoutedPacket packet) {
                    try {
                        // Parse keystroke from packet
                        NoteBytesMap eventData = packet.getPayload().getAsNoteBytesMap();
                        String key = eventData.get("key").getAsString();
                        
                        if ("Enter".equals(key)) {
                            // Done collecting
                            result.complete(keystrokes);
                            subscription.cancel();
                        } else if ("Backspace".equals(key)) {
                            if (!keystrokes.isEmpty()) {
                                keystrokes.remove(keystrokes.size() - 1);
                            }
                            subscription.request(1);
                        } else {
                            keystrokes.add(new Keystroke(key));
                            subscription.request(1);
                        }
                        
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }
                }
                
                @Override
                public void onError(Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
                
                @Override
                public void onComplete() {
                    result.complete(keystrokes);
                }
            };
            
            // Subscribe to keyboard path
            FlowProcess daemon = registry.getProcess(iosDaemonPath);
            daemon.subscribe(keystrokeCollector, keyboardPath);
            
            return result;
        }
        
        private NoteBytesEphemeral convertToPassword(List<Keystroke> keystrokes) {
            byte[] passwordBytes = new byte[keystrokes.size()];
            for (int i = 0; i < keystrokes.size(); i++) {
                passwordBytes[i] = keystrokes.get(i).toByte();
            }
            return new NoteBytesEphemeral(passwordBytes);
        }
        
        @Override
        public CompletableFuture<String> readLine() {
            // Similar to readPassword but returns String
            return CompletableFuture.completedFuture("");
        }
        
        @Override
        public String getSourcePath() {
            return "system/base/secure-input";
        }
        
        private static class Keystroke {
            private final String key;
            
            Keystroke(String key) {
                this.key = key;
            }
            
            byte toByte() {
                return key.getBytes()[0];
            }
        }
    }
}