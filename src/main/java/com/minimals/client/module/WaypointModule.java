package com.minimals.client.module;

import com.minimals.client.module.setting.BoolSetting;
import com.minimals.client.module.setting.IntSetting;

/**
 * Waypoints: markers with an icon, a name, the distance to the player and their coordinates,
 * drawn over the world at their location. The module's own keybind (the "Keybind" row in the
 * ClickGUI) only switches the module on/off; placing a waypoint has its own, separate key
 * ("Add Waypoint" in Minecraft's Controls screen).
 *
 * All drawing lives in {@link com.minimals.client.waypoint.WaypointRenderer}; all storage in
 * {@link com.minimals.client.waypoint.WaypointManager}. This class only holds on/off and options.
 */
public class WaypointModule extends Module {

    public final BoolSetting showDistance = addSetting(new BoolSetting("Show Distance", true));
    public final BoolSetting showCoords = addSetting(new BoolSetting("Show Coords", true));
    public final BoolSetting showName = addSetting(new BoolSetting("Show Name", true));
    public final BoolSetting deathButton = addSetting(new BoolSetting("Death Button", true));
    /** Waypoints further away than this (blocks) are hidden. 0 would mean "never hide", so the floor is 100. */
    public final IntSetting maxDistance = addSetting(new IntSetting("Max Distance", 5000, 100, 30000, 100, "m"));
    /** Icon/label size in percent of the default. */
    public final IntSetting scale = addSetting(new IntSetting("Scale", 100, 50, 200, 10, "%"));

    public WaypointModule() {
        super("Waypoints", Category.VISUALS);
    }
}
