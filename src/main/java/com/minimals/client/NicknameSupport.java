package com.minimals.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared logic for the client-side nickname. Servers format chat, join/leave and death
 * messages in many different ways (signed player chat, disguised chat, plain system text),
 * so instead of hooking each packet type the real username is replaced inside the final
 * component that reaches the chat / tab list. Only the local player's own name is touched.
 */
public final class NicknameSupport {

    private static String cachedName = "";
    private static Pattern cachedPattern;

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

    /** Whole-word pattern for the local username (names are [A-Za-z0-9_]), or null. */
    private static Pattern realNamePattern() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        String name = player.getGameProfile().name();
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (!name.equals(cachedName)) {
            cachedName = name;
            cachedPattern = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])");
        }
        return cachedPattern;
    }

    public static boolean containsRealName(Component component) {
        Pattern pattern = realNamePattern();
        return pattern != null && pattern.matcher(component.getString()).find();
    }

    /**
     * Returns the component with every whole-word occurrence of the local username replaced
     * by the styled nickname; all other text keeps its style, click and hover events.
     * Returns the original instance when there is no nickname or no occurrence.
     */
    public static Component replaceRealName(Component original) {
        Pattern pattern = realNamePattern();
        Component nickname = ClientSettings.nicknameComponent();
        if (pattern == null || nickname == null || !containsRealName(original)) {
            return original;
        }
        String nick = ClientSettings.NICKNAME.get();
        Style nickStyle = nickname.getStyle();
        MutableComponent out = Component.empty();
        original.visit((style, text) -> {
            Matcher matcher = pattern.matcher(text);
            int last = 0;
            while (matcher.find()) {
                if (matcher.start() > last) {
                    out.append(Component.literal(text.substring(last, matcher.start())).setStyle(style));
                }
                out.append(Component.literal(nick).setStyle(nickStyle.applyTo(style)));
                last = matcher.end();
            }
            if (last < text.length()) {
                out.append(Component.literal(text.substring(last)).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }
}
