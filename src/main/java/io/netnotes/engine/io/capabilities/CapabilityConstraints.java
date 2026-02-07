package io.netnotes.engine.io.capabilities;

import io.netnotes.noteBytes.*;
import io.netnotes.noteBytes.collections.NoteBytesMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CapabilityConstraints - Manages mutual exclusions and requirements between capabilities
 * 
 * Design:
 * - Mutual exclusions: capability A excludes capability B (bidirectional)
 * - Requirements: capability A requires capability B (must be available and enabled)
 * - Thread-safe using ConcurrentHashMap
 * - Serializable to NoteBytes for protocol transmission
 */
public class CapabilityConstraints {
    
    // Mutual exclusions: capability -> set of mutually exclusive capabilities
    private final Map<String, Set<String>> mutualExclusions = new ConcurrentHashMap<>();
    
    // Requirements: capability -> set of required capabilities
    private final Map<String, Set<String>> requirements = new ConcurrentHashMap<>();
    
    public CapabilityConstraints() {}
    
    // ===== MUTUAL EXCLUSIONS =====
    
    /**
     * Add mutual exclusion constraint (bidirectional)
     * When capability1 is enabled, capability2 must be disabled and vice versa
     */
    public void addMutualExclusion(String capability1, String capability2) {
        mutualExclusions.computeIfAbsent(capability1, k -> ConcurrentHashMap.newKeySet())
                       .add(capability2);
        mutualExclusions.computeIfAbsent(capability2, k -> ConcurrentHashMap.newKeySet())
                       .add(capability1);
    }
    
    /**
     * Check if two capabilities are mutually exclusive
     */
    public boolean areMutuallyExclusive(String capability1, String capability2) {
        Set<String> exclusions = mutualExclusions.get(capability1);
        return exclusions != null && exclusions.contains(capability2);
    }
    
    /**
     * Get all capabilities that are mutually exclusive with given capability
     */
    public Set<String> getExclusions(String capability) {
        Set<String> exclusions = mutualExclusions.get(capability);
        return exclusions != null ? new HashSet<>(exclusions) : Collections.emptySet();
    }
    
    // ===== REQUIREMENTS =====
    
    /**
     * Add requirement constraint
     * capability requires requiredCapability to be available and enabled
     */
    public void addRequirement(String capability, String requiredCapability) {
        requirements.computeIfAbsent(capability, k -> ConcurrentHashMap.newKeySet())
                   .add(requiredCapability);
    }
    
    /**
     * Get all capabilities required by given capability
     */
    public Set<String> getRequirements(String capability) {
        Set<String> reqs = requirements.get(capability);
        return reqs != null ? new HashSet<>(reqs) : Collections.emptySet();
    }
    
    /**
     * Check if capability has required dependencies
     */
    public boolean hasRequirements(String capability) {
        Set<String> reqs = requirements.get(capability);
        return reqs != null && !reqs.isEmpty();
    }
    
    // ===== VALIDATION =====
    
    /**
     * Check if capability can be enabled given current capability set
     */
    public boolean canEnable(String capability, DeviceCapabilitySet capabilitySet) {
        // Check if available
        if (!capabilitySet.hasCapability(capability)) {
            return false;
        }
        
        // Check mutual exclusions
        Set<String> exclusions = getExclusions(capability);
        for (String excluded : exclusions) {
            if (capabilitySet.isEnabled(excluded)) {
                return false; // Mutually exclusive capability is enabled
            }
        }
        
        // Check requirements
        Set<String> reqs = getRequirements(capability);
        for (String required : reqs) {
            if (!capabilitySet.hasCapability(required) || !capabilitySet.isEnabled(required)) {
                return false; // Required capability not available or not enabled
            }
        }
        
        return true;
    }
    
    /**
     * Get reason why capability cannot be enabled
     */
    public String getFailureReason(String capability, DeviceCapabilitySet capabilitySet) {
        if (!capabilitySet.hasCapability(capability)) {
            return "Capability not available on device";
        }
        
        // Check mutual exclusions
        Set<String> exclusions = getExclusions(capability);
        for (String excluded : exclusions) {
            if (capabilitySet.isEnabled(excluded)) {
                return "Mutually exclusive with enabled capability: " + 
                       DeviceCapabilitySet.getUserFriendlyName(excluded);
            }
        }
        
        // Check requirements
        Set<String> reqs = getRequirements(capability);
        for (String required : reqs) {
            if (!capabilitySet.hasCapability(required)) {
                return "Requires unavailable capability: " + 
                       DeviceCapabilitySet.getUserFriendlyName(required);
            }
            if (!capabilitySet.isEnabled(required)) {
                return "Requires capability to be enabled: " + 
                       DeviceCapabilitySet.getUserFriendlyName(required);
            }
        }
        
        return "Unknown constraint violation";
    }
    
    /**
     * Validate entire capability set for consistency
     */
    public List<String> validateCapabilitySet(DeviceCapabilitySet capabilitySet) {
        List<String> violations = new ArrayList<>();
        
        Set<String> enabled = capabilitySet.getEnabledCapabilities();
        
        // Check each enabled capability
        for (String capability : enabled) {
            // Check mutual exclusions
            Set<String> exclusions = getExclusions(capability);
            for (String excluded : exclusions) {
                if (enabled.contains(excluded)) {
                    violations.add(String.format(
                        "Mutual exclusion violation: '%s' and '%s' cannot both be enabled",
                        capability, excluded));
                }
            }
            
            // Check requirements
            Set<String> reqs = getRequirements(capability);
            for (String required : reqs) {
                if (!enabled.contains(required)) {
                    violations.add(String.format(
                        "Requirement violation: '%s' requires '%s' to be enabled",
                        capability, required));
                }
            }
        }
        
        return violations;
    }
    
    // ===== SERIALIZATION =====
    
    /**
     * Serialize to NoteBytesObject
     */
    public NoteBytesObject toNoteBytes() {
        NoteBytesObject obj = new NoteBytesObject();
        
        // Serialize mutual exclusions
        NoteBytesObject exclusionsObj = new NoteBytesObject();
        for (Map.Entry<String, Set<String>> entry : mutualExclusions.entrySet()) {
            NoteBytesArray exclusionArray = new NoteBytesArray();
            for (String excluded : entry.getValue()) {
                exclusionArray.add(new NoteBytesReadOnly(excluded));
            }
            exclusionsObj.add(entry.getKey(), exclusionArray);
        }
        obj.add("mutual_exclusions", exclusionsObj);
        
        // Serialize requirements
        NoteBytesObject requirementsObj = new NoteBytesObject();
        for (Map.Entry<String, Set<String>> entry : requirements.entrySet()) {
            NoteBytesArray reqArray = new NoteBytesArray();
            for (String required : entry.getValue()) {
                reqArray.add(new NoteBytesReadOnly(required));
            }
            requirementsObj.add(entry.getKey(), reqArray);
        }
        obj.add("requirements", requirementsObj);
        
        return obj;
    }
    
    /**
     * Deserialize from NoteBytesObject
     */
    public void fromNoteBytes(NoteBytesObject obj) {
        NoteBytesMap map = obj.getAsNoteBytesMap();
        
        // Restore mutual exclusions
        NoteBytes exclusionsBytes = map.get("mutual_exclusions");
        if (exclusionsBytes != null) {
            NoteBytesMap exclusionsMap = exclusionsBytes.getAsNoteBytesObject().getAsNoteBytesMap();
            for (NoteBytes key : exclusionsMap.keySet()) {
                NoteBytes arrayBytes = exclusionsMap.get(key);
                if (arrayBytes != null) {
                    NoteBytesArray array = arrayBytes.getAsNoteBytesArray();
                    for (NoteBytes item : array.getAsArray()) {
                        String excluded = item.getAsString();
                        mutualExclusions.computeIfAbsent(key.getAsString(), k -> ConcurrentHashMap.newKeySet())
                                       .add(excluded);
                    }
                }
            }
        }
        
        // Restore requirements
        NoteBytes requirementsBytes = map.get("requirements");
        if (requirementsBytes != null) {
            NoteBytesMap requirementsMap = requirementsBytes.getAsNoteBytesObject().getAsNoteBytesMap();
            for (NoteBytes key : requirementsMap.keySet()) {
                NoteBytes arrayBytes = requirementsMap.get(key);
                if (arrayBytes != null) {
                    NoteBytesArray array = arrayBytes.getAsNoteBytesArray();
                    for (NoteBytes item : array.getAsArray()) {
                        String required = item.getAsString();
                        requirements.computeIfAbsent(key.getAsString(), k -> ConcurrentHashMap.newKeySet())
                                   .add(required);
                    }
                }
            }
        }
    }
    
    // ===== UTILITY =====
    
    /**
     * Clear all constraints
     */
    public void clear() {
        mutualExclusions.clear();
        requirements.clear();
    }
    
    /**
     * Check if any constraints exist
     */
    public boolean isEmpty() {
        return mutualExclusions.isEmpty() && requirements.isEmpty();
    }
    
    /**
     * Get total constraint count
     */
    public int getConstraintCount() {
        int count = 0;
        for (Set<String> exclusions : mutualExclusions.values()) {
            count += exclusions.size();
        }
        for (Set<String> reqs : requirements.values()) {
            count += reqs.size();
        }
        return count / 2; // Mutual exclusions are bidirectional
    }
    
    @Override
    public String toString() {
        return String.format("CapabilityConstraints{exclusions=%d, requirements=%d}",
            mutualExclusions.size(), requirements.size());
    }
}