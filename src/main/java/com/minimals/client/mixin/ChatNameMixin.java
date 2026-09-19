package com.minimals.client.mixin;

import com.minimals.client.ClientSettings;
import com.minimals.client.NicknameSupport;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Chat nickname. Every chat line is decorated with ChatType.Bound.decorate, whose
 * {@code name} is the sender component the server sent. For a message whose sender is the
 * local player we swap in a Bound carrying the nickname before any decorating happens, so
 * the chat line, the filtered variant and the narrator all use it.
 *
 * The sender is compared by profile id; ChatListener.isSenderLocalPlayer is not used because
 * it is only true on a singleplayer/LAN host.
 */
@Mixin(ChatListener.class)
public class ChatNameMixin {

    @ModifyVariable(method = "handlePlayerChatMessage", at = @At("HEAD"), argsOnly = true, index = 3)
    private ChatType.Bound minimals$nickname(ChatType.Bound bound, PlayerChatMessage message, GameProfile sender) {
        if (!ClientSettings.hasNickname() || !NicknameSupport.isLocalProfile(sender)) {
            return bound;
        }
        Component nickname = ClientSettings.nicknameComponent();
        if (nickname == null) {
            return bound;
        }
        return new ChatType.Bound(bound.chatType(), nickname, bound.targetName());
    }
}
