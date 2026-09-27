package com.minimals.client.worldhost;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny hand-rolled JSON store for the friend list, kept separate from the main mod
 * config (world-host-friends.json, mirroring how the original World Host mod keeps
 * friends.json out of the shareable settings file). Format: {"uuid":"name", ...}.
 */
public final class WorldHostFriends {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("minimals").resolve("world-host-friends.json");

    /** UUID -> last known username (display only; identity is always the UUID). */
    private static final Map<UUID, String> FRIENDS = new LinkedHashMap<>();

    private static final Pattern ENTRY = Pattern.compile("\"([0-9a-fA-F-]{36})\"\\s*:\\s*\"([^\"]*)\"");

    private WorldHostFriends() {
    }

    public static void load() {
        FRIENDS.clear();
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        try {
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            Matcher m = ENTRY.matcher(text);
            while (m.find()) {
                try {
                    FRIENDS.put(UUID.fromString(m.group(1)), m.group(2));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException e) {
            WorldHostManager.LOGGER.warn("Could not read world-host-friends.json", e);
        }
    }

    public static void save() {
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<UUID, String> e : FRIENDS.entrySet()) {
            sb.append("  \"").append(e.getKey()).append("\": \"")
                    .append(e.getValue().replace("\"", "")).append('"');
            if (++i < FRIENDS.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("}\n");
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            WorldHostManager.LOGGER.warn("Could not save world-host-friends.json", e);
        }
    }

    public static void add(UUID uuid, String name) {
        FRIENDS.put(uuid, name);
        save();
    }

    public static void remove(UUID uuid) {
        if (FRIENDS.remove(uuid) != null) {
            save();
        }
    }

    public static boolean isFriend(UUID uuid) {
        return FRIENDS.containsKey(uuid);
    }

    public static String nameOf(UUID uuid) {
        return FRIENDS.getOrDefault(uuid, uuid.toString());
    }

    public static Set<UUID> all() {
        return new LinkedHashSet<>(FRIENDS.keySet());
    }

    public static Map<UUID, String> allWithNames() {
        return new LinkedHashMap<>(FRIENDS);
    }
}
