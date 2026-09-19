package com.minimals.client.ui;

import com.minimals.client.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Sidebar tab. Either a real {@link Module.Category}, or the synthetic "All" tab (built with
 * {@link #all}) which lists every module and only appears while the search field is in use.
 */
public class CategoryTabWidget extends Button {

    private final String label;
    private final BooleanSupplier activeSupplier;

    public CategoryTabWidget(int x, int y, int width, int height, Module.Category category,
                              BooleanSupplier activeSupplier, Consumer<Module.Category> onSelect) {
        super(x, y, width, height, Component.literal(category.label), btn -> onSelect.accept(category), DEFAULT_NARRATION);
        this.label = category.label;
        this.activeSupplier = activeSupplier;
    }

    private CategoryTabWidget(int x, int y, int width, int height, String label,
                              BooleanSupplier activeSupplier, Runnable onSelect) {
        super(x, y, width, height, Component.literal(label), btn -> onSelect.run(), DEFAULT_NARRATION);
        this.label = label;
        this.activeSupplier = activeSupplier;
    }

    /** The "All" tab: every module, filtered by the search query. */
    public static CategoryTabWidget all(int x, int y, int width, int height,
                                        BooleanSupplier activeSupplier, Runnable onSelect) {
        return new CategoryTabWidget(x, y, width, height, "All", activeSupplier, onSelect);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean active = activeSupplier.getAsBoolean();
        int w = getWidth();
        int h = getHeight();

        if (active) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + 3, getY() + h, 2, UiRenderer.ACCENT);
        } else if (isHovered()) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + h, 4, UiRenderer.ROW_BG_HOVER);
        }

        int textColor = active ? UiRenderer.ACCENT : UiRenderer.TEXT_SECONDARY;
        UiRenderer.text(graphics, label, getX() + 14, getY() + (h - 8) / 2, textColor);
    }
}
