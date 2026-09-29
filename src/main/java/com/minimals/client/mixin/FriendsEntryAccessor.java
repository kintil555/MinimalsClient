package com.minimals.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

/** Exposes the profile id of a vanilla friends-list entry (the class is package-private). */
@Mixin(targets = "net.minecraft.client.gui.screens.friends.AbstractFriendsEntryContainerWidget")
public interface FriendsEntryAccessor {
    @Accessor("playerId")
    UUID minimals$getPlayerId();
}
