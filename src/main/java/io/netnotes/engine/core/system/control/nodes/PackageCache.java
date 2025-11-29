package io.netnotes.engine.core.system.control.nodes;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * PackageCache - In-memory cache of available packages
 * Built from repository updates (like apt-cache)
 */
public class PackageCache {
    private final ConcurrentHashMap<NoteBytesReadOnly, PackageInfo> cache;
    
    public PackageCache() {
        this.cache = new ConcurrentHashMap<>();
    }
    
    public void updateCache(List<PackageInfo> packages) {
        cache.clear();
        packages.forEach(pkg -> cache.put(pkg.getPackageId(), pkg));
        System.out.println("[PackageCache] Cached " + packages.size() + " packages");
    }
    
    public List<PackageInfo> getAllPackages() {
        return new ArrayList<>(cache.values());
    }
    
    public PackageInfo getPackage(String packageId) {
        return cache.get(packageId);
    }
}