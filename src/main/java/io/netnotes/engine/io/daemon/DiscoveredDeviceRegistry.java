package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.capabilities.DeviceCapabilitySet;
import io.netnotes.engine.io.capabilities.CapabilityRegistry.DefaultCapabilities;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.io.capabilities.CapabilityRegistry;
import io.netnotes.noteBytes.*;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.LoggingHelpers.Log;

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
    private final Map<NoteBytes, DeviceDescriptorWithCapabilities> discoveredDevices = 
        new ConcurrentHashMap<>();
    
    // Track claimed devices: deviceId → sourceId
    private final List<NoteBytes> claimedDevices = new CopyOnWriteArrayList<>();
    
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
     *   "event": TYPE_CMD,
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
            Log.logError("No items array in device list");
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
        
        Log.logMsg("Discovered " + discoveredDevices.size() + " devices");
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
            Log.logError("Failed to parse device: " + e.getMessage());
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
        desc.deviceId = deviceMap.get(Keys.DEVICE_ID);
        desc.vendorId = deviceMap.get(Keys.VENDOR_ID).getAsInt();
        desc.productId = deviceMap.get(Keys.PRODUCT_ID).getAsInt();
        desc.deviceClass = deviceMap.get(Keys.DEVICE_CLASS).getAsInt();
        desc.deviceSubClass = deviceMap.get(Keys.DEVICE_SUBCLASS).getAsInt();
        desc.deviceProtocol = deviceMap.get(Keys.DEVICE_PROTOCOL).getAsInt();
        
        desc.busNumber = deviceMap.get(Keys.BUS_NUMBER).getAsInt();
        desc.deviceAddress = deviceMap.get(Keys.DEVICE_ADDRESS).getAsInt();
        
        // Device type
        NoteBytesReadOnly typeBytes = deviceMap.getReadOnly(Keys.ITEM_TYPE);
        if (typeBytes != null) {
            desc.setDeviceType(typeBytes);
        }
        
        // Optional string fields
        NoteBytes mfg = deviceMap.get(Keys.MANUFACTURER);
        if (mfg != null) desc.manufacturer = mfg.getAsString();
        
        NoteBytes prod = deviceMap.get(Keys.PRODUCT);
        if (prod != null) desc.product = prod.getAsString();
        
        NoteBytes serial = deviceMap.get(Keys.SERIAL_NUMBER);
        if (serial != null) desc.serialNumber = serial.getAsString();
        
        // Status fields
        desc.available = deviceMap.get(Keys.AVAILABLE).getAsBoolean();
        desc.kernelDriverAttached = deviceMap.get(Keys.KERNEL_DRIVER_ATTACHED).getAsBoolean();
        
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
        NoteBytesReadOnly deviceType = usbDevice.getDeviceType();
        
        DeviceCapabilitySet caps = new DeviceCapabilitySet(deviceName, deviceType);
        
        // Get capability bits from daemon
        NoteBytes capsBytes = deviceMap.getOrDefault(AVAILABLE_CAPABILITIES, null);
        if (capsBytes == null) {
            Log.logError("No capabilities in device descriptor");
            return caps;
        }
        
        // Convert to BigInteger (cpp_int → BigInteger.toByteArray() format)
        BigInteger capabilityBits = capsBytes.getAsBigInteger();
        
        // Parse each bit into named capability
        for (int i = 0; i < capabilityBits.bitLength(); i++) {
            if (capabilityBits.testBit(i)) {
                NoteBytes capName = getCapabilityNameForBit(i);
                if (capName != null && CAPABILITY_REGISTRY.isRegistered(capName)) {
                    caps.addAvailableCapability(capName);
                } else {
                    Log.logMsg("Unknown capability bit: " + i);
                }
            }
        }
        
        // Also parse human-readable names if provided (for debugging)
        NoteBytes namesBytes = deviceMap.get("capability_names");
        if (namesBytes != null && namesBytes.getType() == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
            NoteBytesReadOnly[] names = namesBytes.getAsNoteBytesArrayReadOnly().getAsArray();
            Log.logMsg("Device capabilities: " + 
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
    private NoteBytes getCapabilityNameForBit(int bitPosition) {
        // Device types (0-7)
        return switch (bitPosition) {
            case 0 -> DefaultCapabilities.KEYBOARD;
            case 1 -> DefaultCapabilities.MOUSE;
            case 2 -> DefaultCapabilities.TOUCH;
            case 3 -> DefaultCapabilities.GAMEPAD;
            case 4 -> DefaultCapabilities.PEN;
            case 5 -> DefaultCapabilities.TOUCHPAD;
            case 6 -> DefaultCapabilities.SCROLL;
            
            // Modes (8-15)
            case 8 -> DefaultCapabilities.RAW_MODE;
            case 9 -> DefaultCapabilities.PARSED_MODE;
            case 10 -> DefaultCapabilities.PASSTHROUGH_MODE;
            case 11 -> DefaultCapabilities.FILTERED_MODE;
            
            // Coordinates (16-23)
            case 16 -> DefaultCapabilities.ABSOLUTE_COORDINATES;
            case 17 -> DefaultCapabilities.RELATIVE_COORDINATES;
            case 18 -> DefaultCapabilities.SCREEN_COORDINATES;
            case 19 -> DefaultCapabilities.NORMALIZED_COORDINATES;
            
            // Advanced features (24-31)
            case 24 -> DefaultCapabilities.HIGH_PRECISION;
            case 25 -> DefaultCapabilities.MULTIPLE_DEVICES;
            case 26 -> DefaultCapabilities.GLOBAL_CAPTURE;
            case 27 -> DefaultCapabilities.PROVIDES_SCANCODES;
            case 28 -> DefaultCapabilities.NANOSECOND_TIMESTAMPS;
            
            // Device detection (32-39)
            case 32 -> DefaultCapabilities.DEVICE_TYPE_KNOWN;
            case 33 -> DefaultCapabilities.HID_DEVICE;
            case 34 -> DefaultCapabilities.USB_DEVICE;
            case 35 -> DefaultCapabilities.BLUETOOTH_DEVICE;
            
            // State (40-47)
            case 40 -> DefaultCapabilities.ENCRYPTION_SUPPORTED;
            case 41 -> DefaultCapabilities.ENCRYPTION_ENABLED;
            case 42 -> DefaultCapabilities.BUFFERING_SUPPORTED;
            case 43 -> DefaultCapabilities.BUFFERING_ENABLED;
            
            // Lifecycle (48-55)
            case 48 -> DefaultCapabilities.SCENE_LOCATION;
            case 49 -> DefaultCapabilities.SCENE_SIZE;
            case 50 -> DefaultCapabilities.WINDOW_LIFECYCLE;
            case 51 -> DefaultCapabilities.STAGE_POSITION;
            case 52 -> DefaultCapabilities.STAGE_SIZE;
            case 53 -> DefaultCapabilities.STAGE_FOCUS;
            
            // Composite (56-63)
            case 56 -> DefaultCapabilities.COMPOSITE_SOURCE;
            case 57 -> DefaultCapabilities.MULTIPLE_CHILDREN;
            
            default -> null;
        };
    }
    
    // ===== QUERIES =====
    
    /**
     * Get device by deviceId
     */
    public DeviceDescriptorWithCapabilities getDevice(NoteBytes deviceId) {
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
    public boolean isClaimed(NoteBytes deviceId) {
        return claimedDevices.contains(deviceId);
    }
    

    
    
    // ===== CLAIM MANAGEMENT =====
    
    /**
     * Mark device as claimed with sourceId
     */
    public void markClaimed(NoteBytes deviceId) {
        DeviceDescriptorWithCapabilities device = discoveredDevices.get(deviceId);
        if (device != null) {
            discoveredDevices.put(deviceId, device.claim());
            claimedDevices.add(deviceId);
            Log.logMsg("Marked device " + deviceId + " as claimed");
        }
    }
    
    /**
     * Mark device as released (unclaimed)
     */
    public void markReleased(NoteBytes deviceId) {
        DeviceDescriptorWithCapabilities device = discoveredDevices.get(deviceId);
        if (device != null) {
            discoveredDevices.put(deviceId, new DeviceDescriptorWithCapabilities(
                device.usbDevice, device.capabilities, false
            ));
            claimedDevices.remove(deviceId);
            Log.logMsg("Marked device " + deviceId + " as released");
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
    public boolean validateModeCompatibility(NoteBytes deviceId, NoteBytes requestedMode) {
        DeviceDescriptorWithCapabilities device = discoveredDevices.get(deviceId);
        if (device == null) {
            return false;
        }
        
        DeviceCapabilitySet caps = device.capabilities;
        
        // Check if mode capability exists
        if (!caps.hasCapability(requestedMode)) {
            Log.logError("Device does not have capability: " + requestedMode);
            return false;
        }
        
        // Check if mode can be enabled (constraint validation)
        if (!caps.canEnable(requestedMode)) {
            Log.logError("Cannot enable mode " + requestedMode + ": " + 
                             caps.getEnableFailureReason(requestedMode));
            return false;
        }
        
        return true;
    }
    
    /**
     * Get available modes for device
     */
    public Set<NoteBytes> getAvailableModes(NoteBytes deviceId) {
        DeviceDescriptorWithCapabilities device = discoveredDevices.get(deviceId);
        if (device == null) {
            return Collections.emptySet();
        }
        
        return device.capabilities.getAvailableModes();
    }
    
    // ===== DEBUG =====
    
    public void printDevices() {
        Log.logMsg("=== Discovered Devices ===");
        for (DeviceDescriptorWithCapabilities device : discoveredDevices.values()) {
            IODaemonProtocol.USBDeviceDescriptor usb = device.usbDevice;
            System.out.printf("  %s: %s (type=%s, claimed=%s)%n",
                usb.deviceId,
                usb.product != null ? usb.product : "Unknown",
                usb.getDeviceType(),
                device.claimed);
            Log.logMsg("    Available modes: " + device.capabilities.getAvailableModes());
            Log.logMsg("    Capabilities: " + device.capabilities.getAvailableCapabilities());
        }
        Log.logMsg("==========================");
    }
}