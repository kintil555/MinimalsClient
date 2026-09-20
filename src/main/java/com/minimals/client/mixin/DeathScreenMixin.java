package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.WaypointModule;
import com.minimals.client.waypoint.WaypointCreateScreen;
import com.minimals.client.waypoint.WaypointIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Waypoints: adds a "Set Waypoint" button to the death screen, right under vanilla's
 * "Title Screen" button, that marks the spot where the player died.
 *
 * Vanilla lays its buttons out at height/4 + 72 (Respawn) and height/4 + 96 (Title Screen),
 * both 200x20 and centred, so the slot at height/4 + 120 is free. init() runs again on every
 * window resize, so the button is rebuilt together with the vanilla ones.
 */
@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin extends Screen {

    @Shadow
    private LocalPlayer player;

    protected DeathScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minimals$addWaypointButton(CallbackInfo ci) {
        WaypointModule module = ModuleManager.waypoints();
        if (!module.isEnabled() || !module.deathButton.get() || player == null) {
            return;
        }

        // Position where the player died, read now (the player entity is replaced on respawn).
        int x = player.getBlockX();
        int y = player.getBlockY();
        int z = player.getBlockZ();
        Screen self = this;

        addRenderableWidget(Button.builder(Component.literal("Set Waypoint"), btn ->
                        Minecraft.getInstance().gui.setScreen(
                                new WaypointCreateScreen(self, x, y, z, "Death", WaypointIcon.DEATH)))
                .bounds(width / 2 - 100, height / 4 + 120, 200, 20).build());
    }
}
