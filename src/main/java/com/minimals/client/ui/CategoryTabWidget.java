package com.minimals.client.ui;

import com.minimals.client.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class CategoryTabWidget extends Button {

    private final Module.Category category;
    private final Supplier<Module.Category> activeSupplier;

    public CategoryTabWidget(int x, int y, int width, int height, Module.Category category,
                              Supplier<Module.Category> activeSupplier, Consumer<Module.Category> onSelect) {
        super(x, y, width, height, Component.literal(category.label), btn -> onSelect.accept(category), DEFAULT_NARRATION);
        this.category = category;
        this.activeSupplier = activeSupplier;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean active = activeSupplier.get() == category;
        int w = getWidth();
        int h = getHeight();

        if (active) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + 3, getY() + h, 2, UiRenderer.ACCENT);
        } else if (isHovered()) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + h, 4, UiRenderer.ROW_BG_HOVER);
        }

        int textColor = active ? UiRenderer.ACCENT : UiRenderer.TEXT_SECONDARY;
        graphics.text(net.minecraft.client.Minecraft.getInstance().font, category.label,
                getX() + 14, getY() + (h - 8) / 2, textColor, false);
    }
}
