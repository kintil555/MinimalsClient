package com.minimals.client.ui.hud;

import com.minimals.client.module.KeystrokeModule;
import com.minimals.client.module.ModuleManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * WASD + LMB/RMB CPS keystroke display, laid out like a typical keystroke overlay: W on top,
 * A/S/D below it, LMB/RMB (with CPS) below that.
 */
public class KeystrokeElement extends HudElement {

    private static final int KEY_SIZE = 20;
    private static final int GAP = 2;
    private static final int CLICK_H = 24;

    private static final int BG = 0x80808080;
    private static final int BG_ACTIVE = 0xC0A0A0A0;
    private static final int TEXT = 0xFFFFFFFF;

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
        return KEY_SIZE * 3 + GAP * 2;
    }

    @Override
    public int getHeight() {
        return KEY_SIZE * 2 + GAP + CLICK_H + GAP;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (mc.options == null) {
            return;
        }

        int colW = KEY_SIZE * 3 + GAP * 2;
        int wX = x + KEY_SIZE + GAP;

        drawKey(graphics, font, wX, y, "W", mc.options.keyUp.isDown());

        int row2Y = y + KEY_SIZE + GAP;
        drawKey(graphics, font, x, row2Y, "A", mc.options.keyLeft.isDown());
        drawKey(graphics, font, wX, row2Y, "S", mc.options.keyDown.isDown());
        drawKey(graphics, font, x + (KEY_SIZE + GAP) * 2, row2Y, "D", mc.options.keyRight.isDown());

        int clickY = row2Y + KEY_SIZE + GAP;
        int halfW = (colW - GAP) / 2;
        boolean leftDown = mc.mouseHandler != null && mc.mouseHandler.isLeftPressed();
        boolean rightDown = mc.mouseHandler != null && mc.mouseHandler.isRightPressed();

        drawClickBox(graphics, font, x, clickY, halfW, "LMB", module().getLeftCps(), leftDown);
        drawClickBox(graphics, font, x + halfW + GAP, clickY, colW - halfW - GAP, "RMB",
                module().getRightCps(), rightDown);
    }

    private static void drawKey(GuiGraphicsExtractor graphics, Font font, int x, int y, String label, boolean down) {
        graphics.fill(x, y, x + KEY_SIZE, y + KEY_SIZE, down ? BG_ACTIVE : BG);
        int textX = x + (KEY_SIZE - font.width(label)) / 2;
        int textY = y + (KEY_SIZE - 8) / 2;
        graphics.text(font, label, textX, textY, TEXT, true);
    }

    private static void drawClickBox(GuiGraphicsExtractor graphics, Font font, int x, int y, int width,
                                      String label, int cps, boolean down) {
        graphics.fill(x, y, x + width, y + CLICK_H, down ? BG_ACTIVE : BG);
        String cpsText = cps + " CPS";
        int labelX = x + (width - font.width(label)) / 2;
        int cpsX = x + (width - font.width(cpsText)) / 2;
        graphics.text(font, label, labelX, y + 2, TEXT, true);
        graphics.text(font, cpsText, cpsX, y + 2 + 9, TEXT, true);
    }
}
