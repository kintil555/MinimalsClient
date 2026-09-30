package com.minimals.client.worldhost;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Minecraft 26.1.2 has no vanilla Friends list (added in 26.2), so there are no World Host
 * friends on this version. The API is kept so WorldHostManager stays unchanged; it simply
 * never announces to or accepts anyone.
 */
public final class WorldHostFriends {

    private WorldHostFriends() {
    }

    public static boolean isFriend(UUID uuid) {
        return false;
    }

    public static String nameOf(UUID uuid) {
        return uuid.toString();
    }

    public static Set<UUID> all() {
        return Collections.emptySet();
    }
}
