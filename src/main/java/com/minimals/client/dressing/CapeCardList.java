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
 * Scrollable list of cape "cards": a thumbnail of the cape texture next to its name and current
 * state (Active / Selected), instead of a flat text dropdown - closer to how a wardrobe/outfit
 * picker (e.g. Essential's cosmetics UI) shows cosmetics as visual cards you can browse. A
 * leading "No cape" card is always included. Thumbnails are lazily downloaded and registered via
 * {@link PreviewTextureLoader} the first time a card scrolls into view, then cached for the life
 * of this widget.
 */
public class CapeCardList extends AbstractWidget {

    private static final int CARD_H = 40;
    private static final int THUMB_SIZE = 28;
    private static final int MAX_VISIBLE = 4;

    /** One row: null cape id means "No cape". */
    public record Entry(String id, String name, String url, boolean active) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Identifier> thumbnails = new HashMap<>();
    private final Map<String, Boolean> loading = new HashMap<>();
    private final Consumer<Entry> onPick;
    private String selectedId = "__none__";
    private boolean selectedIsNone = true;
    private int scroll;

    public CapeCardList(int x, int y, int width, Consumer<Entry> onPick) {
        super(x, y, width, CARD_H * MAX_VISIBLE, Component.literal("Capes"));
        this.onPick = onPick;
    }

    public void setEntries(List<Entry> capes, boolean anyActive) {
        entries.clear();
        entries.add(new Entry(null, "No cape", null, !anyActive));
        entries.addAll(capes);
        scroll = 0;
    }

    public void setSelected(String capeIdOrNull) {
        selectedIsNone = capeIdOrNull == null;
        selectedId = capeIdOrNull;
    }

    private int visibleCount() {
        return Math.min(entries.size(), MAX_VISIBLE);
    }

    @Override
    public int getHeight() {
        return CARD_H * visibleCount();
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
        int idx = scroll + (int) ((event.y() - getY()) / CARD_H);
        if (idx >= 0 && idx < entries.size()) {
            onPick.accept(entries.get(idx));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroll = Math.max(0, Math.min(entries.size() - visibleCount(), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int w = getWidth();
        int visible = visibleCount();
        for (int row = 0; row < visible; row++) {
            int idx = scroll + row;
            if (idx >= entries.size()) {
                break;
            }
            Entry e = entries.get(idx);
            int rowY = getY() + row * CARD_H;
            boolean isSelected = e.id() == null ? selectedIsNone : e.id().equals(selectedId);
            boolean hovered = mouseX >= getX() && mouseX < getX() + w && mouseY >= rowY && mouseY < rowY + CARD_H;

            int bg = isSelected ? 0x338B5CF6 : hovered ? UiRenderer.ROW_BG_HOVER : UiRenderer.SETTINGS_PANEL_BG;
            UiRenderer.roundedRect(graphics, getX(), rowY + 1, getX() + w, rowY + CARD_H - 1, 6, bg);
            if (isSelected) {
                UiRenderer.roundedRect(graphics, getX(), rowY + 1, getX() + 2, rowY + CARD_H - 1, 1, UiRenderer.ACCENT);
            }

            int thumbX = getX() + 6;
            int thumbY = rowY + (CARD_H - THUMB_SIZE) / 2;
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

            int textX = thumbX + THUMB_SIZE + 8;
            UiRenderer.text(graphics, e.name(), textX, rowY + 8, UiRenderer.TEXT_PRIMARY);
            String state = e.active() ? "Active" : isSelected ? "Selected (not applied)" : "";
            if (!state.isEmpty()) {
                UiRenderer.text(graphics, state, textX, rowY + 20,
                        e.active() ? 0xFF7CD87C : UiRenderer.TEXT_SECONDARY);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal("Capes"));
    }
}
