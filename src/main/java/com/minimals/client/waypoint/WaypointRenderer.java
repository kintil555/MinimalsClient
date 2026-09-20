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

    private static final Map<Long, Label> LABELS = new HashMap<>();
    /** Set whenever the waypoint list could have changed, so stale cache entries are pruned. */
    private static int lastListIdentity;

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
        Vec3 playerPos = mc.player.position();

        double maxDist = module.maxDistance.get();
        double maxDistSqr = maxDist * maxDist;
        int guiW = graphics.guiWidth();
        int guiH = graphics.guiHeight();
        float scale = module.scale.get() / 100f;
        Font font = mc.font;

        for (Waypoint w : waypoints) {
            double cx = w.x() + 0.5;
            double cy = w.y() + 0.5;
            double cz = w.z() + 0.5;

            double dx = cx - playerPos.x;
            double dy = cy - playerPos.y;
            double dz = cz - playerPos.z;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > maxDistSqr) {
                continue;
            }

            // In front of the camera? One dot product against the camera's forward axis.
            double vx = cx - cameraPos.x;
            double vy = cy - cameraPos.y;
            double vz = cz - cameraPos.z;
            if (vx * forward.x() + vy * forward.y() + vz * forward.z() <= 0.05) {
                continue;
            }

            Vec3 ndc = mc.gameRenderer.projectPointToScreen(new Vec3(cx, cy, cz));
            float sx = ndcToScreenX(ndc.x, guiW);
            float sy = ndcToScreenY(ndc.y, guiH);
            if (sx < EDGE || sx > guiW - EDGE || sy < EDGE || sy > guiH - EDGE) {
                continue;
            }

            int distance = (int) Math.round(Math.sqrt(distSqr));
            Label label = LABELS.computeIfAbsent(w.id(), id -> new Label());
            if (label.distance != distance) {
                label.distance = distance;
                label.distanceText = distance + "m";
                label.coordsText = w.x() + ", " + w.y() + ", " + w.z();
            }

            drawMarker(graphics, font, module, w, label, sx, sy, scale);
        }
    }

    private static void drawMarker(GuiGraphicsExtractor graphics, Font font, WaypointModule module,
                                   Waypoint w, Label label, float sx, float sy, float scale) {
        int lineH = font.lineHeight;
        int iconColor = ARGB.opaque(w.color());

        graphics.pose().pushMatrix();
        graphics.pose().translate(sx, sy);
        graphics.pose().scale(scale, scale);

        // Icon centred on the projected point.
        int iconX = -ICON_DRAW / 2;
        int iconY = -ICON_DRAW / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, w.icon().texture, iconX, iconY,
                0f, 0f, ICON_DRAW, ICON_DRAW, ICON_DRAW, ICON_DRAW, iconColor);

        // Text block under the icon: name, then distance, then coordinates.
        int textY = iconY + ICON_DRAW + PAD;
        if (module.showName.get() && !w.name().isEmpty()) {
            textY = drawCentered(graphics, font, w.name(), textY, iconColor, lineH);
        }
        if (module.showDistance.get()) {
            textY = drawCentered(graphics, font, label.distanceText, textY, TEXT_PRIMARY, lineH);
        }
        if (module.showCoords.get()) {
            drawCentered(graphics, font, label.coordsText, textY, TEXT_SECONDARY, lineH);
        }

        graphics.pose().popMatrix();
    }

    /** Draws one line centred on x=0 over a soft backdrop and returns the y of the next line. */
    private static int drawCentered(GuiGraphicsExtractor graphics, Font font, String text, int y, int color, int lineH) {
        int w = font.width(text);
        int x = -w / 2;
        graphics.fill(x - 2, y - 1, x + w + 2, y + lineH, BACKDROP);
        graphics.text(font, text, x, y, color, false);
        return y + lineH + LINE_GAP;
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
        int identity = System.identityHashCode(current);
        if (identity == lastListIdentity) {
            return;
        }
        lastListIdentity = identity;
        LABELS.keySet().removeIf(id -> {
            for (Waypoint w : current) {
                if (w.id() == id) {
                    return false;
                }
            }
            return true;
        });
    }
}
