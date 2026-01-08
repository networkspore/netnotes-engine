package io.netnotes.engine.core.system.control.terminal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netnotes.engine.core.system.control.containers.ContainerId;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

public class ClientTerminalRenderManager extends FlowProcess {

    private final ConcurrentHashMap<ContainerId, TerminalContainerHandle> handles = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<TerminalContainerHandle, RenderSlot> slots =
        new ConcurrentHashMap<>();

    private final ContextPath renderingServicePath;

    private final Executor executor = VirtualExecutors.getVirtualExecutor();

    public ClientTerminalRenderManager(String name, ContextPath renderingServicePath){
        super(name, ProcessType.BIDIRECTIONAL);
        this.renderingServicePath = renderingServicePath;
    }

    public TerminalContainerHandle.TerminalBuilder createTerminal(String name) {
        
        // Override build to register with manager
        return new TerminalContainerHandle.TerminalBuilder(name) {
     
            public TerminalContainerHandle build() {
                TerminalContainerHandle handle = super.build();
                
                // Register as child of manager
                registerChild(handle);
                
                // Track handle
                handles.put(handle.getId(), handle);
                
                // Give handle a way to request rendering
                handle.setOnRenderRequest(ClientTerminalRenderManager.this::requestRender);
                
                Log.logMsg(String.format(
                    "[ClientRenderManager] Created handle: %s at %s",
                    handle.getId(), handle.getContextPath()
                ));
                
                // Start handle automatically
                startProcess(handle.getContextPath())
                    .exceptionally(ex -> {
                        Log.logError("[ClientRenderManager] Failed to start handle: " + 
                            ex.getMessage());
                        return null;
                    });
                
                return handle;
            }
        }.renderingService(renderingServicePath)
         .name(name);
    } 

    public void requestRender(TerminalContainerHandle handle) {
        RenderSlot slot = slots.computeIfAbsent(handle, RenderSlot::new);
        slot.request();
    }

    private final class RenderSlot {

        private final TerminalContainerHandle handle;

        private final AtomicBoolean renderRequested = new AtomicBoolean(false);
        private final AtomicBoolean rendering = new AtomicBoolean(false);

        RenderSlot(TerminalContainerHandle handle) {
            this.handle = handle;
        }

        void request() {
            renderRequested.set(true);
            trySchedule();
        }

        void trySchedule() {
            if (!renderRequested.get()) return;
            if (rendering.get()) return;

            executor.execute(this::run);
        }

        void run() {
            if (!renderRequested.compareAndSet(true, false)) {
                return;
            }


            if (handle.isReadyToRender()) {
                // Not ready yet — request again later
                renderRequested.set(true);
                return;
            }

            rendering.set(true);

            handle.render()
                .whenComplete((v, ex) -> {
                    rendering.set(false);

                    if (ex != null) {
                        Log.logError("[RenderManager] Render failed: " + ex.getMessage());
                    }

                    // If another request came in while rendering, run again
                    if (renderRequested.get()) {
                        trySchedule();
                    }
                });
        }

        void cancel() {
            renderRequested.set(false);
        }
    }

    /***
     * Required error lets the ProcessService know that this process does not support stream channels
     */
	@Override
	public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
		throw new UnsupportedOperationException("Unimplemented method 'handleStreamChannel'");
	}
}
