package com.minimals.client.dressingroom.ui;

import com.minimals.client.dressingroom.DressingRoomManager;
import com.minimals.client.dressingroom.Outfit;
import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Tiny popup shown at the cursor on right-click: Rename (inline edit box) + Delete. */
public class OutfitContextMenu extends AbstractWidget {
    private static final int WIDTH = 120;
    private static final int ROW_H = 18;

    private final Outfit outfit;
    private final Runnable onClose;
    private final Runnable onDeleted;
    private boolean renaming = false;
    private EditBox nameBox;

    public OutfitContextMenu(int x, int y, Outfit outfit, Runnable onClose, Runnable onDeleted) {
        super(x, y, WIDTH, ROW_H * 2, CommonComponents.EMPTY);
        this.outfit = outfit;
        this.onClose = onClose;
        this.onDeleted = onDeleted;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        UiRenderer.roundedRect(graphics, getX(), getY(), WIDTH, ROW_H * 2, 5, UiRenderer.PANEL_BG);
        UiRenderer.roundedRectOutline(graphics, getX(), getY(), WIDTH, ROW_H * 2, 5, UiRenderer.ACCENT);

        if (renaming && nameBox != null) {
            nameBox.render(graphics, mouseX, mouseY, partialTick);
        } else {
            drawRow(graphics, 0, "Rename", mouseX, mouseY);
        }
        drawRow(graphics, 1, "Delete", mouseX, mouseY);
    }

    private void drawRow(GuiGraphicsExtractor graphics, int row, String label, int mouseX, int mouseY) {
        int rowY = getY() + row * ROW_H;
        boolean hovered = mouseX >= getX() && mouseX <= getX() + WIDTH && mouseY >= rowY && mouseY <= rowY + ROW_H;
        if (hovered) {
            UiRenderer.roundedRect(graphics, getX() + 2, rowY + 1, WIDTH - 4, ROW_H - 2, 3, UiRenderer.ROW_HOVER);
        }
        graphics.drawString(Minecraft.getInstance().font, label, getX() + 8, rowY + 5,
            row == 1 ? UiRenderer.DANGER : UiRenderer.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (renaming && nameBox != null && nameBox.isMouseOver(event.x(), event.y())) {
            return nameBox.mouseClicked(event, doubleClick);
        }
        int row = (int) (event.y() - getY()) / ROW_H;
        if (row == 0 && !renaming) {
            startRename();
            return true;
        } else if (row == 1) {
            DressingRoomManager.get().deleteOutfit(outfit.id());
            onDeleted.run();
            onClose.run();
            return true;
        }
        return true;
    }

    private void startRename() {
        renaming = true;
        nameBox = new EditBox(Minecraft.getInstance().font, getX() + 4, getY() + 2, WIDTH - 8, ROW_H - 4, Component.literal(outfit.name()));
        nameBox.setValue(outfit.name());
        nameBox.setResponder(v -> outfit.setName(v));
    }

    public void commitAndClose() {
        if (renaming) DressingRoomManager.get().save();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
