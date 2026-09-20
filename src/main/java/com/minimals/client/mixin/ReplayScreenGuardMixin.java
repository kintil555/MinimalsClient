package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import com.minimals.client.replay.TimelineScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Same goal as Flashback's MixinClientPacketListener, but generic instead of one hook per packet.
 *
 * The recorded server opens GUIs on the recorded player (sign editor, chests, books, villager
 * trades, dialogs, mod menus...). In a replay the LocalPlayer is only a free camera, so any of
 * those covers the editor and steals the mouse. Rather than listing every packet, a replay
 * accepts only the screens that belong to it: everything else opened while a replay is active is
 * ignored. Recorded packets that would open a screen therefore do nothing.
 */
@Mixin(Gui.class)
public abstract class ReplayScreenGuardMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void minimals$guard(Screen screen, CallbackInfo ci) {
        if (screen == null || !ReplayPlayer.isActive()) {
            return; // closing a screen is always fine
        }
        if (screen instanceof TimelineScreen
                || screen instanceof PauseScreen
                || screen instanceof LevelLoadingScreen
                || screen instanceof GenericMessageScreen
                || screen instanceof TitleScreen) {
            return;
        }
        // Screens from our own package (list/menu) and anything else the player opened by hand
        // (keybind screens such as the mod menu) stay allowed; only game-driven GUIs are dropped.
        if (screen.getClass().getName().startsWith("com.minimals.")) {
            return;
        }
        if (minimals$openedByRecordedPacket()) {
            ci.cancel();
        }
    }

    /**
     * True while a clientbound packet handler is running: those run on the main thread from the
     * connection's packet processor, with ClientPacketListener/ClientCommonPacketListenerImpl or a
     * PacketUtils frame on the stack. A screen the user opens with a key comes from a tick/input
     * handler and never has one.
     */
    private static boolean minimals$openedByRecordedPacket() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String c = e.getClassName();
            if (c.equals("net.minecraft.client.multiplayer.ClientPacketListener")
                    || c.equals("net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl")
                    || c.equals("net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl")) {
                return true;
            }
        }
        return false;
    }
}
