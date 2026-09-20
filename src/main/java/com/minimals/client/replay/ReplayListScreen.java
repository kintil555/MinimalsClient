package com.minimals.client.replay;

import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Lists every .mreplay in the replay folder; each row has Play and Delete. */
public class ReplayListScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 250;
    private static final int PAD = 12;
    private static final int HEADER_H = 40;
    private static final int ROW_H = 36;
    private static final int ROW_GAP = 4;
    private static final int SCROLL_STEP = 20;

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private int scroll;

    private record Entry(Path file, ReplayFormat.Meta meta, String error) {
    }

    public ReplayListScreen(Screen parent) {
        super(Component.literal("Replays"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reload();
        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(px + PANEL_W - PAD - 60, py + 10, 60, 20).build());
    }

    private void reload() {
        entries.clear();
        try (Stream<Path> files = Files.list(ReplayStorage.dir())) {
            files.filter(p -> p.getFileName().toString().endsWith(ReplayStorage.EXTENSION))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .forEach(p -> {
                        try {
                            entries.add(new Entry(p, ReplayFormat.readMeta(p), null));
                        } catch (IOException e) {
                            entries.add(new Entry(p, null, e.getMessage()));
                        }
                    });
        } catch (IOException ignored) {
            // Empty list is shown.
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int viewTop() {
        return (height - PANEL_H) / 2 + HEADER_H;
    }

    private int viewBottom() {
        return (height - PANEL_H) / 2 + PANEL_H - PAD;
    }

    private int maxScroll() {
        int content = entries.size() * (ROW_H + ROW_GAP);
        return Math.max(0, content - (viewBottom() - viewTop()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (scrollY * SCROLL_STEP)));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;
        UiRenderer.roundedRect(graphics, px, py, px + PANEL_W, py + PANEL_H, 8, UiRenderer.PANEL_BG);
        UiRenderer.text(graphics, "Replays", px + PAD, py + 16, UiRenderer.TEXT_PRIMARY);

        graphics.enableScissor(px, viewTop(), px + PANEL_W, viewBottom());
        int y = viewTop() - scroll;
        for (int i = 0; i < entries.size(); i++) {
            drawRow(graphics, entries.get(i), px + PAD, y, mouseX, mouseY);
            y += ROW_H + ROW_GAP;
        }
        graphics.disableScissor();

        if (entries.isEmpty()) {
            String msg = "No replays yet. Record from the RShift menu.";
            UiRenderer.text(graphics, msg, px + (PANEL_W - UiRenderer.textWidth(msg)) / 2,
                    viewTop() + 20, UiRenderer.TEXT_SECONDARY);
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, Entry e, int x, int y, int mouseX, int mouseY) {
        int w = PANEL_W - PAD * 2;
        UiRenderer.roundedRect(graphics, x, y, x + w, y + ROW_H, 5, UiRenderer.HEADER_BTN_BG);
        String title = e.file().getFileName().toString().replace(ReplayStorage.EXTENSION, "");
        UiRenderer.text(graphics, title, x + 8, y + 7, UiRenderer.TEXT_PRIMARY);
        String sub = e.meta() == null ? "Unreadable: " + e.error()
                : formatTicks(e.meta().totalTicks()) + "  -  MC " + e.meta().mcVersion();
        UiRenderer.text(graphics, sub, x + 8, y + 20, UiRenderer.TEXT_SECONDARY);

        boolean playHover = inBox(mouseX, mouseY, playX(x, w), y + 8, 40, 20);
        boolean delHover = inBox(mouseX, mouseY, delX(x, w), y + 8, 20, 20);
        if (e.meta() != null) {
            UiRenderer.roundedRect(graphics, playX(x, w), y + 8, playX(x, w) + 40, y + 28, 4,
                    playHover ? 0xFF9F75FF : UiRenderer.ACCENT);
            UiRenderer.text(graphics, "Play", playX(x, w) + 8, y + 14, 0xFFFFFFFF);
        }
        UiRenderer.roundedRect(graphics, delX(x, w), y + 8, delX(x, w) + 20, y + 28, 4,
                delHover ? 0xFFD44A1A : 0xFF7A2A12);
        UiRenderer.text(graphics, "X", delX(x, w) + 7, y + 14, 0xFFFFFFFF);
    }

    private static int playX(int x, int w) {
        return x + w - 8 - 20 - 4 - 40;
    }

    private static int delX(int x, int w) {
        return x + w - 8 - 20;
    }

    private static boolean inBox(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String formatTicks(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int mx = (int) event.x();
        int my = (int) event.y();
        if (my < viewTop() || my > viewBottom()) {
            return false;
        }
        int x = (width - PANEL_W) / 2 + PAD;
        int w = PANEL_W - PAD * 2;
        int y = viewTop() - scroll;
        for (Entry e : new ArrayList<>(entries)) {
            if (e.meta() != null && inBox(mx, my, playX(x, w), y + 8, 40, 20)) {
                ReplayView.reset();
                ReplayPlayer.open(e.file());
                return true;
            }
            if (inBox(mx, my, delX(x, w), y + 8, 20, 20)) {
                try {
                    Files.deleteIfExists(e.file());
                } catch (IOException ignored) {
                    // Row stays; the file is probably in use.
                }
                reload();
                return true;
            }
            y += ROW_H + ROW_GAP;
        }
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
