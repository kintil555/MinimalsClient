package com.minimals.client.mixin;

import com.minimals.client.ClientSettings;
import com.minimals.client.NicknameSupport;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Chat nickname. addPlayerMessage, addServerSystemMessage and addClientSystemMessage all
 * funnel into the private addMessage(Component, ...), so this single hook covers signed chat,
 * disguised chat and system lines (join/leave, death messages, plugin-formatted chat).
 */
@Mixin(ChatComponent.class)
public class ChatNameMixin {

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, index = 1)
    private Component minimals$nickname(Component contents) {
        if (!ClientSettings.hasNickname()) {
            return contents;
        }
        return NicknameSupport.replaceRealName(contents);
    }
}
