package com.minimals.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Simple closed/open dropdown: a button showing the current choice, which opens a list of
 * options below it on click. Used anywhere a small fixed set of named items needs picking
 * (a cape, a fetched skin) without pulling in a full scrollable menu.
 */
public class DropdownWidget extends AbstractWidget {

    private static final int ROW_H = 18;
    private static final int MAX_VISIBLE = 5;

    private List<String> options;
    private String selected;
    private final String placeholder;
    private final Consumer<String> onSelect;
    private boolean open;

    public DropdownWidget(int x, int y, int width, String placeholder, List<String> options,
                           Consumer<String> onSelect) {
        super(x, y, width, ROW_H, Component.literal(placeholder));
        this.placeholder = placeholder;
        this.options = options;
        this.onSelect = onSelect;
    }

    public void setOptions(List<String> options) {
        this.options = options;
        this.open = false;
    }

    public void setSelected(String selected) {
        this.selected = selected;
    }

    /** Total height including the open list, for callers laying out widgets below this one. */
    public int totalHeight() {
        if (!open || options.isEmpty()) {
            return ROW_H;
        }
        return ROW_H + Math.min(options.size(), MAX_VISIBLE) * ROW_H;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        double my = event.y();
        if (my <= getY() + ROW_H) {
            open = !open && !options.isEmpty();
            return;
        }
        if (open) {
            int idx = (int) ((my - getY() - ROW_H) / ROW_H);
            if (idx >= 0 && idx < options.size()) {
                selected = options.get(idx);
                onSelect.accept(selected);
            }
            open = false;
        }
    }

    /** True when the click landed inside this widget's current (possibly open) bounds. */
    public boolean hitTest(double mx, double my) {
        return mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + totalHeight();
    }

    /** Widens the clickable area to the open list too - the base isMouseOver only knows about
     *  getWidth()/getHeight(), which is just the closed row. */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return hitTest(mouseX, mouseY);
    }

    public void closeIfOutside(double mx, double my) {
        if (open && !hitTest(mx, my)) {
            open = false;
        }
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int w = getWidth();
        UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + ROW_H, 5,
                isHovered() ? UiRenderer.ROW_BG_HOVER : UiRenderer.SETTINGS_PANEL_BG);
        String label = selected != null ? selected : placeholder;
        UiRenderer.text(graphics, label, getX() + 8, getY() + (ROW_H - 8) / 2,
                selected != null ? UiRenderer.TEXT_PRIMARY : UiRenderer.TEXT_SECONDARY);
        String caret = open ? "^" : "v";
        UiRenderer.text(graphics, caret, getX() + w - 14, getY() + (ROW_H - 8) / 2, UiRenderer.TEXT_SECONDARY);

        if (open) {
            int listY = getY() + ROW_H;
            int visible = Math.min(options.size(), MAX_VISIBLE);
            UiRenderer.roundedRect(graphics, getX(), listY, getX() + w, listY + visible * ROW_H, 5,
                    UiRenderer.PANEL_BG);
            for (int i = 0; i < visible; i++) {
                int rowY = listY + i * ROW_H;
                boolean hovered = mouseY >= rowY && mouseY < rowY + ROW_H && mouseX >= getX() && mouseX < getX() + w;
                if (hovered) {
                    UiRenderer.roundedRect(graphics, getX(), rowY, getX() + w, rowY + ROW_H, 5, UiRenderer.ROW_BG_HOVER);
                }
                UiRenderer.text(graphics, options.get(i), getX() + 8, rowY + (ROW_H - 8) / 2, UiRenderer.TEXT_PRIMARY);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.literal(placeholder));
    }
}
