package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.messaging.NoteMessaging.ItemTypes;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.Modes;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.utils.AtomicSequence;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * IODaemon Protocol - Updated with string-based device types
 */
public class IODaemonProtocol {
    private static final String UNKNOWN = ItemTypes.UNKNOWN.getAsString();
    // ===== PROTOCOL PHASES =====
    
    public enum Phase {
        HANDSHAKE,
        DISCOVERY,
        CLAIM,
        CONFIGURE,
        STREAMING,
        SHUTDOWN
    }
    
    // ===== DEVICE TYPE (Hardware identification) =====
    
    /**
     * Device Type - What the hardware is (detected from USB descriptors)
     */
    public static class DeviceType {
        
        /**
         * Validate device type string
         */
        public static boolean isValid(NoteBytesReadOnly type) {
            return type != null && (
                type.equals(ItemTypes.KEYBOARD) ||
                type.equals(ItemTypes.MOUSE) ||
                type.equals(ItemTypes.GAMEPAD) ||
                type.equals(ItemTypes.TOUCHPAD) ||
                type.equals(ItemTypes.UNKNOWN)
            );
        }
        
        /**
         * Get default type for unknown devices
         */
        public static NoteBytesReadOnly getDefault() {
            return ItemTypes.UNKNOWN;
        }
    }
    
    // ===== DEVICE MODE (User preference for how to use device) =====
    
    /**
     * Device Mode - How the user wants to interact with the device
     */
    public static class DeviceMode {

        /**
         * Validate device mode string
         */
        public static boolean isValid(NoteBytesReadOnly mode) {
            return mode != null && (
                mode.equals(Modes.RAW) ||
                mode.equals(Modes.PARSED) ||
                mode.equals(Modes.PASSTHROUGH) ||
                mode.equals(Modes.FILTERED)
            );
        }
        
        /**
         * Get default mode
         */
        public static NoteBytesReadOnly getDefault() {
            return Modes.PARSED;
        }
        
        /**
         * Check if mode is compatible with device type
         */
        public static boolean isCompatible(NoteBytesReadOnly deviceType, NoteBytesReadOnly mode) {
            // Raw mode works with everything
            if (mode.equals(Modes.RAW)) {
                return true;
            }
            
            // Parsed mode requires known device type
            if (mode.equals(Modes.PARSED)) {
                return !deviceType.equals(ItemTypes.UNKNOWN);
            }
            
            return true;
        }
    }
    
    // ===== COMMAND CONSTANTS =====
    
    public static class Commands {
        // Handshake
        //public static final String HELLO = ProtocolMesssages.HELLO;
        //public static final String ACCEPT = "accept";
        
        // Discovery
        public static final NoteBytesReadOnly DEVICE_LIST = new NoteBytesReadOnly("device_list");
        public static final NoteBytesReadOnly GET_DEVICE_INFO = new NoteBytesReadOnly("get_device_info");
        public static final NoteBytesReadOnly DEVICE_INFO = new NoteBytesReadOnly("device_info");
        
        // Claim
        public static final NoteBytesReadOnly CLAIM_DEVICE = new NoteBytesReadOnly("claim_device");
        public static final NoteBytesReadOnly DEVICE_CLAIMED = new NoteBytesReadOnly("device_claimed");
        public static final NoteBytesReadOnly RELEASE_DEVICE = new NoteBytesReadOnly("release_device");
        public static final NoteBytesReadOnly DEVICE_RELEASED = new NoteBytesReadOnly("device_released");
        
        // Control
        public static final NoteBytesReadOnly PAUSE_DEVICE = new NoteBytesReadOnly("pause_device");
        public static final NoteBytesReadOnly RESUME_DEVICE = new NoteBytesReadOnly("resume_device");
        
        // Errors
    }
    
    // ===== USB DEVICE DESCRIPTOR =====
    
    /**
     * Complete USB device information for discovery
     */
    public static class USBDeviceDescriptor {
        // USB identifiers
        public int vendorId;
        public int productId;
        public int deviceClass;
        public int deviceSubClass;
        public int deviceProtocol;
        
        // Interface information
        public List<USBInterface> interfaces = new ArrayList<>();
        
        // Device strings
        public String manufacturer;
        public String product;
        public String serialNumber;
        
        // Device capabilities
        public int busNumber;
        public int deviceAddress;
        public int usbVersion;
        public int maxPacketSize;
        
        // Daemon-assigned identifier
        public String deviceId;
        
        // Device type (string-based)
        private String deviceType;
        
        // Device state
        public boolean available;
        public boolean kernelDriverAttached;
        
        /**
         * Get device type (auto-detect if not set)
         */
        public String get_device_type() {
            if (deviceType != null) {
                return deviceType;
            }
            
            // Auto-detect from USB class codes
            deviceType = detectDeviceType();
            return deviceType;
        }
       
        /**
         * Set device type explicitly
         */
        public void set_device_type(NoteBytesReadOnly type) {
            if (DeviceType.isValid(type)) {
                this.deviceType = type.getAsString();
            } else {
                this.deviceType = UNKNOWN;
            }
        }
        
        /**
         * Detect device type from USB descriptors
         */
        private String detectDeviceType() {
            // Check interfaces for HID devices
            return detectDeviceTypeBytes().getAsString();
        }

         private NoteBytesReadOnly detectDeviceTypeBytes() {
            // Check interfaces for HID devices
            for (USBInterface iface : interfaces) {
                if (iface.interfaceClass == 3) { // HID class
                    if (iface.interfaceProtocol == 1) {
                        return ItemTypes.KEYBOARD;
                    } else if (iface.interfaceProtocol == 2) {
                        return ItemTypes.MOUSE;
                    }
                }
            }
            
            // Check device class
            if (deviceClass == 3) { // HID
                return ItemTypes.UNKNOWN; // Generic HID, type unclear
            }
            
            return ItemTypes.UNKNOWN;
        }
        
        /**
         * Convert to NoteBytesObject for transmission
         */
        public NoteBytesObject toNoteBytesObject() {
            NoteBytesObject obj = new NoteBytesObject();
            
            obj.add(Keys.DEVICE_ID, deviceId);
            obj.add("vendor_id", vendorId);
            obj.add("product_id", productId);
            obj.add("device_class", deviceClass);
            obj.add("device_subclass", deviceSubClass);
            obj.add("device_protocol", deviceProtocol);
            obj.add("device_type", get_device_type()); // String type
            
            obj.add("bus_number", busNumber);
            obj.add("device_address", deviceAddress);
            obj.add("usb_version", usbVersion);
            obj.add("max_packet_size", maxPacketSize);
            
            if (manufacturer != null) obj.add("manufacturer", manufacturer);
            if (product != null) obj.add("product", product);
            if (serialNumber != null) obj.add("serial_number", serialNumber);
            
            obj.add("available", available);
            obj.add("kernel_driver_attached", kernelDriverAttached);
            
            // Add interfaces
            NoteBytesArray interfacesArray = new NoteBytesArray();
            for (USBInterface iface : interfaces) {
                interfacesArray.add(iface.toNoteBytesObject());
            }
            obj.add("interfaces", interfacesArray);
            
            return obj;
        }
        
        /**
         * Parse from NoteBytesObject
         */
        public static USBDeviceDescriptor fromNoteBytesObject(NoteBytesObject obj) {
            USBDeviceDescriptor desc = new USBDeviceDescriptor();
            
            NoteBytesMap map = obj.getAsNoteBytesMap();
            
            desc.deviceId = map.getReadOnly(Keys.DEVICE_ID).getAsString();
            desc.vendorId = map.get("vendor_id").getAsInt();
            desc.productId = map.get("product_id").getAsInt();
            desc.deviceClass = map.get("device_class").getAsInt();
            desc.deviceSubClass = map.get("device_subclass").getAsInt();
            desc.deviceProtocol = map.get("device_protocol").getAsInt();
            
            // Get device type as string
            NoteBytesReadOnly typeBytes = map.getReadOnly(ProtocolMesssages.ITEM_TYPE);
            if (typeBytes != null) {
                desc.set_device_type(typeBytes);
            }
            
            desc.busNumber = map.get("bus_number").getAsInt();
            desc.deviceAddress = map.get("device_address").getAsInt();
            desc.usbVersion = map.get("usb_version").getAsInt();
            desc.maxPacketSize = map.get("max_packet_size").getAsInt();
            
            NoteBytes mfg = map.get("manufacturer");
            if (mfg != null) desc.manufacturer = mfg.getAsString();
            
            NoteBytes prod = map.get("product");
            if (prod != null) desc.product = prod.getAsString();
            
            NoteBytes serial = map.get("serial_number");
            if (serial != null) desc.serialNumber = serial.getAsString();
            
            desc.available = map.get("available").getAsBoolean();
            desc.kernelDriverAttached = map.get("kernel_driver_attached").getAsBoolean();
            
            // Parse interfaces
            NoteBytes ifaces = map.get("interfaces");
            if (ifaces != null) {
                NoteBytesArray ifacesArray = ifaces.getAsNoteBytesArray();
                for (NoteBytes ifaceBytes : ifacesArray.getAsArray()) {
                    desc.interfaces.add(USBInterface.fromNoteBytesObject(
                        ifaceBytes.getAsNoteBytesObject()));
                }
            }
            
            return desc;
        }
    }
    
    /**
     * USB Interface descriptor
     */
    public static class USBInterface {
        public int interfaceNumber;
        public int interfaceClass;
        public int interfaceSubClass;
        public int interfaceProtocol;
        public int numEndpoints;
        
        public boolean isHID;
        public int hidReportDescriptorLength;
        
        public List<USBEndpoint> endpoints = new ArrayList<>();
        
        public NoteBytesObject toNoteBytesObject() {
            NoteBytesObject obj = new NoteBytesObject();
            
            obj.add("interface_number", interfaceNumber);
            obj.add("interface_class", interfaceClass);
            obj.add("interface_subclass", interfaceSubClass);
            obj.add("interface_protocol", interfaceProtocol);
            obj.add("num_endpoints", numEndpoints);
            obj.add("is_hid", isHID);
            
            if (isHID) {
                obj.add("hid_report_descriptor_length", hidReportDescriptorLength);
            }
            
            NoteBytesArray endpointsArray = new NoteBytesArray();
            for (USBEndpoint ep : endpoints) {
                endpointsArray.add(ep.toNoteBytesObject());
            }
            obj.add("endpoints", endpointsArray);
            
            return obj;
        }
        
        public static USBInterface fromNoteBytesObject(NoteBytesObject obj) {
            USBInterface iface = new USBInterface();
            NoteBytesMap map = obj.getAsNoteBytesMap();
            
            iface.interfaceNumber = map.get("interface_number").getAsInt();
            iface.interfaceClass = map.get("interface_class").getAsInt();
            iface.interfaceSubClass = map.get("interface_subclass").getAsInt();
            iface.interfaceProtocol = map.get("interface_protocol").getAsInt();
            iface.numEndpoints = map.get("num_endpoints").getAsInt();
            iface.isHID = map.get("is_hid").getAsBoolean();
            
            if (iface.isHID) {
                iface.hidReportDescriptorLength = 
                    map.get("hid_report_descriptor_length").getAsInt();
            }
            
            NoteBytes eps = map.get("endpoints");
            if (eps != null) {
                NoteBytesArray epsArray = eps.getAsNoteBytesArray();
                for (NoteBytes epBytes : epsArray.getAsArray()) {
                    iface.endpoints.add(USBEndpoint.fromNoteBytesObject(
                        epBytes.getAsNoteBytesObject()));
                }
            }
            
            return iface;
        }
    }
    
    /**
     * USB Endpoint descriptor
     */
    public static class USBEndpoint {
        public int endpointAddress;
        public int direction;
        public int transferType;
        public int maxPacketSize;
        public int interval;
        
        public NoteBytesObject toNoteBytesObject() {
            NoteBytesObject obj = new NoteBytesObject();
            obj.add("endpoint_address", endpointAddress);
            obj.add("direction", direction);
            obj.add("transfer_type", transferType);
            obj.add("max_packet_size", maxPacketSize);
            obj.add("interval", interval);
            return obj;
        }
        
        public static USBEndpoint fromNoteBytesObject(NoteBytesObject obj) {
            USBEndpoint ep = new USBEndpoint();
            NoteBytesMap map = obj.getAsNoteBytesMap();
            
            ep.endpointAddress = map.get("endpoint_address").getAsInt();
            ep.direction = map.get("direction").getAsInt();
            ep.transferType = map.get("transfer_type").getAsInt();
            ep.maxPacketSize = map.get("max_packet_size").getAsInt();
            ep.interval = map.get("interval").getAsInt();
            
            return ep;
        }
    }
    
    // ===== ASYNC NOTEBYTES WRITER =====
    
    public static class AsyncNoteBytesWriter {
        private final NoteBytesWriter writer;
        private final BlockingQueue<WriteRequest> writeQueue = new LinkedBlockingQueue<>();
        private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(
            r -> Thread.ofVirtual().name("AsyncWriter").unstarted(r)
        );
        private volatile boolean running = true;
        
        public AsyncNoteBytesWriter(OutputStream out) {
            this.writer = new NoteBytesWriter(out);
            startWriteLoop();
        }
        
        private void startWriteLoop() {
            writeExecutor.submit(() -> {
                try {
                    while (running || !writeQueue.isEmpty()) {
                        WriteRequest request = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (request != null) {
                            try {
                                request.write(writer);
                                request.complete();
                            } catch (IOException e) {
                                request.completeExceptionally(e);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        public CompletableFuture<Void> writeAsync(NoteBytesObject obj) {
            WriteRequest request = new WriteRequest(obj);
            writeQueue.offer(request);
            return request.future;
        }
        
        public CompletableFuture<Void> writeAsync(NoteBytes bytes) {
            WriteRequest request = new WriteRequest(bytes);
            writeQueue.offer(request);
            return request.future;
        }
        
        public void writeSync(NoteBytesObject obj) throws IOException {
            try {
                writeAsync(obj).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Write interrupted", e);
            } catch (ExecutionException e) {
                throw new IOException("Write failed", e.getCause());
            }
        }
        
        public void shutdown() {
            running = false;
            writeExecutor.shutdown();
        }
        
        private static class WriteRequest {
            private final Object data;
            private final CompletableFuture<Void> future = new CompletableFuture<>();
            
            WriteRequest(Object data) {
                this.data = data;
            }
            
            void write(NoteBytesWriter writer) throws IOException {
                if (data instanceof NoteBytesObject) {
                    writer.write((NoteBytesObject) data);
                } else if (data instanceof NoteBytes) {
                    writer.write((NoteBytes) data);
                }
            }
            
            void complete() {
                future.complete(null);
            }
            
            void completeExceptionally(Throwable t) {
                future.completeExceptionally(t);
            }
        }
    }
    
    // ===== PROTOCOL MESSAGE BUILDERS =====
    
    public static class MessageBuilder {
        public static NoteBytesObject createCommand(NoteBytesReadOnly command, NoteBytesPair... params) {
            NoteBytesObject msg = new NoteBytesObject();
            msg.add(Keys.TYPE, EventBytes.TYPE_CMD);
            msg.add(Keys.SEQUENCE, AtomicSequence.getNextSequenceLong());
            msg.add(Keys.CMD, command);
            
            for (NoteBytesPair param : params) {
                msg.add(param.getKey(), param.getValue());
            }
            
            return msg;
        }
        
        public static NoteBytesObject createError(int errorCode, String message) {
            NoteBytesObject msg = new NoteBytesObject();
            msg.add(Keys.TYPE, EventBytes.TYPE_ERROR);
            msg.add(Keys.SEQUENCE, AtomicSequence.getNextSequenceLong());
            msg.add(Keys.ERROR_CODE, errorCode);
            msg.add(Keys.MSG, message);
            return msg;
        }
        
        public static NoteBytesObject createAccept(String status) {
            NoteBytesObject msg = new NoteBytesObject();
            msg.add(Keys.TYPE, EventBytes.TYPE_ACCEPT);
            msg.add(Keys.SEQUENCE, AtomicSequence.getNextSequenceLong());
            msg.add(Keys.STATUS, status);
            return msg;
        }
        

    }
}