package com.minimals.client.shulker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Draws the shulker preview under the item name: a grid of up to 8 slots (4 columns, same 24px
 * slots and sprite as the vanilla bundle tooltip) and an "and more" line when the box holds more
 * distinct items than fit.
 *
 * Layout constants mirror vanilla ClientBundleTooltip (SLOT_SIZE 24, 4 columns, 96px grid).
 */
public final class ClientShulkerTooltip implements ClientTooltipComponent {

    private static final Identifier SLOT_BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("container/bundle/slot_background");

    private static final int SLOT_SIZE = 24;
    private static final int COLUMNS = 4;
    private static final int GRID_WIDTH = COLUMNS * SLOT_SIZE;
    /** Offset of the item icon inside its 24px slot (16px icon centred with a 4px margin). */
    private static final int ITEM_INSET = 4;
    private static final int MORE_TEXT_COLOR = 0xFFAAAAAA;
    private static final int MORE_TEXT_MARGIN = 2;

    private static final Component AND_MORE = Component.literal("and more");
    private static final Component EMPTY = Component.literal("Empty");

    private final ShulkerPreview preview;

    public ClientShulkerTooltip(ShulkerPreview preview) {
        this.preview = preview;
    }

    private int rows() {
        return Math.max(1, Mth.positiveCeilDiv(preview.entries().size(), COLUMNS));
    }

    @Override
    public int getWidth(Font font) {
        return GRID_WIDTH;
    }

    @Override
    public int getHeight(Font font) {
        if (preview.isEmpty()) {
            return font.lineHeight + MORE_TEXT_MARGIN;
        }
        int height = rows() * SLOT_SIZE;
        if (preview.hasMore()) {
            height += MORE_TEXT_MARGIN + font.lineHeight;
        }
        return height;
    }

    @Override
    public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
        if (preview.isEmpty()) {
            graphics.text(font, EMPTY, x, y, MORE_TEXT_COLOR);
            return;
        }

        // Centre the 96px grid inside the tooltip (the name line above can be wider).
        int left = x + (w - GRID_WIDTH) / 2;
        List<ItemStack> entries = preview.entries();
        for (int i = 0; i < entries.size(); i++) {
            int drawX = left + (i % COLUMNS) * SLOT_SIZE;
            int drawY = y + (i / COLUMNS) * SLOT_SIZE;
            ItemStack stack = entries.get(i);

            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND_SPRITE, drawX, drawY, SLOT_SIZE, SLOT_SIZE);
            graphics.item(stack, drawX + ITEM_INSET, drawY + ITEM_INSET, i + 1);
            graphics.itemDecorations(font, stack, drawX + ITEM_INSET, drawY + ITEM_INSET);
        }

        if (preview.hasMore()) {
            int textY = y + rows() * SLOT_SIZE + MORE_TEXT_MARGIN;
            graphics.text(font, AND_MORE, left, textY, MORE_TEXT_COLOR);
        }
    }
}
