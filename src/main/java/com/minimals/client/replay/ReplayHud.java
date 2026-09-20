package com.minimals.client.replay;

import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Fixed (not draggable, not in the HUD editor) recording indicator: a red dot and the elapsed time,
 * top-left. While a replay plays it shows a small "REPLAY" tag instead.
 */
public final class ReplayHud {

    private static final int RED = 0xFFE23B3B;

    private ReplayHud() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (ReplayRecorder.isRecording()) {
            // Blink the dot once a second so it is obviously live.
            boolean on = (System.currentTimeMillis() / 500) % 2 == 0;
            String text = "REC " + clock(ReplayRecorder.recordedTicks());
            int w = UiRenderer.textWidth(text) + 22;
            box(graphics, 6, 6, 6 + w, 22);
            if (on) {
                graphics.fill(12, 11, 18, 17, RED);
            }
            UiRenderer.text(graphics, text, 22, 10, 0xFFFFFFFF);
        } else if (ReplayPlayer.isActive() && ReplayPlayer.isInWorld()) {
            String text = "REPLAY " + clock(ReplayPlayer.positionTicks());
            int w = UiRenderer.textWidth(text) + 14;
            box(graphics, 6, 6, 6 + w, 22);
            UiRenderer.text(graphics, text, 13, 10, 0xFFFFFFFF);
        }
    }

    private static void box(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
        UiRenderer.roundedRect(graphics, x1, y1, x2, y2, 4, 0xB0141417);
    }

    private static String clock(int ticks) {
        int s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
