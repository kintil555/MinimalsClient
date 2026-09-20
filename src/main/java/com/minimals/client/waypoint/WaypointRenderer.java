package com.minimals.client.waypoint;

import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.WaypointModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws waypoints as screen-space HUD overlays: each one is projected from its world position to
 * the screen and drawn as an icon with its name, distance and coordinates.
 *
 * Cost per frame (the reason this is cheap enough to leave on):
 * <ul>
 *   <li>Waypoints of other dimensions are never seen here - {@link WaypointManager#visible()}
 *       is pre-filtered and cached.</li>
 *   <li>Distance culling uses squared distance, so {@code sqrt} only runs for waypoints that
 *       survive it.</li>
 *   <li>Behind-camera waypoints are rejected with one dot product before any matrix maths.</li>
 *   <li>The distance and coordinate strings are cached per waypoint and rebuilt only when the
 *       whole-number distance actually changes (at most once per block moved), so a steady frame
 *       allocates no strings.</li>
 * </ul>
 */
public final class WaypointRenderer {

    private static final int ICON_DRAW = WaypointIcon.SIZE;
    private static final int LINE_GAP = 1;
    private static final int PAD = 2;
    /** Keeps markers off the very edge of the screen so they never half-hide. */
    private static final int EDGE = 12;

    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFC8C8C8;
    private static final int BACKDROP = 0x66000000;

    /** Cached label lines for one waypoint at a given whole-number distance. */
    private static final class Label {
        int distance = Integer.MIN_VALUE;
        String distanceText = "";
        String coordsText = "";
    }

    /** Distance in pixels over which a marker fades out before the screen edge. */
    private static final float EDGE_FADE = 24f;

    /** Scratch objects reused every frame; only the client thread renders. */
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Vector3f PROJECTED = new Vector3f();

    private static final Map<Long, Label> LABELS = new HashMap<>();
    /** Set whenever the waypoint list could have changed, so stale cache entries are pruned. */
    private static List<Waypoint> lastList;

    private WaypointRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        WaypointModule module = ModuleManager.waypoints();
        if (!module.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // F1 needs no check here: Gui skips Hud.extractRenderState entirely while the HUD is
        // hidden, and this renderer is attached to the Hud through HudElementRegistry.
        if (mc.player == null || mc.level == null) {
            return;
        }

        List<Waypoint> waypoints = WaypointManager.visible();
        if (waypoints.isEmpty()) {
            return;
        }
        pruneCache(waypoints);

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Vector3fc forward = camera.forwardVector();

        double maxDist = module.maxDistance.get();
        double maxDistSqr = maxDist * maxDist;
        int guiW = graphics.guiWidth();
        int guiH = graphics.guiHeight();
        float scale = module.scale.get() / 100f;
        Font font = mc.font;
        // Built once per frame; the camera does not change between waypoints.
        Matrix4f viewProj = camera.getViewRotationProjectionMatrix(VIEW_PROJ);

        for (Waypoint w : waypoints) {
            double cx = w.x() + 0.5;
            double cy = w.y() + 0.5;
            double cz = w.z() + 0.5;

            // Everything below is measured from the camera, which is interpolated per frame.
            // The player position only changes every tick (20 Hz), so using it made the
            // distance text jump while the icon moved smoothly.
            double vx = cx - cameraPos.x;
            double vy = cy - cameraPos.y;
            double vz = cz - cameraPos.z;
            double distSqr = vx * vx + vy * vy + vz * vz;
            if (distSqr > maxDistSqr) {
                continue;
            }

            // In front of the camera? One dot product against the camera's forward axis.
            if (vx * forward.x() + vy * forward.y() + vz * forward.z() <= 0.05) {
                continue;
            }

            // Same maths as GameRenderer.projectPointToScreen, but with one shared vector
            // instead of a new Vec3 + Vector3f per waypoint per frame.
            PROJECTED.set((float) vx, (float) vy, (float) vz);
            viewProj.transformProject(PROJECTED);
            float sx = ndcToScreenX(PROJECTED.x, guiW);
            float sy = ndcToScreenY(PROJECTED.y, guiH);

            // Fade out over the last EDGE_FADE pixels instead of popping off at the border.
            float edgeAlpha = edgeAlpha(sx, sy, guiW, guiH);
            if (edgeAlpha <= 0f) {
                continue;
            }

            int distance = (int) Math.round(Math.sqrt(distSqr));
            Label label = LABELS.get(w.id());
            if (label == null) {
                label = new Label();
                LABELS.put(w.id(), label);
            }
            if (label.distance != distance) {
                label.distance = distance;
                label.distanceText = distance + "m";
                label.coordsText = w.x() + ", " + w.y() + ", " + w.z();
            }

            drawMarker(graphics, font, module, w, label, sx, sy, scale, edgeAlpha);
        }
    }

    private static void drawMarker(GuiGraphicsExtractor graphics, Font font, WaypointModule module,
                                   Waypoint w, Label label, float sx, float sy, float scale, float alpha) {
        int lineH = font.lineHeight;
        int iconColor = ARGB.color(Math.round(alpha * 255f), w.color());
        int primary = ARGB.multiplyAlpha(TEXT_PRIMARY, alpha);
        int secondary = ARGB.multiplyAlpha(TEXT_SECONDARY, alpha);
        int backdrop = ARGB.multiplyAlpha(BACKDROP, alpha);

        graphics.pose().pushMatrix();
        // Whole pixels only. Icon and text are drawn on an integer grid, so a fractional
        // translate makes them land on different sub-pixels frame to frame and shimmer.
        graphics.pose().translate(Math.round(sx), Math.round(sy));
        graphics.pose().scale(scale, scale);

        // Icon centred on the projected point.
        int iconX = -ICON_DRAW / 2;
        int iconY = -ICON_DRAW / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, w.icon().texture, iconX, iconY,
                0f, 0f, ICON_DRAW, ICON_DRAW, ICON_DRAW, ICON_DRAW, iconColor);

        // Text block under the icon: name, then distance, then coordinates.
        int textY = iconY + ICON_DRAW + PAD;
        if (module.showName.get() && !w.name().isEmpty()) {
            textY = drawCentered(graphics, font, w.name(), textY, iconColor, lineH, backdrop);
        }
        if (module.showDistance.get()) {
            textY = drawCentered(graphics, font, label.distanceText, textY, primary, lineH, backdrop);
        }
        if (module.showCoords.get()) {
            drawCentered(graphics, font, label.coordsText, textY, secondary, lineH, backdrop);
        }

        graphics.pose().popMatrix();
    }

    /** Draws one line centred on x=0 over a soft backdrop and returns the y of the next line. */
    private static int drawCentered(GuiGraphicsExtractor graphics, Font font, String text, int y, int color,
                                    int lineH, int backdrop) {
        int w = font.width(text);
        int x = -w / 2;
        graphics.fill(x - 2, y - 1, x + w + 2, y + lineH, backdrop);
        graphics.text(font, text, x, y, color, false);
        return y + lineH + LINE_GAP;
    }

    /** 1 in the middle of the screen, fading to 0 at EDGE pixels from the border. */
    private static float edgeAlpha(float sx, float sy, int guiW, int guiH) {
        float nearest = Math.min(Math.min(sx, guiW - sx), Math.min(sy, guiH - sy)) - EDGE;
        if (nearest <= 0f) {
            return 0f;
        }
        return nearest >= EDGE_FADE ? 1f : nearest / EDGE_FADE;
    }

    /** NDC x in [-1, 1] (left to right) to GUI pixels. */
    static float ndcToScreenX(double ndcX, int guiWidth) {
        return (float) ((ndcX * 0.5 + 0.5) * guiWidth);
    }

    /** NDC y in [-1, 1] (bottom to top) to GUI pixels (top to bottom, hence the flip). */
    static float ndcToScreenY(double ndcY, int guiHeight) {
        return (float) ((0.5 - ndcY * 0.5) * guiHeight);
    }

    /** Drops cached labels of waypoints that no longer exist (deleted, other world/dimension). */
    private static void pruneCache(List<Waypoint> current) {
        // WaypointManager publishes a new immutable list whenever anything changes, so a
        // reference check is exact. (identityHashCode can collide and skip a prune.)
        if (current == lastList) {
            return;
        }
        lastList = current;
        java.util.Set<Long> alive = new java.util.HashSet<>();
        for (Waypoint w : current) {
            alive.add(w.id());
        }
        LABELS.keySet().retainAll(alive);
    }
}
