package com.minimals.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;

import java.util.UUID;

/**
 * Shared checks for the client-side nickname: it must only ever replace the local player's
 * own name, never another player's.
 */
public final class NicknameSupport {

    private NicknameSupport() {
    }

    public static boolean isLocalPlayerId(UUID id) {
        LocalPlayer player = Minecraft.getInstance().player;
        return id != null && player != null && id.equals(player.getGameProfile().id());
    }

    public static boolean isLocalProfile(GameProfile profile) {
        return profile != null && isLocalPlayerId(profile.id());
    }

    public static boolean isLocalInfo(PlayerInfo info) {
        return info != null && isLocalProfile(info.getProfile());
    }
}
