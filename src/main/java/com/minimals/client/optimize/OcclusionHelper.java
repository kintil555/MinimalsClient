package com.minimals.client.optimize;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared line-of-sight test used by the Optimization module.
 *
 * <p>A raycast against block collision shapes is far cheaper than extracting a render state and
 * submitting draw calls, but it is still too expensive to run for every entity every frame, so
 * results are cached per object for a short window (temporal hysteresis). Only the client thread
 * touches this cache.
 */
public final class OcclusionHelper {

    /** Anything closer than this is never culled (prevents flicker when standing next to a mob). */
    private static final double IMMUNITY_DISTANCE_SQR = 16.0;

    /** How long a cached visibility answer stays valid. */
    private static final long CACHE_MS = 150L;

    private static final Map<Integer, Entry> ENTITY_CACHE = new HashMap<>();
    private static final Map<Long, Entry> BLOCK_CACHE = new HashMap<>();
    private static Level cachedLevel;

    private OcclusionHelper() {
    }

    private static final class Entry {
        long stamp;
        boolean visible;
    }

    /** Drops the cache when the player changes world/dimension. */
    private static void validateLevel(Level level) {
        if (cachedLevel != level) {
            cachedLevel = level;
            ENTITY_CACHE.clear();
            BLOCK_CACHE.clear();
        }
    }

    /**
     * True when no solid block blocks the straight line between the two points.
     * Uses collider shapes only and ignores fluids, so water/lava never hides anything.
     */
    public static boolean hasLineOfSight(Level level, Vec3 from, double toX, double toY, double toZ) {
        Vec3 to = new Vec3(toX, toY, toZ);
        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * Multi-point visibility test for an entity: samples feet, eyes, head and two opposite
     * horizontal corners so a mob peeking around a wall is not culled.
     */
    public static boolean isEntityVisible(Entity entity, Level level, Vec3 camera) {
        validateLevel(level);

        if (entity.distanceToSqr(camera.x, camera.y, camera.z) < IMMUNITY_DISTANCE_SQR) {
            return true;
        }

        int key = entity.getId();
        Entry entry = ENTITY_CACHE.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && now - entry.stamp < CACHE_MS) {
            return entry.visible;
        }

        AABB box = entity.getBoundingBox();
        double midX = (box.minX + box.maxX) * 0.5;
        double midY = (box.minY + box.maxY) * 0.5;
        double midZ = (box.minZ + box.maxZ) * 0.5;

        boolean visible =
                hasLineOfSight(level, camera, midX, entity.getEyeY(), midZ)
                        || hasLineOfSight(level, camera, midX, box.maxY - 0.05, midZ)
                        || hasLineOfSight(level, camera, midX, box.minY + 0.1, midZ)
                        || hasLineOfSight(level, camera, box.minX + 0.05, midY, box.minZ + 0.05)
                        || hasLineOfSight(level, camera, box.maxX - 0.05, midY, box.maxZ - 0.05);

        if (entry == null) {
            entry = new Entry();
            ENTITY_CACHE.put(key, entry);
        }
        entry.stamp = now;
        entry.visible = visible;

        if (ENTITY_CACHE.size() > 4096) {
            ENTITY_CACHE.clear();
        }
        return visible;
    }

    /**
     * Visibility test for a static position (block entities): samples the block centre and the
     * four horizontal face centres so a chest against a wall still renders from the open side.
     */
    public static boolean isBlockVisible(Level level, Vec3 camera, double x, double y, double z) {
        validateLevel(level);

        double dx = camera.x - (x + 0.5);
        double dy = camera.y - (y + 0.5);
        double dz = camera.z - (z + 0.5);
        if (dx * dx + dy * dy + dz * dz < IMMUNITY_DISTANCE_SQR) {
            return true;
        }

        long key = net.minecraft.core.BlockPos.asLong((int) x, (int) y, (int) z);
        Entry entry = BLOCK_CACHE.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && now - entry.stamp < CACHE_MS) {
            return entry.visible;
        }

        boolean visible =
                hasLineOfSight(level, camera, x + 0.5, y + 1.05, z + 0.5)
                        || hasLineOfSight(level, camera, x - 0.05, y + 0.5, z + 0.5)
                        || hasLineOfSight(level, camera, x + 1.05, y + 0.5, z + 0.5)
                        || hasLineOfSight(level, camera, x + 0.5, y + 0.5, z - 0.05)
                        || hasLineOfSight(level, camera, x + 0.5, y + 0.5, z + 1.05);

        if (entry == null) {
            entry = new Entry();
            BLOCK_CACHE.put(key, entry);
        }
        entry.stamp = now;
        entry.visible = visible;

        if (BLOCK_CACHE.size() > 8192) {
            BLOCK_CACHE.clear();
        }
        return visible;
    }
}
