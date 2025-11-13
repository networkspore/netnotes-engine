package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.events.EventBytes;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.utils.AtomicSequence;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IODaemon Protocol - Phased connection lifecycle
 * 
 * Protocol Phases:
 * ================
 * 
 * 1. HANDSHAKE
 *    - Client: HELLO
 *    - Server: ACCEPT + version info
 * 
 * 2. DISCOVERY
 *    - Client: REQUEST_DISCOVERY
 *    - Server: DEVICE_LIST (all available USB devices, detailed info)
 *    - Client can request detailed info: GET_DEVICE_INFO(device_id)
 * 
 * 3. CLAIM
 *    - Client: CLAIM_DEVICE(device_id, source_id, pid, mode)
 *    - Server: DEVICE_CLAIMED or ERROR
 *    - Server locks device to client PID
 * 
 * 4. CONFIGURE
 *    - Optional: ENABLE_ENCRYPTION
 *    - Optional: SET_MODE (raw, keyboard, mouse, etc.)
 *    - Optional: SET_FILTERS
 * 
 * 5. STREAMING
 *    - Server → Client: [sourceId:INTEGER][event:OBJECT/ENCRYPTED]
 *    - Continuous event stream
 *    - Client can send control commands
 * 
 * 6. SHUTDOWN
 *    - Client: RELEASE_DEVICE(source_id) or DISCONNECT
 *    - Server: Releases device, reattaches kernel driver
 */
public class IODaemonProtocol {
    
    // ===== PROTOCOL PHASES =====
    
    public enum Phase {
        HANDSHAKE,
        DISCOVERY,
        CLAIM,
        CONFIGURE,
        STREAMING,
        SHUTDOWN
    }
    
    // ===== DEVICE MODES =====
    
    public enum DeviceMode {
        RAW,           // Raw HID reports
        KEYBOARD,      // Parsed keyboard events
        MOUSE,         // Parsed mouse events
        GAMEPAD,       // Parsed gamepad events
        CUSTOM         // Custom protocol
    }
    
    // ===== COMMAND CONSTANTS =====
    
    public static class Commands {
        // Handshake
        public static final String HELLO = "hello";
        public static final String ACCEPT = "accept";
        
        // Discovery
        public static final String REQUEST_DISCOVERY = "request_discovery";
        public static final String DEVICE_LIST = "device_list";
        public static final String GET_DEVICE_INFO = "get_device_info";
        public static final String DEVICE_INFO = "device_info";
        
        // Claim
        public static final String CLAIM_DEVICE = "claim_device";
        public static final String DEVICE_CLAIMED = "device_claimed";
        public static final String RELEASE_DEVICE = "release_device";
        public static final String DEVICE_RELEASED = "device_released";
        
        // Configure
        public static final String ENABLE_ENCRYPTION = "enable_encryption";
        public static final String SET_MODE = "set_mode";
        public static final String SET_FILTER = "set_filter";
        
        // Control
        public static final String PAUSE_DEVICE = "pause_device";
        public static final String RESUME_DEVICE = "resume_device";
        public static final String DISCONNECT = "disconnect";
        
        // Errors
        public static final String ERROR = "error";
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
        public int usbVersion;          // e.g., 0x0200 for USB 2.0
        public int maxPacketSize;
        
        // Daemon-assigned identifier
        public String deviceId;         // Unique ID for this session
        
        // Device state
        public boolean available;       // Can be claimed
        public boolean kernelDriverAttached;
        
        /**
         * Convert to NoteBytesObject for transmission
         */
        public NoteBytesObject toNoteBytesObject() {
            NoteBytesObject obj = new NoteBytesObject();
            
            obj.add("device_id", deviceId);
            obj.add("vendor_id", vendorId);
            obj.add("product_id", productId);
            obj.add("device_class", deviceClass);
            obj.add("device_subclass", deviceSubClass);
            obj.add("device_protocol", deviceProtocol);
            
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
            
            desc.deviceId = map.get("device_id").getAsString();
            desc.vendorId = map.get("vendor_id").getAsInt();
            desc.productId = map.get("product_id").getAsInt();
            desc.deviceClass = map.get("device_class").getAsInt();
            desc.deviceSubClass = map.get("device_subclass").getAsInt();
            desc.deviceProtocol = map.get("device_protocol").getAsInt();
            
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
        
        // HID-specific
        public boolean isHID;
        public int hidReportDescriptorLength;
        
        // Endpoints
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
            
            // Add endpoints
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
            
            // Parse endpoints
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
        public int direction;        // 0=OUT, 1=IN
        public int transferType;     // 0=control, 1=iso, 2=bulk, 3=interrupt
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
    
    /**
     * Thread-safe NoteBytesWriter using semaphore for synchronization
    */
    public static class AsyncNoteBytesWriter {
        private final NoteBytesWriter writer;
      //  private final Semaphore writeLock = new Semaphore(1);
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

        
        /**
         * Write NoteBytesObject asynchronously
         */
        public CompletableFuture<Void> writeAsync(NoteBytesObject obj) {
            WriteRequest request = new WriteRequest(obj);
            writeQueue.offer(request);
            return request.future;
        }
        
        /**
         * Write NoteBytes asynchronously
         */
        public CompletableFuture<Void> writeAsync(NoteBytes bytes) {
            WriteRequest request = new WriteRequest(bytes);
            writeQueue.offer(request);
            return request.future;
        }
        
        /**
         * Write synchronously (blocks until complete)
         */
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
                    writer.writeObject((NoteBytesObject) data);
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
    
    /**
     * Build protocol messages
     */
    public static class MessageBuilder {

        /**
         * Create command message
         */
        public static NoteBytesObject createCommand(String command, NoteBytesPair... params) {
            NoteBytesObject msg = new NoteBytesObject();
            msg.add(Keys.TYPE_KEY, EventBytes.TYPE_CMD);
            msg.add(Keys.SEQUENCE_KEY, generateSequence());
            msg.add(Keys.CMD_KEY, command);
            
            for (NoteBytesPair param : params) {
                msg.add(param.getKey(), param.getValue());
            }
            
            return msg;
        }
        
        /**
         * Create error message
         */
        public static NoteBytesObject createError(int errorCode, String message) {
            NoteBytesObject msg = new NoteBytesObject();
            msg.add(Keys.TYPE_KEY, EventBytes.TYPE_ERROR);
            msg.add(Keys.SEQUENCE_KEY, generateSequence());
            msg.add(Keys.ERROR_KEY, errorCode);
            msg.add(Keys.MSG_KEY, message);
            return msg;
        }
        
        /**
         * Create accept message
         */
        public static NoteBytesObject createAccept(String status) {
            NoteBytesObject msg = new NoteBytesObject();
            msg.add(Keys.TYPE_KEY, EventBytes.TYPE_ACCEPT);
            msg.add(Keys.SEQUENCE_KEY, generateSequence());
            msg.add(Keys.STATUS_KEY, status);
            return msg;
        }
        
        /**
         * Generate 6-byte sequence
         */
        public static NoteBytes generateSequence() {
            return AtomicSequence.getNextSequenceReadOnly();
        }
    }
}