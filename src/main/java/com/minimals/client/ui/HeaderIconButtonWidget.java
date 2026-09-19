package com.minimals.client.ui;

import com.minimals.client.MinimalClientMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Square icon button for the ClickGUI header (HUD editor). Sits on a slightly lighter
 * background than the panel. The 16x16 icon is plain white and is tinted at blit time,
 * same technique as {@link GearButtonWidget}.
 */
public class HeaderIconButtonWidget extends Button {

    private static final int ICON_SIZE = 16;

    private final Identifier texture;
    private final Runnable onPress;

    public HeaderIconButtonWidget(int x, int y, int size, String textureName, String label, Runnable onPress) {
        super(x, y, size, size, Component.literal(label), btn -> { }, DEFAULT_NARRATION);
        this.texture = Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID, "textures/gui/" + textureName + ".png");
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHovered();

        UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + h, 5,
                hovered ? UiRenderer.HEADER_BTN_BG_HOVER : UiRenderer.HEADER_BTN_BG);

        int tint = hovered ? UiRenderer.ACCENT : UiRenderer.TEXT_SECONDARY;
        int iconX = getX() + (w - ICON_SIZE) / 2;
        int iconY = getY() + (h - ICON_SIZE) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, iconX, iconY,
                0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, UiRenderer.withOpacity(tint));
    }
}
