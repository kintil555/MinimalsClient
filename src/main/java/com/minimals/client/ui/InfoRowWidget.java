package com.minimals.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Non-interactive one-line label row (status text in the settings page). */
public class InfoRowWidget extends Button {

    private final String text;

    public InfoRowWidget(int x, int y, int width, int height, String text) {
        super(x, y, width, height, Component.literal(text), btn -> { }, DEFAULT_NARRATION);
        this.text = text;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        // Display only.
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiRenderer.text(graphics, text, getX() + 10, getY() + (getHeight() - 8) / 2, UiRenderer.TEXT_SECONDARY);
    }
}
