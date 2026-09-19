package com.minimals.client.ui;

import com.minimals.client.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class ModuleRowWidget extends Button {

    private final Module module;

    public ModuleRowWidget(int x, int y, int width, int height, Module module) {
        super(x, y, width, height, Component.literal(module.getName()), btn -> module.toggle(), DEFAULT_NARRATION);
        this.module = module;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered();
        int w = getWidth();
        int h = getHeight();
        int rowColor = hovered ? UiRenderer.ROW_BG_HOVER : UiRenderer.ROW_BG;
        if (rowColor != 0) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + h, 6, rowColor);
        }

        int textColor = module.isEnabled() ? UiRenderer.TEXT_PRIMARY : UiRenderer.TEXT_SECONDARY;
        graphics.text(net.minecraft.client.Minecraft.getInstance().font, module.getName(),
                getX() + 12, getY() + (h - 8) / 2, textColor, false);

        int toggleW = 8;
        int toggleH = 8;
        int toggleX = getX() + w - toggleW - 12;
        int toggleY = getY() + (h - toggleH) / 2;
        int toggleColor = module.isEnabled() ? UiRenderer.TOGGLE_ON : UiRenderer.TOGGLE_OFF;
        UiRenderer.roundedRect(graphics, toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, 4, toggleColor);
    }
}
