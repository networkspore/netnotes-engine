package io.netnotes.engine.core.system.control.containers;

import java.util.Set;

import io.netnotes.engine.core.system.control.ui.UIRenderer;

public class RendererInfo {
    private final UIRenderer renderer;
    private final Set<ContainerType> supportedTypes;
    private final String description;
    
 
    public RendererInfo(UIRenderer renderer, Set<ContainerType> supportedTypes, String description) {
        this.renderer = renderer;
        this.supportedTypes = supportedTypes;
        this.description = description;
    }
    
    public boolean supports(ContainerType type) {
        return supportedTypes.isEmpty() || supportedTypes.contains(type);
    }

    public UIRenderer getRenderer() {
        return renderer;
    }

    public Set<ContainerType> getSupportedTypes() {
        return supportedTypes;
    }

    public String getDescription() {
        return description;
    }

}