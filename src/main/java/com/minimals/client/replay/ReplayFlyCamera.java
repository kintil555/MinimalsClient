package com.minimals.client.replay;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Spectator-style free camera for replays, active ONLY while the right mouse button is held.
 *
 * While held: the mouse is grabbed (vanilla) and turns the camera, and W/A/S/D + Space/Shift fly
 * it (Ctrl = faster). When released the cursor is free again so the timeline bar can be used.
 *
 * Movement is applied to the local player's position directly. In a replay the player is a
 * client-only spectator (see ReplayGameModeMixin) and every packet it sends is dropped by the
 * fake server, so nothing here can leak to a real server. Only GLFW window input is used (cursor
 * mode, cursor position, key and button polling): no GL/Vulkan calls, so it behaves the same on
 * both render back ends.
 */
public final class ReplayFlyCamera {

    private static final double BASE_SPEED = 0.9;   // blocks per client tick (20 Hz)
    private static final double FAST_MULTIPLIER = 3.0;

    private static boolean flying;

    private ReplayFlyCamera() {
    }

    public static boolean isFlying() {
        return flying;
    }

    /**
     * Right button went down over the world: start flying.
     *
     * The editor screen is closed and the mouse is grabbed the vanilla way. That makes vanilla do
     * everything for us: MouseHandler.turnPlayer rotates the camera (the old hand-rolled look read
     * glfwGetCursorPos, which does not move while the cursor is disabled, so the camera never
     * turned), the crosshair appears because no screen is open, and ESC opens the pause screen.
     */
    public static void begin(Minecraft mc) {
        if (flying || !ReplayPlayer.isInWorld()) {
            return;
        }
        // grabMouse() also closes the open screen, whose removed() would call end(): the flag stays
        // off until both are done so that callback is a no-op.
        mc.gui.setScreen((Screen) null);
        mc.mouseHandler.grabMouse();
        flying = mc.mouseHandler.isMouseGrabbed();
    }

    /**
     * Right button released, the camera can no longer be flown, or the replay ended. Frees the
     * cursor and (unless the replay is over or another screen took over) reopens the editor.
     */
    public static void end(Minecraft mc) {
        if (!flying) {
            return;
        }
        flying = false;
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
        if (ReplayPlayer.isInWorld() && mc.gui.screen() == null) {
            mc.gui.setScreen(new TimelineScreen());
        }
    }

    /**
     * Called every client tick. Also acts as a safety net: if the button is no longer down
     * (released outside the window, alt-tab) we stop.
     */
    public static void tick(Minecraft mc) {
        if (!flying) {
            return;
        }
        Player player = mc.player;
        if (player == null || !ReplayPlayer.isInWorld()) {
            flying = false;
            return;
        }
        if (mc.gui.screen() != null) {
            // ESC opened the pause screen (or something else): leave fly mode without reopening
            // the editor on top of it. The editor comes back once that screen closes.
            flying = false;
            return;
        }
        if (!isRightDown(mc)) {
            end(mc);
            return;
        }

        var window = mc.getWindow();
        boolean w = InputConstants.isKeyDown(window, InputConstants.KEY_W);
        boolean s = InputConstants.isKeyDown(window, InputConstants.KEY_S);
        boolean a = InputConstants.isKeyDown(window, InputConstants.KEY_A);
        boolean d = InputConstants.isKeyDown(window, InputConstants.KEY_D);
        boolean up = InputConstants.isKeyDown(window, InputConstants.KEY_SPACE);
        boolean down = InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT);
        boolean fast = InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL);

        double forward = (w ? 1 : 0) - (s ? 1 : 0);
        double strafe = (a ? 1 : 0) - (d ? 1 : 0);
        double vertical = (up ? 1 : 0) - (down ? 1 : 0);
        if (forward == 0 && strafe == 0 && vertical == 0) {
            return;
        }

        // Forward follows the full look direction (incl. pitch), like spectator flight.
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float pitch = player.getXRot() * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(
                -Mth.sin(yaw) * Mth.cos(pitch),
                -Mth.sin(pitch),
                Mth.cos(yaw) * Mth.cos(pitch));
        Vec3 left = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));

        Vec3 dir = facing.scale(forward).add(left.scale(strafe)).add(0.0, vertical, 0.0);
        if (dir.lengthSqr() > 1.0E-7) {
            dir = dir.normalize();
        }
        double speed = BASE_SPEED * (fast ? FAST_MULTIPLIER : 1.0);
        Vec3 next = player.position().add(dir.scale(speed));

        player.setOldPosAndRot();
        player.setPos(next.x, next.y, next.z);
        player.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * GLFW polls mouse buttons separately from keys (InputConstants.isKeyDown is glfwGetKey and is
     * never true for a mouse button), so ask GLFW for the button directly.
     */
    private static boolean isRightDown(Minecraft mc) {
        return GLFW.glfwGetMouseButton(mc.getWindow().handle(), InputConstants.MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }
}
