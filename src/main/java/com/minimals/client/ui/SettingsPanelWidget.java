package com.minimals.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Non-interactive backdrop behind an expanded module's settings: a darker rounded block with a
 * thin accent bar on the left, so its rows read as belonging to the module above.
 */
public class SettingsPanelWidget extends AbstractWidget {

    public SettingsPanelWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.active = false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + getWidth();
        int y2 = y1 + getHeight();
        UiRenderer.roundedRect(graphics, x1, y1, x2, y2, 6, UiRenderer.SETTINGS_PANEL_BG);
        graphics.fill(x1 + 2, y1 + 6, x1 + 4, y2 - 6, UiRenderer.withOpacity(0x998B5CF6));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
