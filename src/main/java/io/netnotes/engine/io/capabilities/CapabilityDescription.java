package io.netnotes.engine.io.capabilities;

import java.util.List;
import java.util.Set;

import io.netnotes.noteBytes.NoteBytes;

/**
 * User-friendly capability description
 */
public record CapabilityDescription(
    String name,
    NoteBytes deviceType,
    Set<NoteBytes> availableCapabilities,
    Set<NoteBytes> enabledCapabilities,
    Set<NoteBytes> availableModes,
    NoteBytes enabledMode,
    List<CapabilityDescription> children
) {
    public String toReadableString() {
    
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" (").append(deviceType).append(")\n");
        sb.append("  Available: ").append(availableCapabilities).append("\n");
        sb.append("  Enabled: ").append(enabledCapabilities).append("\n");
        if (enabledMode != null) {
            sb.append("  Mode: ").append(enabledMode).append("\n");
        }
        if (!children.isEmpty()) {
            sb.append("  Children:\n");
            for (CapabilityDescription child : children) {
                sb.append("    ").append(child.toReadableString().replace("\n", "\n    "));
            }
        }
        return sb.toString();
    }
}