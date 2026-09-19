package com.minimals.client.ui;

import com.minimals.client.MinimalClientMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

/**
 * Gear icon in the bottom-left of the panel. Opens/closes the settings page.
 * The 16x16 texture is plain white, so the icon is tinted through the blit colour argument.
 */
public class GearButtonWidget extends Button {

    private static final Identifier GEAR_TEXTURE =
            Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID, "textures/gui/gear.png");
    private static final int ICON_SIZE = 16;

    private final BooleanSupplier activeSupplier;
    private final Runnable onToggle;

    public GearButtonWidget(int x, int y, int size, BooleanSupplier activeSupplier, Runnable onToggle) {
        super(x, y, size, size, Component.literal("Settings"), btn -> { }, DEFAULT_NARRATION);
        this.activeSupplier = activeSupplier;
        this.onToggle = onToggle;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onToggle.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean active = activeSupplier.getAsBoolean();
        int w = getWidth();
        int h = getHeight();

        if (active || isHovered()) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + h, 5, UiRenderer.ROW_BG_HOVER);
        }

        int tint = active ? UiRenderer.ACCENT : isHovered() ? UiRenderer.TEXT_PRIMARY : UiRenderer.TEXT_SECONDARY;
        int iconX = getX() + (w - ICON_SIZE) / 2;
        int iconY = getY() + (h - ICON_SIZE) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, GEAR_TEXTURE, iconX, iconY,
                0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, UiRenderer.withOpacity(tint));
    }
}
