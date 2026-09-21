package com.minimals.client.ui.hud;

import com.minimals.client.module.KeystrokeModule;
import com.minimals.client.module.ModuleManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * WASD keystroke display: W on top, A/S/D below, optional Shift and Space bars, and optional
 * LMB/RMB boxes with CPS. Spacing, idle/pressed colours and which rows show are all module
 * settings; overall size is the generic HUD-editor scale.
 */
public class KeystrokeElement extends HudElement {

    private static final int KEY_SIZE = 20;
    private static final int CLICK_H = 24;
    private static final int BAR_H = 14;

    public KeystrokeElement() {
        super("keystrokes", "Keystrokes", 0.02f, 0.55f);
    }

    private static KeystrokeModule module() {
        return ModuleManager.keystrokes();
    }

    @Override
    public boolean isActive() {
        return module().isEnabled();
    }

    @Override
    public int getWidth() {
        int gap = module().spacing.get();
        return KEY_SIZE * 3 + gap * 2;
    }

    @Override
    public int getHeight() {
        KeystrokeModule m = module();
        int gap = m.spacing.get();
        int h = KEY_SIZE * 2 + gap;
        if (m.showMouse.get()) {
            h += gap + CLICK_H;
        }
        if (m.showShift.get()) {
            h += gap + BAR_H;
        }
        if (m.showSpace.get()) {
            h += gap + BAR_H;
        }
        return h;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }
        Font font = mc.font;
        KeystrokeModule m = module();
        int gap = m.spacing.get();
        int colW = getWidth();
        int wX = x + KEY_SIZE + gap;

        drawKey(graphics, font, m, wX, y, KEY_SIZE, KEY_SIZE, "W", mc.options.keyUp);

        int row2Y = y + KEY_SIZE + gap;
        drawKey(graphics, font, m, x, row2Y, KEY_SIZE, KEY_SIZE, "A", mc.options.keyLeft);
        drawKey(graphics, font, m, wX, row2Y, KEY_SIZE, KEY_SIZE, "S", mc.options.keyDown);
        drawKey(graphics, font, m, x + (KEY_SIZE + gap) * 2, row2Y, KEY_SIZE, KEY_SIZE, "D", mc.options.keyRight);

        int cursorY = row2Y + KEY_SIZE + gap;

        if (m.showMouse.get()) {
            int halfW = (colW - gap) / 2;
            boolean leftDown = mc.mouseHandler != null && mc.mouseHandler.isLeftPressed();
            boolean rightDown = mc.mouseHandler != null && mc.mouseHandler.isRightPressed();
            drawClickBox(graphics, font, m, x, cursorY, halfW, "LMB", m.getLeftCps(), leftDown);
            drawClickBox(graphics, font, m, x + halfW + gap, cursorY, colW - halfW - gap, "RMB",
                    m.getRightCps(), rightDown);
            cursorY += CLICK_H + gap;
        }
        if (m.showShift.get()) {
            drawKey(graphics, font, m, x, cursorY, colW, BAR_H, "Shift", mc.options.keyShift);
            cursorY += BAR_H + gap;
        }
        if (m.showSpace.get()) {
            drawKey(graphics, font, m, x, cursorY, colW, BAR_H, "Space", mc.options.keyJump);
        }
    }

    private static void drawKey(GuiGraphicsExtractor graphics, Font font, KeystrokeModule m, int x, int y,
                                int w, int h, String label, KeyMapping key) {
        graphics.fill(x, y, x + w, y + h, m.fillColor(key.isDown()));
        int textX = x + (w - font.width(label)) / 2;
        int textY = y + (h - 8) / 2;
        graphics.text(font, label, textX, textY, 0xFF000000 | m.textColor.get(), true);
    }

    private static void drawClickBox(GuiGraphicsExtractor graphics, Font font, KeystrokeModule m, int x, int y,
                                     int width, String label, int cps, boolean down) {
        graphics.fill(x, y, x + width, y + CLICK_H, m.fillColor(down));
        int color = 0xFF000000 | m.textColor.get();
        String cpsText = cps + " CPS";
        graphics.text(font, label, x + (width - font.width(label)) / 2, y + 2, color, true);
        graphics.text(font, cpsText, x + (width - font.width(cpsText)) / 2, y + 2 + 9, color, true);
    }
}
