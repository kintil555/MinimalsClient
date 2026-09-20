package com.minimals.client.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Owns the waypoints of the world the player is currently in.
 *
 * <ul>
 *   <li>The list is loaded from disk once when the world key changes (join / switch server), and
 *       written back only when something is added, renamed or removed - never per frame.</li>
 *   <li>Readers get an immutable snapshot ({@link #visible()}) already filtered to the current
 *       dimension. It is rebuilt only when the waypoints or the dimension change, so the
 *       per-frame render path allocates nothing and does no filtering.</li>
 *   <li>All access is from the client thread; the fields are not shared across threads.</li>
 * </ul>
 */
public final class WaypointManager {

    /** Waypoints per world. Generous for real use, small enough that a runaway loop cannot fill the disk. */
    public static final int MAX_PER_WORLD = 256;

    private static String worldKey;
    private static List<Waypoint> all = List.of();

    private static String visibleDimension;
    private static List<Waypoint> visible = List.of();

    private WaypointManager() {
    }

    /**
     * Makes sure the in-memory list belongs to the world the player is in now. Cheap when
     * nothing changed (one string compare), so it is safe to call every tick.
     */
    public static void sync(Minecraft mc) {
        String key = currentWorldKey(mc);
        if (key == null) {
            if (worldKey != null) {
                // Left the world: drop the cache so the next join reloads its own file.
                worldKey = null;
                all = List.of();
                visible = List.of();
                visibleDimension = null;
            }
            return;
        }
        if (!key.equals(worldKey)) {
            worldKey = key;
            all = List.copyOf(WaypointStore.load(key));
            visibleDimension = null;
        }
        String dimension = currentDimension(mc);
        if (dimension != null && !dimension.equals(visibleDimension)) {
            rebuildVisible(dimension);
        }
    }

    /** Waypoints of the current dimension. Immutable; never null. */
    public static List<Waypoint> visible() {
        return visible;
    }

    public static List<Waypoint> all() {
        return all;
    }

    public static boolean isFull() {
        return all.size() >= MAX_PER_WORLD;
    }

    /**
     * Adds a waypoint at the given block and saves. Returns null when the list is full or there
     * is no world to attach it to.
     */
    public static Waypoint add(Minecraft mc, String name, WaypointIcon icon, int color, int x, int y, int z) {
        sync(mc);
        String dimension = currentDimension(mc);
        if (worldKey == null || dimension == null || isFull()) {
            return null;
        }
        Waypoint waypoint = new Waypoint(nextId(), Waypoint.cleanName(name), icon, color & 0xFFFFFF, x, y, z, dimension);
        List<Waypoint> next = new ArrayList<>(all.size() + 1);
        next.addAll(all);
        next.add(waypoint);
        commit(next, dimension);
        return waypoint;
    }

    public static void remove(Minecraft mc, long id) {
        sync(mc);
        if (worldKey == null) {
            return;
        }
        List<Waypoint> next = new ArrayList<>(all.size());
        for (Waypoint w : all) {
            if (w.id() != id) {
                next.add(w);
            }
        }
        if (next.size() != all.size()) {
            commit(next, visibleDimension);
        }
    }

    private static void commit(List<Waypoint> next, String dimension) {
        all = List.copyOf(next);
        WaypointStore.save(worldKey, all);
        rebuildVisible(dimension);
    }

    private static void rebuildVisible(String dimension) {
        visibleDimension = dimension;
        if (dimension == null) {
            visible = List.of();
            return;
        }
        List<Waypoint> filtered = new ArrayList<>();
        for (Waypoint w : all) {
            if (w.dimension().equals(dimension)) {
                filtered.add(w);
            }
        }
        visible = List.copyOf(filtered);
    }

    /** Millisecond timestamp, bumped when needed so two waypoints made in the same ms never share an id. */
    private static long lastId;

    private static long nextId() {
        long id = System.currentTimeMillis();
        if (id <= lastId) {
            id = lastId + 1;
        }
        for (Waypoint w : all) {
            if (w.id() >= id) {
                id = w.id() + 1;
            }
        }
        lastId = id;
        return id;
    }

    public static String currentDimension(Minecraft mc) {
        ClientLevel level = mc.level;
        return level == null ? null : level.dimension().identifier().toString();
    }

    /**
     * Stable identity of the current world, or null when not in one.
     * Singleplayer uses the save folder name (unchanged if the world is renamed in the menu);
     * multiplayer uses the address the player typed/picked, lower-cased.
     */
    static String currentWorldKey(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            Path root = server.getWorldPath(LevelResource.ROOT);
            Path folder = root == null ? null : root.toAbsolutePath().normalize().getFileName();
            if (folder != null) {
                return "sp:" + folder;
            }
        }
        ServerData data = mc.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) {
            return "mp:" + data.ip.strip().toLowerCase(Locale.ROOT);
        }
        // Realms / unknown transports: still keep waypoints, in one shared bucket.
        return "mp:unknown";
    }
}
