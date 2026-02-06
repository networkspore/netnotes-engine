package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.core.system.control.containers.DeviceManager;

public abstract class TerminalDeviceManager extends DeviceManager
<
    TerminalContainerHandle,
    TerminalDeviceManager
>{

    protected TerminalDeviceManager(String deviceId, String mode, String deviceType) {
        super(deviceId, mode, deviceType);
    }

   
    
}
