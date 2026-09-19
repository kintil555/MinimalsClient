package com.minimals.client.ui;

import com.minimals.client.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * A module row. Clicking the body toggles the module; clicking the arrow icon on the
 * right expands/collapses its settings dropdown instead.
 */
public class ModuleRowWidget extends Button {

    /** Width of the clickable arrow zone at the right edge of the row. */
    private static final int ARROW_ZONE_W = 22;

    private final Module module;
    private final BooleanSupplier expandedSupplier;
    private final Runnable onExpandToggle;

    public ModuleRowWidget(int x, int y, int width, int height, Module module,
                           BooleanSupplier expandedSupplier, Runnable onExpandToggle) {
        super(x, y, width, height, Component.literal(module.getName()), btn -> { }, DEFAULT_NARRATION);
        this.module = module;
        this.expandedSupplier = expandedSupplier;
        this.onExpandToggle = onExpandToggle;
    }

    private boolean isInArrowZone(double mouseX) {
        return mouseX >= getX() + getWidth() - ARROW_ZONE_W;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        // Keyboard activation (Enter/Space on a focused row) toggles the module.
        module.toggle();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (isInArrowZone(event.x())) {
            onExpandToggle.run();
        } else {
            module.toggle();
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered();
        boolean expanded = expandedSupplier.getAsBoolean();
        int w = getWidth();
        int h = getHeight();
        int rowColor = hovered || expanded ? UiRenderer.ROW_BG_HOVER : UiRenderer.ROW_BG;
        if (rowColor != 0) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + h, 6, rowColor);
        }

        int textColor = module.isEnabled() ? UiRenderer.TEXT_PRIMARY : UiRenderer.TEXT_SECONDARY;
        UiRenderer.text(graphics, module.getName(), getX() + 12, getY() + (h - 8) / 2, textColor);

        // arrow icon: ">" collapsed, "v" expanded. Highlighted when the cursor is over its zone.
        boolean overArrow = hovered && isInArrowZone(mouseX);
        int arrowColor = overArrow || expanded ? UiRenderer.ACCENT : UiRenderer.TEXT_SECONDARY;
        String arrow = expanded ? "v" : ">";
        int arrowX = getX() + w - ARROW_ZONE_W + (ARROW_ZONE_W - UiRenderer.textWidth(arrow)) / 2;
        UiRenderer.text(graphics, arrow, arrowX, getY() + (h - 8) / 2, arrowColor);

        int toggleW = 8;
        int toggleH = 8;
        int toggleX = getX() + w - ARROW_ZONE_W - toggleW - 6;
        int toggleY = getY() + (h - toggleH) / 2;
        int toggleColor = module.isEnabled() ? UiRenderer.TOGGLE_ON : UiRenderer.TOGGLE_OFF;
        UiRenderer.roundedRect(graphics, toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, 4, toggleColor);
    }
}
