package com.minimals.client.replay;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Spectator-style free camera for replays, active ONLY while the right mouse button is held.
 *
 * While held: the cursor is grabbed and the mouse turns the camera, and W/A/S/D + Space/Shift fly
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
    /** Last cursor position while the cursor is captured, to turn raw movement into rotation. */
    private static double lastX;
    private static double lastY;

    private ReplayFlyCamera() {
    }

    public static boolean isFlying() {
        return flying;
    }

    /**
     * Right button went down over the world: start flying.
     *
     * MouseHandler.grabMouse() would also close the open screen, so the timeline bar would vanish
     * and never see the button release. The cursor is captured through the same GLFW call it uses
     * (InputConstants.grabOrReleaseMouse) and the look delta is read here instead.
     */
    public static void begin(Minecraft mc) {
        if (flying || !ReplayPlayer.isInWorld()) {
            return;
        }
        flying = true;
        var window = mc.getWindow();
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(window.handle(), x, y);
        InputConstants.grabOrReleaseMouse(window, InputConstants.CURSOR_DISABLED, x[0], y[0]);
        lastX = x[0];
        lastY = y[0];
    }

    /** Right button released (or the replay ended): give the cursor back at the screen centre. */
    public static void end(Minecraft mc) {
        if (!flying) {
            return;
        }
        flying = false;
        var window = mc.getWindow();
        InputConstants.grabOrReleaseMouse(window, InputConstants.CURSOR_NORMAL,
                window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0);
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
        if (player == null || !ReplayPlayer.isInWorld() || !isRightDown(mc)) {
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
     * Turns the camera by the cursor movement since the last frame, with vanilla's sensitivity
     * curve. Called once per rendered frame (see ReplayLookMixin) so rotation stays smooth; the
     * 20 Hz client tick is only used for movement.
     */
    public static void lookFrame(Minecraft mc) {
        Player player = mc.player;
        if (!flying || player == null) {
            return;
        }
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().handle(), x, y);
        double dx = x[0] - lastX;
        double dy = y[0] - lastY;
        lastX = x[0];
        lastY = y[0];
        if (dx == 0.0 && dy == 0.0) {
            return;
        }
        double ss = mc.options.sensitivity().get() * 0.6F + 0.2F;
        double sens = ss * ss * ss * 8.0;
        double xo = dx * sens * (mc.options.invertMouseX().get() ? -1.0 : 1.0);
        double yo = dy * sens * (mc.options.invertMouseY().get() ? -1.0 : 1.0);
        // Same maths as Entity.turn, minus the vehicle callback.
        player.setYRot(player.getYRot() + (float) xo * 0.15F);
        player.setXRot(Mth.clamp(player.getXRot() + (float) yo * 0.15F, -90.0F, 90.0F));
        player.setOldPosAndRot();
    }

    /**
     * GLFW polls mouse buttons separately from keys (InputConstants.isKeyDown is glfwGetKey and is
     * never true for a mouse button), so ask GLFW for the button directly.
     */
    private static boolean isRightDown(Minecraft mc) {
        return GLFW.glfwGetMouseButton(mc.getWindow().handle(), InputConstants.MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }
}
