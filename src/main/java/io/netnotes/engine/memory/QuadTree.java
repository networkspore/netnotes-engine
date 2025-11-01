package io.netnotes.engine.memory;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory-adaptive QuadTree with dynamic scaling based on render strategy.
 * 
 * Features:
 * - Strategy-based node limits (HIGH_PERFORMANCE: unlimited, MINIMAL: strict limits)
 * - LRU node pool for recycling (avoids GC churn)
 * - Automatic compaction under memory pressure
 * - Progressive degradation (deep trees flatten to shallow)
 */
public class QuadTree<T> {
    
    // Strategy-based configuration
    private static class StrategyConfig {
        final int maxObjects;      // Objects per node before split
        final int maxLevels;       // Maximum tree depth
        final int nodePoolSize;    // LRU node pool capacity
        final boolean enablePool;  // Use node pooling
        
        StrategyConfig(int maxObjects, int maxLevels, int nodePoolSize, boolean enablePool) {
            this.maxObjects = maxObjects;
            this.maxLevels = maxLevels;
            this.nodePoolSize = nodePoolSize;
            this.enablePool = enablePool;
        }
        
        static StrategyConfig forStrategy(OptimizationStrategy strategy) {
            return switch (strategy) {
                case HIGH_PERFORMANCE -> new StrategyConfig(10, 8, 1000, true);
                case OPTIMAL -> new StrategyConfig(10, 6, 500, true);
                case LOW_MEMORY -> new StrategyConfig(15, 4, 100, true);
                case MINIMAL -> new StrategyConfig(20, 3, 0, false);
            };
        }
    }

    public enum OptimizationStrategy {
        HIGH_PERFORMANCE,  // Max quality and caching
        OPTIMAL,           // Balanced (default)
        LOW_MEMORY,        // Reduce caching, smaller buffers
        MINIMAL            // No snapshot, text-only caching
    }
    
    // LRU Node Pool for recycling
    private static class NodePool<T> {
        private final Map<Integer, List<QuadTree<T>>> pool;
        private final int maxSize;
        private int totalNodes;
        
        NodePool(int maxSize) {
            this.maxSize = maxSize;
            this.totalNodes = 0;
            // LinkedHashMap with access-order for LRU
            this.pool = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, List<QuadTree<T>>> eldest) {
                    if (totalNodes > maxSize) {
                        List<QuadTree<T>> nodes = eldest.getValue();
                        totalNodes -= nodes.size();
                        return true;
                    }
                    return false;
                }
            };
        }
        
        QuadTree<T> acquire(int level, Rectangle bounds, StrategyConfig config) {
            List<QuadTree<T>> levelPool = pool.get(level);
            
            if (levelPool != null && !levelPool.isEmpty()) {
                QuadTree<T> node = levelPool.remove(levelPool.size() - 1);
                totalNodes--;
                node.reset(bounds, config);
                return node;
            }
            
            // Create new node
            return new QuadTree<>(level, bounds, config, this);
        }
        
        void release(QuadTree<T> node) {
            if (totalNodes >= maxSize) return;
            
            int level = node.level;
            List<QuadTree<T>> levelPool = pool.computeIfAbsent(level, v -> new ArrayList<>());
            levelPool.add(node);
            totalNodes++;
        }
        
        void clear() {
            pool.clear();
            totalNodes = 0;
        }
        
        int size() {
            return totalNodes;
        }
    }
    
    // Entry storage
    private static class Entry<T> {
        final Rectangle bounds;
        final T data;
        
        Entry(Rectangle bounds, T data) {
            this.bounds = bounds;
            this.data = data;
        }
    }
    
    // Instance fields
    private final int level;
    private Rectangle bounds;
    private final List<Entry<T>> objects;
    private QuadTree<T>[] nodes;
    private StrategyConfig config;
    private final NodePool<T> nodePool;
    
    // Statistics
    private int insertCount;
    private int splitCount;
    
    // Public constructor (root node)
    public QuadTree(Rectangle bounds, OptimizationStrategy strategy) {
        this(0, bounds, StrategyConfig.forStrategy(strategy), 
             new NodePool<>(StrategyConfig.forStrategy(strategy).nodePoolSize));
    }
    
    // Internal constructor
    private QuadTree(int level, Rectangle bounds, StrategyConfig config, NodePool<T> nodePool) {
        this.level = level;
        this.bounds = bounds;
        this.config = config;
        this.nodePool = nodePool;
        this.objects = new ArrayList<>(config.maxObjects);
        this.nodes = null;
        this.insertCount = 0;
        this.splitCount = 0;
    }
    
    /**
     * Adapt to new strategy - reconfigures tree on the fly
     */
    public void adaptToStrategy(OptimizationStrategy newStrategy) {
        StrategyConfig newConfig = StrategyConfig.forStrategy(newStrategy);
        
        // Only reconfigure if meaningful change
        if (newConfig.maxLevels < this.config.maxLevels) {
            // More restrictive - may need to flatten tree
            this.config = newConfig;
            compactTree();
        } else {
            // Less restrictive - just update config
            this.config = newConfig;
        }
    }
    
    /**
     * Compact tree by collapsing nodes that exceed new depth limit
     */
    private void compactTree() {
        if (level >= config.maxLevels && nodes != null) {
            // This level is now too deep - collapse children
            collapseChildren();
        } else if (nodes != null) {
            // Recursively compact children
            for (QuadTree<T> node : nodes) {
                if (node != null) {
                    node.compactTree();
                }
            }
        }
    }
    
    /**
     * Collapse all children into this node
     */
    private void collapseChildren() {
        if (nodes == null) return;
        
        List<Entry<T>> collected = new ArrayList<>();
        collectAllEntries(collected);
        
        // Clear children and return to pool
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                if (config.enablePool) {
                    nodes[i].clearAndRelease();
                } else {
                    nodes[i].clear();
                }
                nodes[i] = null;
            }
        }
        nodes = null;
        
        // Restore collected entries to this node
        objects.clear();
        objects.addAll(collected);
    }
    
    /**
     * Collect all entries from this node and descendants
     */
    private void collectAllEntries(List<Entry<T>> collected) {
        collected.addAll(objects);
        
        if (nodes != null) {
            for (QuadTree<T> node : nodes) {
                if (node != null) {
                    node.collectAllEntries(collected);
                }
            }
        }
    }
    
    /**
     * Reset node for reuse (called from pool)
     */
    private void reset(Rectangle bounds, StrategyConfig config) {
        this.bounds = bounds;
        this.config = config;
        this.objects.clear();
        
        if (this.nodes != null) {
            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = null;
            }
            this.nodes = null;
        }
        
        this.insertCount = 0;
        this.splitCount = 0;
    }
    
    /**
     * Clear and release to pool
     */
    private void clearAndRelease() {
        clear();
        if (config.enablePool && nodePool != null) {
            nodePool.release(this);
        }
    }
    
    /**
     * Clear the quadtree
     */
    public void clear() {
        objects.clear();
        
        if (nodes != null) {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] != null) {
                    if (config.enablePool) {
                        nodes[i].clearAndRelease();
                    } else {
                        nodes[i].clear();
                    }
                    nodes[i] = null;
                }
            }
            nodes = null;
        }
    }
    
    /**
     * Split the node into 4 subnodes
     */
    @SuppressWarnings("unchecked")
    private void split() {
        if (level >= config.maxLevels) {
            // Can't split - at max depth
            return;
        }
        
        int subWidth = bounds.width / 2;
        int subHeight = bounds.height / 2;
        int x = bounds.x;
        int y = bounds.y;
        
        nodes = (QuadTree<T>[]) new QuadTree[4];
        
        // Use pool if enabled
        if (config.enablePool && nodePool != null) {
            nodes[0] = nodePool.acquire(level + 1, 
                new Rectangle(x + subWidth, y, subWidth, subHeight), config);
            nodes[1] = nodePool.acquire(level + 1, 
                new Rectangle(x, y, subWidth, subHeight), config);
            nodes[2] = nodePool.acquire(level + 1, 
                new Rectangle(x, y + subHeight, subWidth, subHeight), config);
            nodes[3] = nodePool.acquire(level + 1, 
                new Rectangle(x + subWidth, y + subHeight, subWidth, subHeight), config);
        } else {
            nodes[0] = new QuadTree<>(level + 1, 
                new Rectangle(x + subWidth, y, subWidth, subHeight), config, nodePool);
            nodes[1] = new QuadTree<>(level + 1, 
                new Rectangle(x, y, subWidth, subHeight), config, nodePool);
            nodes[2] = new QuadTree<>(level + 1, 
                new Rectangle(x, y + subHeight, subWidth, subHeight), config, nodePool);
            nodes[3] = new QuadTree<>(level + 1, 
                new Rectangle(x + subWidth, y + subHeight, subWidth, subHeight), config, nodePool);
        }
        
        splitCount++;
    }
    
    /**
     * Determine which node the object belongs to
     */
    private int getIndex(Rectangle rect) {
        if (nodes == null) return -1;
        
        int index = -1;
        double verticalMidpoint = bounds.x + bounds.width / 2.0;
        double horizontalMidpoint = bounds.y + bounds.height / 2.0;
        
        boolean topQuadrant = (rect.y < horizontalMidpoint && 
                              rect.y + rect.height < horizontalMidpoint);
        boolean bottomQuadrant = (rect.y > horizontalMidpoint);
        
        if (rect.x < verticalMidpoint && rect.x + rect.width < verticalMidpoint) {
            if (topQuadrant) {
                index = 1; // NW
            } else if (bottomQuadrant) {
                index = 2; // SW
            }
        } else if (rect.x > verticalMidpoint) {
            if (topQuadrant) {
                index = 0; // NE
            } else if (bottomQuadrant) {
                index = 3; // SE
            }
        }
        
        return index;
    }
    
    /**
     * Insert object into quadtree
     */
    public void insert(Rectangle rect, T data) {
        insertCount++;
        
        if (nodes != null) {
            int index = getIndex(rect);
            if (index != -1) {
                nodes[index].insert(rect, data);
                return;
            }
        }
        
        objects.add(new Entry<>(rect, data));
        
        // Split if needed (respects strategy limits)
        if (objects.size() > config.maxObjects && level < config.maxLevels) {
            if (nodes == null) {
                split();
            }
            
            // Redistribute objects
            int i = 0;
            while (i < objects.size()) {
                int index = getIndex(objects.get(i).bounds);
                if (index != -1) {
                    Entry<T> entry = objects.remove(i);
                    nodes[index].insert(entry.bounds, entry.data);
                } else {
                    i++;
                }
            }
        }
    }
    
    /**
     * Query objects at a specific point
     */
    public T query(int x, int y) {
        List<T> results = new ArrayList<>();
        queryRecursive(x, y, results);
        return results.isEmpty() ? null : results.get(results.size() - 1);
    }
    
    /**
     * Query all objects at a point
     */
    public List<T> queryAll(int x, int y) {
        List<T> results = new ArrayList<>();
        queryRecursive(x, y, results);
        return results;
    }
    
    private void queryRecursive(int x, int y, List<T> results) {
        if (!bounds.contains(x, y)) return;
        
        for (Entry<T> entry : objects) {
            if (entry.bounds.contains(x, y)) {
                results.add(entry.data);
            }
        }
        
        if (nodes != null) {
            for (QuadTree<T> node : nodes) {
                if (node != null) {
                    node.queryRecursive(x, y, results);
                }
            }
        }
    }
    
    /**
     * Query objects that intersect a rectangle
     */
    public List<T> queryRange(Rectangle range) {
        List<T> results = new ArrayList<>();
        queryRangeRecursive(range, results);
        return results;
    }
    
    private void queryRangeRecursive(Rectangle range, List<T> results) {
        if (!bounds.intersects(range)) return;
        
        for (Entry<T> entry : objects) {
            if (entry.bounds.intersects(range)) {
                results.add(entry.data);
            }
        }
        
        if (nodes != null) {
            for (QuadTree<T> node : nodes) {
                if (node != null) {
                    node.queryRangeRecursive(range, results);
                }
            }
        }
    }
    
    /**
     * Get total number of objects in tree
     */
    public int size() {
        int count = objects.size();
        if (nodes != null) {
            for (QuadTree<T> node : nodes) {
                if (node != null) {
                    count += node.size();
                }
            }
        }
        return count;
    }
    
    /**
     * Get enhanced statistics
     */
    public AdaptiveTreeStats getStats() {
        AdaptiveTreeStats stats = new AdaptiveTreeStats();
        stats.strategy = getCurrentStrategyName();
        stats.poolSize = nodePool != null ? nodePool.size() : 0;
        stats.maxPoolSize = config.nodePoolSize;
        collectStats(stats);
        return stats;
    }
    
    private String getCurrentStrategyName() {
        if (config.maxLevels == 8) return "HIGH_PERFORMANCE";
        if (config.maxLevels == 6) return "OPTIMAL";
        if (config.maxLevels == 4) return "LOW_MEMORY";
        return "MINIMAL";
    }
    
    private void collectStats(AdaptiveTreeStats stats) {
        stats.totalNodes++;
        stats.totalObjects += objects.size();
        stats.maxDepth = Math.max(stats.maxDepth, level);
        stats.totalInserts += insertCount;
        stats.totalSplits += splitCount;
        
        if (objects.size() > stats.maxObjectsPerNode) {
            stats.maxObjectsPerNode = objects.size();
        }
        
        if (nodes == null) {
            stats.leafNodes++;
        } else {
            for (QuadTree<T> node : nodes) {
                if (node != null) {
                    node.collectStats(stats);
                }
            }
        }
    }
    
    /**
     * Enhanced statistics with memory awareness
     */
    public static class AdaptiveTreeStats {
        public int totalNodes = 0;
        public int leafNodes = 0;
        public int totalObjects = 0;
        public int maxDepth = 0;
        public int maxObjectsPerNode = 0;
        public int totalInserts = 0;
        public int totalSplits = 0;
        public int poolSize = 0;
        public int maxPoolSize = 0;
        public String strategy = "UNKNOWN";
        
        /**
         * Estimate memory footprint in bytes
         */
        public long estimateMemoryBytes() {
            // Rough estimate:
            // - Each node: ~100 bytes (object overhead + fields)
            // - Each entry: ~50 bytes (Rectangle + reference)
            // - Pool overhead: ~30 bytes per pooled node
            return (totalNodes * 100L) + 
                   (totalObjects * 50L) + 
                   (poolSize * 30L);
        }
        
        public double getAverageObjectsPerLeaf() {
            return leafNodes > 0 ? (double) totalObjects / leafNodes : 0;
        }
        
        public double getPoolUtilization() {
            return maxPoolSize > 0 ? (double) poolSize / maxPoolSize : 0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "AdaptiveQuadTree[strategy=%s, nodes=%d, leaves=%d, objects=%d, depth=%d, " +
                "avgObj/leaf=%.1f, inserts=%d, splits=%d, pool=%d/%d (%.0f%%), mem≈%dKB]",
                strategy, totalNodes, leafNodes, totalObjects, maxDepth,
                getAverageObjectsPerLeaf(), totalInserts, totalSplits,
                poolSize, maxPoolSize, getPoolUtilization() * 100,
                estimateMemoryBytes() / 1024
            );
        }
    }
    
    /**
     * Clear node pool (call on app shutdown)
     */
    public void clearPool() {
        if (nodePool != null) {
            nodePool.clear();
        }
    }
}
