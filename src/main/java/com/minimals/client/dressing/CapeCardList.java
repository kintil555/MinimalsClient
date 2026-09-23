package com.minimals.client.dressing;

import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Grid of cape "cards": a thumbnail of the cape texture with its name and current state
 * (Active / Selected), instead of a flat text dropdown - closer to how a wardrobe/outfit picker
 * (e.g. Essential's cosmetics UI) shows cosmetics as visual cards you can browse. Cards are laid
 * out left-to-right, wrapping onto new rows, so many capes fit in a compact, mostly-horizontal
 * area instead of one tall vertical column. A leading "No cape" card is always included.
 * Thumbnails are lazily downloaded and registered via {@link PreviewTextureLoader} the first time
 * a card scrolls into view, then cached for the life of this widget.
 */
public class CapeCardList extends AbstractWidget {

    private static final int CARD_W = 84;
    private static final int CARD_H = 64;
    private static final int GAP = 6;
    private static final int THUMB_SIZE = 28;
    private static final int MAX_VISIBLE_ROWS = 2;

    /** One card: null cape id means "No cape". */
    public record Entry(String id, String name, String url, boolean active) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Identifier> thumbnails = new HashMap<>();
    private final Map<String, Boolean> loading = new HashMap<>();
    private final Consumer<Entry> onPick;
    private String selectedId = "__none__";
    private boolean selectedIsNone = true;
    private int scroll;
    private int columns = 1;

    public CapeCardList(int x, int y, int width, Consumer<Entry> onPick) {
        super(x, y, width, CARD_H * MAX_VISIBLE_ROWS, Component.literal("Capes"));
        this.onPick = onPick;
        this.columns = Math.max(1, (width + GAP) / (CARD_W + GAP));
    }

    /**
     * Replaces the entry list in place, preserving the current scroll position where possible
     * (e.g. after a background cape fetch completes) instead of always resetting to the top.
     * Callers that want a fresh list (switching tabs) should call {@link #resetScroll()} too.
     */
    public void setEntries(List<Entry> capes, boolean anyActive) {
        entries.clear();
        entries.add(new Entry(null, "No cape", null, !anyActive));
        entries.addAll(capes);
        clampScroll();
    }

    public void resetScroll() {
        scroll = 0;
    }

    public void setSelected(String capeIdOrNull) {
        selectedIsNone = capeIdOrNull == null;
        selectedId = capeIdOrNull;
    }

    private int rowCount() {
        return (entries.size() + columns - 1) / columns;
    }

    private int visibleRows() {
        return Math.min(rowCount(), MAX_VISIBLE_ROWS);
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, rowCount() - MAX_VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    @Override
    public int getHeight() {
        return CARD_H * visibleRows() + Math.max(0, visibleRows() - 1) * GAP;
    }

    private void ensureThumbnail(Entry e) {
        if (e.url() == null || thumbnails.containsKey(e.url()) || Boolean.TRUE.equals(loading.get(e.url()))) {
            return;
        }
        loading.put(e.url(), true);
        PreviewTextureLoader.registerCapeThumbnail(e.url()).thenAccept(id ->
                Minecraft.getInstance().execute(() -> {
                    thumbnails.put(e.url(), id);
                    loading.remove(e.url());
                }));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int col = (int) ((event.x() - getX()) / (CARD_W + GAP));
        int visRow = (int) ((event.y() - getY()) / (CARD_H + GAP));
        if (col < 0 || col >= columns) {
            return;
        }
        int idx = (scroll + visRow) * columns + col;
        if (idx >= 0 && idx < entries.size()) {
            onPick.accept(entries.get(idx));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int maxScroll = Math.max(0, rowCount() - MAX_VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int visible = visibleRows();
        for (int visRow = 0; visRow < visible; visRow++) {
            int row = scroll + visRow;
            int cardY = getY() + visRow * (CARD_H + GAP);
            for (int col = 0; col < columns; col++) {
                int idx = row * columns + col;
                if (idx >= entries.size()) {
                    continue;
                }
                Entry e = entries.get(idx);
                int cardX = getX() + col * (CARD_W + GAP);
                boolean isSelected = e.id() == null ? selectedIsNone : e.id().equals(selectedId);
                boolean hovered = mouseX >= cardX && mouseX < cardX + CARD_W
                        && mouseY >= cardY && mouseY < cardY + CARD_H;

                int bg = isSelected ? 0x338B5CF6 : hovered ? UiRenderer.ROW_BG_HOVER : UiRenderer.SETTINGS_PANEL_BG;
                UiRenderer.roundedRect(graphics, cardX, cardY, cardX + CARD_W, cardY + CARD_H, 6, bg);
                if (isSelected) {
                    UiRenderer.roundedRect(graphics, cardX, cardY, cardX + CARD_W, cardY + 2, 1, UiRenderer.ACCENT);
                }

                int thumbX = cardX + (CARD_W - THUMB_SIZE) / 2;
                int thumbY = cardY + 5;
                UiRenderer.roundedRect(graphics, thumbX, thumbY, thumbX + THUMB_SIZE, thumbY + THUMB_SIZE, 4,
                        0x40000000);
                if (e.id() == null) {
                    UiRenderer.centeredText(graphics, "x", thumbX + THUMB_SIZE / 2, thumbY + THUMB_SIZE / 2 - 4,
                            UiRenderer.TEXT_SECONDARY);
                } else {
                    ensureThumbnail(e);
                    Identifier tex = thumbnails.get(e.url());
                    if (tex != null) {
                        // Cape texture is 64x32; the visible back panel lives at roughly (1,1)-(11,17).
                        graphics.blit(tex, thumbX + 2, thumbY + 2, thumbX + THUMB_SIZE - 2, thumbY + THUMB_SIZE - 2,
                                1f / 64f, 11f / 64f, 1f / 32f, 17f / 32f);
                    } else {
                        UiRenderer.centeredText(graphics, "...", thumbX + THUMB_SIZE / 2, thumbY + THUMB_SIZE / 2 - 4,
                                UiRenderer.TEXT_SECONDARY);
                    }
                }

                int textY = thumbY + THUMB_SIZE + 3;
                UiRenderer.centeredText(graphics, e.name(), cardX + CARD_W / 2, textY, UiRenderer.TEXT_PRIMARY);
                String state = e.active() ? "Active" : isSelected ? "Selected" : "";
                if (!state.isEmpty()) {
                    UiRenderer.centeredText(graphics, state, cardX + CARD_W / 2, textY + 9,
                            e.active() ? 0xFF7CD87C : UiRenderer.TEXT_SECONDARY);
                }
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal("Capes"));
    }
}
