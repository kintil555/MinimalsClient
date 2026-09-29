package com.minimals.client.worldhost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * World Host friends are simply the player's vanilla (Microsoft) friends: nothing is
 * stored by this mod any more. Both sides of a friendship see each other's open world
 * because both lists contain each other.
 */
public final class WorldHostFriends {

    private WorldHostFriends() {
    }

    public static boolean isFriend(UUID uuid) {
        PlayerSocialManager social = Minecraft.getInstance().getPlayerSocialManager();
        if (social == null) return false;
        for (PlayerSocialManager.PlayerData friend : social.getFriends()) {
            if (friend.id().equals(uuid)) return true;
        }
        return false;
    }

    public static String nameOf(UUID uuid) {
        PlayerSocialManager social = Minecraft.getInstance().getPlayerSocialManager();
        if (social != null) {
            for (PlayerSocialManager.PlayerData friend : social.getFriends()) {
                if (friend.id().equals(uuid)) return friend.name();
            }
        }
        return uuid.toString();
    }

    public static Set<UUID> all() {
        Set<UUID> result = new LinkedHashSet<>();
        PlayerSocialManager social = Minecraft.getInstance().getPlayerSocialManager();
        if (social != null) {
            for (PlayerSocialManager.PlayerData friend : social.getFriends()) {
                result.add(friend.id());
            }
        }
        return result;
    }
}
