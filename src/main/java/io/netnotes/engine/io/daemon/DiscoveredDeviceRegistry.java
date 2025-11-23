package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.capabilities.DeviceCapabilitySet;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.io.capabilities.CapabilityRegistry;
import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DiscoveredDeviceRegistry - Pre-claim device storage
 * 
 * Responsibilities:
 * - Store USB devices discovered from daemon BEFORE claiming
 * - Parse daemon's BigInteger capability format to DeviceCapabilitySet
 * - Provide device lookup by deviceId
 * - Track which devices are claimed (have sourceId assigned)
 * 
 * Flow:
 * 1. Daemon sends device list with capabilities as BigInteger (cpp_int serialized)
 * 2. Registry parses and stores as DeviceDescriptorWithCapabilities
 * 3. Client claims device → gets sourceId → registered in InputSourceRegistry
 * 4. Registry marks device as claimed
 */
public class DiscoveredDeviceRegistry {
    
    private static final CapabilityRegistry CAPABILITY_REGISTRY = DeviceCapabilitySet.getRegistry();

    public static final NoteBytesReadOnly AVAILABLE_CAPABILITIES = new NoteBytesReadOnly("available_capabilities");
    
    // deviceId → device info (before claiming)
    private final Map<String, DeviceDescriptorWithCapabilities> discoveredDevices = 
        new ConcurrentHashMap<>();
    
    // Track claimed devices: deviceId → sourceId
    private final List<String> claimedDevices = new CopyOnWriteArrayList<>();
    
    /**
     * Container for device descriptor + capabilities
     */
    public record DeviceDescriptorWithCapabilities(
        IODaemonProtocol.USBDeviceDescriptor usbDevice,
        DeviceCapabilitySet capabilities,
        boolean claimed
    ) {
        public DeviceDescriptorWithCapabilities claim() {
            return new DeviceDescriptorWithCapabilities(
                usbDevice, capabilities, true
            );
        }
    }
    
    /**
     * Parse device list from daemon and populate registry
     * 
     * Expected format from C++:
     * {
     *   "type": TYPE_CMD,
     *   "cmd": "item_list",
     *   "items": [
     *     {
     *       "itemId": "device_id",
     *       "device_type": "keyboard",
     *       "available_capabilities": <BigInteger>,  // cpp_int serialized
     *       "vendor_id": 1234,
     *       // ... other USB fields
     *     }
     *   ]
     * }
     */
    public void parseDeviceList(NoteBytesMap messageMap) {
        NoteBytes itemsBytes = messageMap.get(Keys.ITEMS);
        if (itemsBytes == null || itemsBytes.getType() != NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
            System.err.println("No items array in device list");
            return;
        }
        
        NoteBytesReadOnly[] devicesArray = itemsBytes.getAsNoteBytesArrayReadOnly().getAsArray();
        
        discoveredDevices.clear();
        
        for (NoteBytesReadOnly deviceBytes : devicesArray) {
            if (deviceBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                DeviceDescriptorWithCapabilities deviceInfo = parseDevice(deviceBytes);
                if (deviceInfo != null) {
                    discoveredDevices.put(deviceInfo.usbDevice.deviceId, deviceInfo);
                }
            }
        }
        
        System.out.println("Discovered " + discoveredDevices.size() + " devices");
    }
    
    /**
     * Parse single device from daemon message
     */
    private DeviceDescriptorWithCapabilities parseDevice(NoteBytesReadOnly deviceBytes) {
        try {
            NoteBytesMap deviceMap = deviceBytes.getAsNoteBytesMap();
            
            // Parse USB descriptor
            IODaemonProtocol.USBDeviceDescriptor usbDevice = parseUSBDescriptor(deviceMap);
            
            // Parse capabilities from BigInteger
            DeviceCapabilitySet capabilities = parseCapabilities(deviceMap, usbDevice);
            
            return new DeviceDescriptorWithCapabilities(
                usbDevice, 
                capabilities,
                false
            );
            
        } catch (Exception e) {
            System.err.println("Failed to parse device: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Parse USB device descriptor fields
     */
    private IODaemonProtocol.USBDeviceDescriptor parseUSBDescriptor(NoteBytesMap deviceMap) {
        IODaemonProtocol.USBDeviceDescriptor desc = new IODaemonProtocol.USBDeviceDescriptor();
        
        // Required fields
        desc.deviceId = deviceMap.get("itemId").getAsString();
        desc.vendorId = deviceMap.get("vendor_id").getAsInt();
        desc.productId = deviceMap.get("product_id").getAsInt();
        desc.deviceClass = deviceMap.get("device_class").getAsInt();
        desc.deviceSubClass = deviceMap.get("device_subclass").getAsInt();
        desc.deviceProtocol = deviceMap.get("device_protocol").getAsInt();
        
        desc.busNumber = deviceMap.get("bus_number").getAsInt();
        desc.deviceAddress = deviceMap.get("device_address").getAsInt();
        
        // Device type
        NoteBytes typeBytes = deviceMap.get("itemType");
        if (typeBytes != null) {
            desc.set_device_type(typeBytes.getAsString());
        }
        
        // Optional string fields
        NoteBytes mfg = deviceMap.get("manufacturer");
        if (mfg != null) desc.manufacturer = mfg.getAsString();
        
        NoteBytes prod = deviceMap.get("product");
        if (prod != null) desc.product = prod.getAsString();
        
        NoteBytes serial = deviceMap.get("serial_number");
        if (serial != null) desc.serialNumber = serial.getAsString();
        
        // Status fields
        desc.available = deviceMap.get("available").getAsBoolean();
        desc.kernelDriverAttached = deviceMap.get("kernel_driver_attached").getAsBoolean();
        
        return desc;
    }
    
    /**
     * Parse capabilities from daemon's BigInteger format
     * 
     * C++ sends: obj.add("available_capabilities", cpp_int_value);
     * cpp_int serializes to BigInteger.toByteArray() format
     */
    private DeviceCapabilitySet parseCapabilities(
            NoteBytesMap deviceMap,
            IODaemonProtocol.USBDeviceDescriptor usbDevice) {
        
        String deviceName = usbDevice.product != null ? 
            usbDevice.product : "USB Device " + usbDevice.deviceId;
        String deviceType = usbDevice.get_device_type();
        
        DeviceCapabilitySet caps = new DeviceCapabilitySet(deviceName, deviceType);
        
        // Get capability bits from daemon
        NoteBytes capsBytes = deviceMap.getOrDefault(AVAILABLE_CAPABILITIES, null);
        if (capsBytes == null) {
            System.err.println("No capabilities in device descriptor");
            return caps;
        }
        
        // Convert to BigInteger (cpp_int → BigInteger.toByteArray() format)
        BigInteger capabilityBits = capsBytes.getAsBigInteger();
        
        // Parse each bit into named capability
        for (int i = 0; i < capabilityBits.bitLength(); i++) {
            if (capabilityBits.testBit(i)) {
                String capName = getCapabilityNameForBit(i);
                if (capName != null && CAPABILITY_REGISTRY.isRegistered(capName)) {
                    caps.addAvailableCapability(capName);
                } else {
                    System.out.println("Unknown capability bit: " + i);
                }
            }
        }
        
        // Also parse human-readable names if provided (for debugging)
        NoteBytes namesBytes = deviceMap.get("capability_names");
        if (namesBytes != null && namesBytes.getType() == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
            NoteBytesReadOnly[] names = namesBytes.getAsNoteBytesArrayReadOnly().getAsArray();
            System.out.println("Device capabilities: " + 
                Arrays.stream(names)
                    .map(NoteBytes::getAsString)
                    .toList());
        }
        
        return caps;
    }
    
    /**
     * Map bit position to capability name
     * Must match C++ capability_registry.h bit positions
     */
    private String getCapabilityNameForBit(int bitPosition) {
        // Device types (0-7)
        return switch (bitPosition) {
            case 0 -> "keyboard";
            case 1 -> "mouse";
            case 2 -> "touch";
            case 3 -> "gamepad";
            case 4 -> "pen";
            case 5 -> "touchpad";
            case 6 -> "scroll";
            
            // Modes (8-15)
            case 8 -> "raw_mode";
            case 9 -> "parsed_mode";
            case 10 -> "passthrough_mode";
            case 11 -> "filtered_mode";
            
            // Coordinates (16-23)
            case 16 -> "absolute_coordinates";
            case 17 -> "relative_coordinates";
            case 18 -> "screen_coordinates";
            case 19 -> "normalized_coordinates";
            
            // Advanced features (24-31)
            case 24 -> "high_precision";
            case 25 -> "multiple_devices";
            case 26 -> "global_capture";
            case 27 -> "provides_scancodes";
            case 28 -> "nanosecond_timestamps";
            
            // Device detection (32-39)
            case 32 -> "device_type_known";
            case 33 -> "hid_device";
            case 34 -> "usb_device";
            case 35 -> "bluetooth_device";
            
            // State (40-47)
            case 40 -> "encryption_supported";
            case 41 -> "encryption_enabled";
            case 42 -> "buffering_supported";
            case 43 -> "buffering_enabled";
            
            // Lifecycle (48-55)
            case 48 -> "scene_location";
            case 49 -> "scene_size";
            case 50 -> "window_lifecycle";
            case 51 -> "stage_position";
            case 52 -> "stage_size";
            case 53 -> "stage_focus";
            
            // Composite (56-63)
            case 56 -> "composite_source";
            case 57 -> "multiple_children";
            
            default -> null;
        };
    }
    
    // ===== QUERIES =====
    
    /**
     * Get device by deviceId
     */
    public DeviceDescriptorWithCapabilities getDevice(String deviceId) {
        return discoveredDevices.get(deviceId);
    }
    
    /**
     * Get all discovered devices
     */
    public List<DeviceDescriptorWithCapabilities> getAllDevices() {
        return new ArrayList<>(discoveredDevices.values());
    }
    
    /**
     * Get unclaimed devices
     */
    public List<DeviceDescriptorWithCapabilities> getUnclaimedDevices() {
        return discoveredDevices.values().stream()
            .filter(d -> !d.claimed)
            .toList();
    }
    
    /**
     * Get claimed devices
     */
    public List<DeviceDescriptorWithCapabilities> getClaimedDevices() {
        return discoveredDevices.values().stream()
            .filter(d -> d.claimed)
            .toList();
    }
    
    /**
     * Check if device is claimed
     */
    public boolean isClaimed(String deviceId) {
        return claimedDevices.contains(deviceId);
    }
    

    
    
    // ===== CLAIM MANAGEMENT =====
    
    /**
     * Mark device as claimed with sourceId
     */
    public void markClaimed(String deviceId) {
        DeviceDescriptorWithCapabilities device = discoveredDevices.get(deviceId);
        if (device != null) {
            discoveredDevices.put(deviceId, device.claim());
            claimedDevices.add(deviceId);
            System.out.println("Marked device " + deviceId + " as claimed");
        }
    }
    
    /**
     * Mark device as released (unclaimed)
     */
    public void markReleased(String deviceId) {
        DeviceDescriptorWithCapabilities device = discoveredDevices.get(deviceId);
        if (device != null) {
            discoveredDevices.put(deviceId, new DeviceDescriptorWithCapabilities(
                device.usbDevice, device.capabilities, false
            ));
            claimedDevices.remove(deviceId);
            System.out.println("Marked device " + deviceId + " as released");
        }
    }
    
    /**
     * Clear all discovered devices
     */
    public void clear() {
        discoveredDevices.clear();
        claimedDevices.clear();
    }
    
    // ===== VALIDATION =====
    
    /**
     * Validate mode compatibility before claiming
     */
    public boolean validateModeCompatibility(String deviceId, String requestedMode) {
        DeviceDescriptorWithCapabilities device = discoveredDevices.get(deviceId);
        if (device == null) {
            return false;
        }
        
        DeviceCapabilitySet caps = device.capabilities;
        
        // Check if mode capability exists
        if (!caps.hasCapability(requestedMode)) {
            System.err.println("Device does not have capability: " + requestedMode);
            return false;
        }
        
        // Check if mode can be enabled (constraint validation)
        if (!caps.canEnable(requestedMode)) {
            System.err.println("Cannot enable mode " + requestedMode + ": " + 
                             caps.getEnableFailureReason(requestedMode));
            return false;
        }
        
        return true;
    }
    
    /**
     * Get available modes for device
     */
    public Set<String> getAvailableModes(String deviceId) {
        DeviceDescriptorWithCapabilities device = discoveredDevices.get(deviceId);
        if (device == null) {
            return Collections.emptySet();
        }
        
        return device.capabilities.getAvailableModes();
    }
    
    // ===== DEBUG =====
    
    public void printDevices() {
        System.out.println("=== Discovered Devices ===");
        for (DeviceDescriptorWithCapabilities device : discoveredDevices.values()) {
            IODaemonProtocol.USBDeviceDescriptor usb = device.usbDevice;
            System.out.printf("  %s: %s (type=%s, claimed=%s)%n",
                usb.deviceId,
                usb.product != null ? usb.product : "Unknown",
                usb.get_device_type(),
                device.claimed);
            System.out.println("    Available modes: " + device.capabilities.getAvailableModes());
            System.out.println("    Capabilities: " + device.capabilities.getAvailableCapabilities());
        }
        System.out.println("==========================");
    }
}