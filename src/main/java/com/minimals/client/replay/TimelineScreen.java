package com.minimals.client.replay;

import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Replay controls, shown as a bar along the bottom of the screen: play/pause, speed, a scrubbable
 * timeline and a FOV slider. It is a screen (so the mouse is free) that does not pause the game.
 * Hold the right mouse button over the world to fly the camera (see ReplayFlyCamera).
 */
public class TimelineScreen extends Screen {

    private static final int BAR_H = 64;
    private static final int PAD = 12;
    private static final double[] SPEEDS = {0.25, 0.5, 1.0, 2.0, 4.0, 8.0};

    private boolean scrubbing;
    private boolean fovDragging;
    /** Tick under the cursor while scrubbing; applied on release (seeking rebuilds the world). */
    private int scrubTick;

    public TimelineScreen() {
        super(Component.literal("Replay"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    // ---- layout ---------------------------------------------------------------------------

    private int barTop() {
        return height - BAR_H;
    }

    private int trackX1() {
        return PAD + 28 + 6;
    }

    private int trackX2() {
        return width - PAD - 150;
    }

    private int trackY() {
        return barTop() + 16;
    }

    private int speedX() {
        return width - PAD - 140;
    }

    private int fovX() {
        return width - PAD - 140;
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int tickAt(double mouseX) {
        double f = (mouseX - trackX1()) / (double) (trackX2() - trackX1());
        f = Math.max(0.0, Math.min(1.0, f));
        return (int) Math.round(f * ReplayPlayer.totalTicks());
    }

    // ---- render ---------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // No super: vanilla would dim the whole world behind the screen, hiding the replay.
        int top = barTop();
        UiRenderer.roundedRect(graphics, 6, top, width - 6, height - 6, 6, 0xE0141417);

        // Play / pause
        boolean playHover = in(mouseX, mouseY, PAD, top + 10, 28, 20);
        UiRenderer.roundedRect(graphics, PAD, top + 10, PAD + 28, top + 30, 4,
                playHover ? 0xFF9F75FF : UiRenderer.ACCENT);
        UiRenderer.text(graphics, ReplayPlayer.isPaused() ? ">" : "||", PAD + 10, top + 16, 0xFFFFFFFF);

        // Track
        int x1 = trackX1();
        int x2 = trackX2();
        int ty = trackY();
        int total = Math.max(1, ReplayPlayer.totalTicks());
        int shown = scrubbing ? scrubTick : ReplayPlayer.positionTicks();
        graphics.fill(x1, ty + 4, x2, ty + 8, 0xFF3A3A42);
        int px = x1 + (int) ((long) (x2 - x1) * shown / total);
        graphics.fill(x1, ty + 4, px, ty + 8, UiRenderer.ACCENT);
        UiRenderer.roundedRect(graphics, px - 4, ty, px + 4, ty + 12, 3, 0xFFFFFFFF);

        String time = clock(shown) + " / " + clock(ReplayPlayer.totalTicks());
        UiRenderer.text(graphics, time, x1, ty + 20, UiRenderer.TEXT_SECONDARY);
        UiRenderer.text(graphics, ReplayPlayer.name(), x1 + UiRenderer.textWidth(time) + 12, ty + 20,
                UiRenderer.TEXT_SECONDARY);

        // Speed buttons
        int sx = speedX();
        UiRenderer.text(graphics, "Speed", sx, top + 8, UiRenderer.TEXT_SECONDARY);
        int bw = 22;
        for (int i = 0; i < SPEEDS.length; i++) {
            int bx = sx + i * (bw + 1);
            boolean on = Math.abs(ReplayPlayer.speed() - SPEEDS[i]) < 0.001;
            boolean hov = in(mouseX, mouseY, bx, top + 18, bw, 14);
            UiRenderer.roundedRect(graphics, bx, top + 18, bx + bw, top + 32, 3,
                    on ? UiRenderer.ACCENT : (hov ? UiRenderer.HEADER_BTN_BG_HOVER : UiRenderer.HEADER_BTN_BG));
            String label = SPEEDS[i] < 1 ? Double.toString(SPEEDS[i]).replace("0.", ".") : ((int) SPEEDS[i]) + "x";
            UiRenderer.text(graphics, label, bx + (bw - UiRenderer.textWidth(label)) / 2, top + 21, 0xFFFFFFFF);
        }

        // FOV slider + reset
        int fx = fovX();
        String fovLabel = ReplayView.isFovOverride() ? "FOV " + (int) ReplayView.fov() : "FOV (game)";
        UiRenderer.text(graphics, fovLabel, fx, top + 38, UiRenderer.TEXT_SECONDARY);
        int sx1 = fx + 62;
        int sx2 = fx + 138;
        graphics.fill(sx1, top + 42, sx2, top + 44, 0xFF3A3A42);
        double f = (ReplayView.fov() - ReplayView.FOV_MIN) / (ReplayView.FOV_MAX - ReplayView.FOV_MIN);
        int kx = sx1 + (int) ((sx2 - sx1) * f);
        UiRenderer.roundedRect(graphics, kx - 3, top + 38, kx + 3, top + 48, 2,
                ReplayView.isFovOverride() ? UiRenderer.ACCENT : 0xFF6A6A72);

        // Hint
        UiRenderer.text(graphics, "Hold RMB + WASD/Space/Shift: fly   Space: play/pause   Left/Right: 5s   Scroll: FOV   Esc: close", PAD, top + 44,
                UiRenderer.TEXT_SECONDARY);
    }

    private static String clock(int ticks) {
        int s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ---- input ----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        int top = barTop();
        if (in(mx, my, PAD, top + 10, 28, 20)) {
            ReplayPlayer.setPaused(!ReplayPlayer.isPaused());
            return true;
        }
        if (in(mx, my, trackX1() - 4, trackY() - 2, trackX2() - trackX1() + 8, 16)) {
            scrubbing = true;
            scrubTick = tickAt(mx);
            return true;
        }
        int sx = speedX();
        for (int i = 0; i < SPEEDS.length; i++) {
            if (in(mx, my, sx + i * 23, top + 18, 22, 14)) {
                ReplayPlayer.setSpeed(SPEEDS[i]);
                return true;
            }
        }
        int sx1 = fovX() + 62;
        int sx2 = fovX() + 138;
        if (in(mx, my, sx1 - 4, top + 36, sx2 - sx1 + 8, 14)) {
            fovDragging = true;
            setFovFrom(mx);
            return true;
        }
        // Right-click on the FOV label resets to the game's own FOV.
        if (event.button() == 1 && in(mx, my, fovX(), top + 36, 60, 12)) {
            ReplayView.setFovOverride(false);
            return true;
        }
        // Right button held over the world (anywhere above the bar): fly the camera.
        if (event.button() == 1 && my < top) {
            ReplayFlyCamera.begin(Minecraft.getInstance());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void setFovFrom(double mouseX) {
        int sx1 = fovX() + 62;
        int sx2 = fovX() + 138;
        double f = Math.max(0.0, Math.min(1.0, (mouseX - sx1) / (double) (sx2 - sx1)));
        ReplayView.setFovOverride(true);
        ReplayView.setFov(ReplayView.FOV_MIN + f * (ReplayView.FOV_MAX - ReplayView.FOV_MIN));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (scrubbing) {
            scrubTick = tickAt(event.x());
            return true;
        }
        if (fovDragging) {
            setFovFrom(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 1 && ReplayFlyCamera.isFlying()) {
            ReplayFlyCamera.end(Minecraft.getInstance());
            return true;
        }
        if (scrubbing) {
            scrubbing = false;
            ReplayPlayer.seek(scrubTick);
            return true;
        }
        fovDragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ReplayView.addFov(-scrollY * 2.0);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE) {
            ReplayPlayer.setPaused(!ReplayPlayer.isPaused());
            return true;
        }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT) {
            ReplayPlayer.seek(ReplayPlayer.positionTicks() - 100);
            return true;
        }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) {
            ReplayPlayer.seek(ReplayPlayer.positionTicks() + 100);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        ReplayFlyCamera.end(Minecraft.getInstance());
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen((Screen) null);
    }
}
