package com.minimals.client.spectate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Client-side player spectate. Only the CAMERA moves ({@link Minecraft#setCameraEntity});
 * the local player body stays where it is. Because vanilla LocalPlayer checks
 * {@code getCameraEntity() == this} before applying input / sending movement, the body is
 * frozen automatically while spectating.
 */
public final class SpectateManager {

    private static UUID targetId;
    /** Ticks spent waiting for the target entity/chunk to exist on the client. */
    private static int loadingTicks;
    private static boolean cameraApplied;

    private SpectateManager() {
    }

    public static boolean isSpectating() {
        return targetId != null;
    }

    public static UUID targetId() {
        return targetId;
    }

    /** True while waiting for the target's chunk/entity to be available. */
    public static boolean isLoading() {
        return isSpectating() && !cameraApplied;
    }

    public static void start(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || id.equals(mc.player.getUUID())) {
            return;
        }
        targetId = id;
        loadingTicks = 0;
        cameraApplied = false;
        // Ask the integrated server (singleplayer) to send the target's chunk.
        SpectateChunkLoader.request(id);
        tryApplyCamera(mc);
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        boolean wasSpectating = isSpectating();
        targetId = null;
        cameraApplied = false;
        loadingTicks = 0;
        SpectateChunkLoader.release();
        if (wasSpectating && mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
    }

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        if (!isSpectating()) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            targetId = null;
            cameraApplied = false;
            SpectateChunkLoader.release();
            return;
        }

        // Q exits, but never while a screen is open (typing "q" in the search box, chat, ...).
        if (mc.gui.screen() == null && com.mojang.blaze3d.platform.InputConstants
                .isKeyDown(mc.getWindow(), com.mojang.blaze3d.platform.InputConstants.KEY_Q)) {
            stop();
            return;
        }

        SpectateChunkLoader.tick(mc);

        Entity current = mc.getCameraEntity();
        AbstractClientPlayer target = find(mc.level, targetId);
        if (target == null) {
            // Target left render range (multiplayer) or disconnected: fall back to own view
            // but keep waiting; if it never comes back the user can press Q.
            if (cameraApplied) {
                cameraApplied = false;
                mc.setCameraEntity(mc.player);
            }
            loadingTicks++;
            return;
        }
        if (current != target) {
            mc.setCameraEntity(target);
        }
        cameraApplied = true;
    }

    private static void tryApplyCamera(Minecraft mc) {
        AbstractClientPlayer target = find(mc.level, targetId);
        if (target != null) {
            mc.setCameraEntity(target);
            cameraApplied = true;
        }
    }

    public static AbstractClientPlayer find(ClientLevel level, UUID id) {
        for (AbstractClientPlayer p : level.players()) {
            if (p.getUUID().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public static int loadingTicks() {
        return loadingTicks;
    }
}
