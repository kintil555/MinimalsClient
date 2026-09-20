package com.minimals.client.waypoint;

import com.minimals.client.ConfigManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads and writes one waypoint file per world/server in {@code config/minimals/waypoints/}.
 * Living on disk (not in the world save) is what lets waypoints survive leaving a server or
 * world, and also works on servers where the player cannot write anything.
 *
 * Line format, one waypoint per line: {@code id|icon|rrggbb|x|y|z|dimension|name}. The name is
 * last and never contains {@code |} (see {@link Waypoint#cleanName}); malformed lines are
 * skipped so a hand-edited file can never crash the game.
 */
final class WaypointStore {

    private static final String EXTENSION = ".txt";
    private static final String HEADER = "# Minimals waypoints: id|icon|color|x|y|z|dimension|name";

    private WaypointStore() {
    }

    static Path directory() {
        return ConfigManager.directory().resolve("waypoints");
    }

    /**
     * Turns a world key such as {@code sp:My World} or {@code mp:play.example.com:25565} into a
     * safe file name. Only [a-z0-9_-] survive, so a hostile server address can never escape the
     * folder; a short hash of the original keeps two different keys that sanitise to the same
     * text from sharing a file.
     */
    static String fileNameFor(String worldKey) {
        String base = worldKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (base.length() > 48) {
            base = base.substring(0, 48);
        }
        return base + "-" + Integer.toHexString(worldKey.hashCode()) + EXTENSION;
    }

    static List<Waypoint> load(String worldKey) {
        Path file = directory().resolve(fileNameFor(worldKey));
        List<Waypoint> result = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return result;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Minimals] Could not read waypoints '" + file.getFileName() + "': " + e);
            return result;
        }
        for (String raw : lines) {
            Waypoint waypoint = parse(raw);
            if (waypoint != null) {
                result.add(waypoint);
            }
        }
        return result;
    }

    static boolean save(String worldKey, List<Waypoint> waypoints) {
        Path dir = directory();
        Path file = dir.resolve(fileNameFor(worldKey));
        Path temp = dir.resolve(file.getFileName() + ".tmp");
        List<String> lines = new ArrayList<>(waypoints.size() + 1);
        lines.add(HEADER);
        for (Waypoint w : waypoints) {
            lines.add(format(w));
        }
        try {
            Files.createDirectories(dir);
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            System.err.println("[Minimals] Could not save waypoints '" + file.getFileName() + "': " + e);
            return false;
        }
    }

    static String format(Waypoint w) {
        return w.id() + "|" + w.icon().name() + "|" + String.format("%06x", w.color() & 0xFFFFFF)
                + "|" + w.x() + "|" + w.y() + "|" + w.z() + "|" + w.dimension() + "|" + w.name();
    }

    /** Returns null for comments, blank lines and anything malformed. */
    static Waypoint parse(String raw) {
        String line = raw.strip();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        String[] parts = line.split("\\|", 8);
        if (parts.length < 8) {
            return null;
        }
        try {
            long id = Long.parseLong(parts[0].trim());
            WaypointIcon icon = WaypointIcon.parse(parts[1]);
            int color = Integer.parseInt(parts[2].trim(), 16) & 0xFFFFFF;
            int x = Integer.parseInt(parts[3].trim());
            int y = Integer.parseInt(parts[4].trim());
            int z = Integer.parseInt(parts[5].trim());
            String dimension = parts[6].trim();
            if (dimension.isEmpty()) {
                return null;
            }
            return new Waypoint(id, Waypoint.cleanName(parts[7]), icon, color, x, y, z, dimension);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
