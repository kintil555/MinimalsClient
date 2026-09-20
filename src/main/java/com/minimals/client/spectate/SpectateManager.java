package com.minimals.client.spectate;

import net.minecraft.client.KeyMapping;
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
    private static KeyMapping exitKey;

    private SpectateManager() {
    }

    public static void setExitKey(KeyMapping key) {
        exitKey = key;
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

        // Vanilla gameplay keys are dead while spectating: release held ones and drop queued
        // presses so nothing fires (drop item, swap hand, attack/use, WASD, jump, sneak...).
        blockVanillaInput(mc);

        // Own keybind (default Q). consumeClick() is per-KeyMapping, so it can never
        // interfere with the vanilla Drop Item binding even though both default to Q.
        if (exitKey != null && exitKey.consumeClick() && mc.gui.screen() == null) {
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

    private static void blockVanillaInput(Minecraft mc) {
        var o = mc.options;
        KeyMapping[] blocked = {
                o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint,
                o.keyDrop, o.keySwapOffhand, o.keyUse, o.keyAttack, o.keyPickItem
        };
        for (KeyMapping key : blocked) {
            key.setDown(false);
            while (key.consumeClick()) {
                // discard queued presses
            }
        }
        for (KeyMapping hotbar : o.keyHotbarSlots) {
            while (hotbar.consumeClick()) {
                // discard hotbar slot switches too
            }
        }
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
