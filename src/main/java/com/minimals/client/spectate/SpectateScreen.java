package com.minimals.client.spectate;

import com.minimals.client.ui.SearchFieldWidget;
import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Searchable list of online players; each card has a Spectate button. */
public class SpectateScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 260;
    private static final int PAD = 12;
    private static final int HEADER_H = 44;
    private static final int CARD_H = 34;
    private static final int CARD_GAP = 4;
    private static final int SCROLL_STEP = 20;
    private static final int SPECTATE_W = 70;

    /** Dark orange-red, per the requested "merah jingga gelap". */
    private static final int SPECTATE_BTN = 0xFFB23A12;
    private static final int SPECTATE_BTN_HOVER = 0xFFD44A1A;

    private static String query = "";

    private SearchFieldWidget searchField;
    private final List<Card> cards = new ArrayList<>();
    private int scroll;

    public SpectateScreen() {
        super(Component.literal("Spectate"));
    }

    private int px() {
        return (width - PANEL_W) / 2;
    }

    private int py() {
        return (height - PANEL_H) / 2;
    }

    private int viewTop() {
        return py() + HEADER_H;
    }

    private int viewBottom() {
        return py() + PANEL_H - PAD;
    }

    @Override
    protected void init() {
        searchField = new SearchFieldWidget(px() + PAD, py() + 9, PANEL_W - PAD * 2, 26, query, q -> {
            query = q;
            scroll = 0;
            rebuild();
        });
        addRenderableWidget(searchField);
        rebuild();
    }

    private void rebuild() {
        for (Card c : cards) {
            removeWidget(c.button);
        }
        cards.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) {
            return;
        }
        String q = query.toLowerCase(Locale.ROOT);
        UUID self = mc.player.getUUID();
        List<PlayerInfo> players = new ArrayList<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info.getProfile().id().equals(self)) {
                continue;
            }
            if (q.isEmpty() || info.getProfile().name().toLowerCase(Locale.ROOT).contains(q)) {
                players.add(info);
            }
        }
        players.sort(Comparator.comparing(p -> p.getProfile().name().toLowerCase(Locale.ROOT)));

        int y = viewTop();
        for (PlayerInfo info : players) {
            UUID id = info.getProfile().id();
            SpectateButton btn = new SpectateButton(px() + PANEL_W - PAD - SPECTATE_W - 6, y + (CARD_H - 22) / 2,
                    () -> {
                        SpectateManager.start(id);
                        Minecraft.getInstance().gui.setScreen((Screen) null);
                    });
            addRenderableWidget(btn);
            cards.add(new Card(info, btn, y));
            y += CARD_H + CARD_GAP;
        }
        applyScroll();
    }

    private int contentHeight() {
        return cards.size() * (CARD_H + CARD_GAP);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (viewBottom() - viewTop()));
    }

    private void applyScroll() {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        int top = viewTop();
        int bottom = viewBottom();
        for (Card c : cards) {
            int cardY = c.baseY - scroll;
            c.button.setY(cardY + (CARD_H - 22) / 2);
            boolean inside = cardY >= top && cardY + CARD_H <= bottom;
            c.button.visible = inside;
            c.button.active = inside;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            scroll -= (int) Math.signum(scrollY) * SCROLL_STEP;
            applyScroll();
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !searchField.isFocused();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int px = px();
        int py = py();
        UiRenderer.roundedRect(graphics, px, py, px + PANEL_W, py + PANEL_H, 10, UiRenderer.PANEL_BG);
        graphics.fill(px + PAD, py + HEADER_H - 1, px + PANEL_W - PAD, py + HEADER_H, 0x22FFFFFF);

        searchField.extractRenderState(graphics, mouseX, mouseY, delta);

        int top = viewTop();
        int bottom = viewBottom();
        for (Card c : cards) {
            int cardY = c.baseY - scroll;
            if (cardY < top || cardY + CARD_H > bottom) {
                continue;
            }
            UiRenderer.roundedRect(graphics, px + PAD, cardY, px + PANEL_W - PAD, cardY + CARD_H, 6,
                    UiRenderer.HEADER_BTN_BG);
            PlayerFaceExtractor.extractRenderState(graphics, c.info.getSkin(), px + PAD + 6, cardY + 5, 24);
            UiRenderer.text(graphics, c.info.getProfile().name(), px + PAD + 36, cardY + (CARD_H - 8) / 2,
                    UiRenderer.TEXT_PRIMARY);
            c.button.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        if (cards.isEmpty()) {
            UiRenderer.text(graphics, query.isEmpty() ? "No other players" : "No players match '" + query + "'",
                    px + PAD + 4, top + 8, UiRenderer.TEXT_SECONDARY);
        }
    }

    private record Card(PlayerInfo info, SpectateButton button, int baseY) {
    }

    private static final class SpectateButton extends Button {
        private final Runnable action;

        SpectateButton(int x, int y, Runnable action) {
            super(x, y, SPECTATE_W, 22, Component.literal("Spectate"), btn -> { }, DEFAULT_NARRATION);
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight(), 5,
                    isHovered() ? SPECTATE_BTN_HOVER : SPECTATE_BTN);
            int tw = UiRenderer.textWidth("Spectate");
            UiRenderer.text(graphics, "Spectate", getX() + (getWidth() - tw) / 2,
                    getY() + (getHeight() - 8) / 2, UiRenderer.TEXT_PRIMARY);
        }
    }
}
