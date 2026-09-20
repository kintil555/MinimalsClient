package com.minimals.client.waypoint;

/**
 * One saved waypoint. Immutable so the render thread can read a published snapshot list without
 * any locking while the GUI/tick code replaces entries.
 *
 * @param id        unique per waypoint (used to delete/edit even if two share a name)
 * @param name      display name, already trimmed and length-capped
 * @param icon      icon mask
 * @param color     RGB (no alpha) the icon and label are tinted with
 * @param x         block coordinates the waypoint marks
 * @param dimension dimension identifier such as {@code minecraft:overworld}; a waypoint is only
 *                  shown while the player is in the same dimension
 */
public record Waypoint(long id, String name, WaypointIcon icon, int color,
                       int x, int y, int z, String dimension) {

    public static final int MAX_NAME_LENGTH = 24;

    /** Colours offered by the create screen. */
    public static final int[] PALETTE = {
            0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF, 0xFFFFFF
    };

    public Waypoint withName(String newName) {
        return new Waypoint(id, newName, icon, color, x, y, z, dimension);
    }

    /** Clamps a user-typed name to something safe to draw and to store on one config line. */
    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace('\n', ' ').replace('\r', ' ').replace('|', ' ').strip();
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
    }
}
