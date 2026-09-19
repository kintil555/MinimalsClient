package com.minimals.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class MenuScreen extends Screen {

    private static final int BOX_W = 200;
    private static final int BOX_H = 120;
    private static final int RADIUS = 12;
    private static final int BG_COLOR = 0xCC202020;

    protected MenuScreen() {
        super(Component.literal("Minimals Menu"));
    }

    @Override
    protected void init() {
        // No widgets yet; menu is currently a static status panel.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int x = (this.width - BOX_W) / 2;
        int y = (this.height - BOX_H) / 2;

        MinimalClientMod.drawRoundedBox(graphics, x, y, x + BOX_W, y + BOX_H, RADIUS, BG_COLOR);

        int titleX = x + 16;
        int titleY = y + 12;
        graphics.text(this.font, "Minimals", titleX, titleY, 0xFFFFFF, true);

        String hud = "HUD: " + (MinimalClientMod.hudVisible ? "On" : "Off");
        graphics.text(this.font, hud, titleX, titleY + 20, 0xFFFFFF, true);

        graphics.text(this.font, "Right Shift: close  |  H: toggle HUD", titleX, titleY + 48, 0xAAAAAA, true);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
