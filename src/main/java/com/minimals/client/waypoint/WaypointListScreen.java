package com.minimals.client.waypoint;

import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The waypoint manager screen. Two tabs: "This Dimension" (what the world overlay shows right
 * now) and "All" (every waypoint saved for this world, with its dimension). Rows scroll with the
 * mouse wheel; each row can be deleted (two clicks) and "Add Here" opens the create dialog at
 * the player's position. Opened from the Waypoints button in the ClickGUI header.
 */
public class WaypointListScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 260;
    private static final int PANEL_RADIUS = 10;
    private static final int PAD = 14;
    private static final int HEADER_H = 44;
    private static final int TAB_H = 22;
    private static final int TAB_W = 110;
    private static final int ROW_H = 28;
    private static final int ROW_GAP = 4;
    private static final int SCROLL_STEP = 16;
    private static final int FOOTER_H = 34;

    private enum Tab {
        DIMENSION("This Dimension"),
        ALL("All");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    /** Remembered across openings, like the ClickGUI's category. */
    private static Tab activeTab = Tab.DIMENSION;

    private final Screen returnTo;
    private final List<AbstractWidget> rows = new ArrayList<>();
    private final List<Integer> rowBaseY = new ArrayList<>();
    private int scrollOffset;
    private int contentHeight;

    public WaypointListScreen(Screen returnTo) {
        super(Component.literal("Waypoints"));
        this.returnTo = returnTo;
    }

    private int panelX() {
        return (width - PANEL_W) / 2;
    }

    private int panelY() {
        return (height - PANEL_H) / 2;
    }

    private int viewportTop() {
        return panelY() + HEADER_H + TAB_H + 8;
    }

    private int viewportBottom() {
        return panelY() + PANEL_H - FOOTER_H;
    }

    @Override
    protected void init() {
        // Data must match the world/dimension the player is in right now, even if the tick
        // loop has not synced since the menu opened.
        WaypointManager.sync(Minecraft.getInstance());
        int px = panelX();
        int py = panelY();

        // Footer: Add Here (left) and Done (right).
        int footerY = py + PANEL_H - PAD - 20;
        int half = (PANEL_W - PAD * 2 - 6) / 2;
        Button addHere = Button.builder(Component.literal("Add Here"), btn -> openCreate())
                .bounds(px + PAD, footerY, half, 20).build();
        addHere.active = WaypointManager.currentDimension(Minecraft.getInstance()) != null;
        addRenderableWidget(addHere);
        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(px + PAD + half + 6, footerY, half, 20).build());

        rebuildRows();
    }

    private void openCreate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.gui.setScreen(new WaypointCreateScreen(this,
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), "", WaypointIcon.LOCATE));
    }

    /** Rebuilds the row widgets for the active tab. Called on tab switch, delete and resize. */
    private void rebuildRows() {
        for (AbstractWidget row : rows) {
            removeWidget(row);
        }
        rows.clear();
        rowBaseY.clear();

        Minecraft mc = Minecraft.getInstance();
        String currentDim = WaypointManager.currentDimension(mc);
        List<Waypoint> source = activeTab == Tab.DIMENSION ? WaypointManager.visible() : WaypointManager.all();
        Vec3 player = mc.player == null ? null : mc.player.position();

        int x = panelX() + PAD;
        int w = PANEL_W - PAD * 2;
        int y = viewportTop();
        for (Waypoint waypoint : source) {
            boolean sameDim = waypoint.dimension().equals(currentDim);
            String distance = "";
            if (sameDim && player != null) {
                double dx = waypoint.x() + 0.5 - player.x;
                double dy = waypoint.y() + 0.5 - player.y;
                double dz = waypoint.z() + 0.5 - player.z;
                distance = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m";
            }
            String subtitle = waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z();
            if (activeTab == Tab.ALL) {
                subtitle += "  " + shortDimension(waypoint.dimension());
            }
            long id = waypoint.id();
            WaypointRowWidget row = new WaypointRowWidget(x, y, w, ROW_H, waypoint, distance, subtitle, () -> {
                WaypointManager.remove(Minecraft.getInstance(), id);
                rebuildRows();
            });
            addRenderableWidget(row);
            rows.add(row);
            rowBaseY.add(y);
            y += ROW_H + ROW_GAP;
        }
        contentHeight = y - viewportTop();
        clampScroll();
        applyScroll();
    }

    /** "minecraft:the_nether" -> "the_nether"; other namespaces keep theirs. */
    private static String shortDimension(String dimension) {
        return dimension.startsWith("minecraft:") ? dimension.substring("minecraft:".length()) : dimension;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (viewportBottom() - viewportTop()));
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset));
    }

    /** Moves rows by the scroll offset and hides the ones outside the viewport (not drawn, not clickable). */
    private void applyScroll() {
        int top = viewportTop();
        int bottom = viewportBottom();
        for (int i = 0; i < rows.size(); i++) {
            AbstractWidget row = rows.get(i);
            row.setY(rowBaseY.get(i) - scrollOffset);
            boolean inside = row.getY() >= top && row.getY() + row.getHeight() <= bottom;
            row.visible = inside;
            row.active = inside;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            scrollOffset -= (int) Math.signum(scrollY) * SCROLL_STEP;
            clampScroll();
            applyScroll();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Tab[] tabs = Tab.values();
        int tabY = panelY() + HEADER_H;
        for (int i = 0; i < tabs.length; i++) {
            int tx = tabX(i);
            if (event.x() >= tx && event.x() < tx + TAB_W && event.y() >= tabY && event.y() < tabY + TAB_H) {
                if (activeTab != tabs[i]) {
                    activeTab = tabs[i];
                    scrollOffset = 0;
                    rebuildRows();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int tabX(int index) {
        return panelX() + PAD + index * (TAB_W + 6);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(returnTo);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x88000000);

        int px = panelX();
        int py = panelY();
        UiRenderer.roundedRect(graphics, px, py, px + PANEL_W, py + PANEL_H, PANEL_RADIUS, UiRenderer.PANEL_BG);

        graphics.fill(px + 10, py + 8, px + 13, py + HEADER_H - 10, UiRenderer.withOpacity(UiRenderer.ACCENT));
        UiRenderer.text(graphics, "Waypoints", px + 20, py + 16, UiRenderer.TEXT_PRIMARY);
        String count = WaypointManager.all().size() + "/" + WaypointManager.MAX_PER_WORLD;
        UiRenderer.text(graphics, count, px + PANEL_W - PAD - UiRenderer.textWidth(count), py + 16,
                UiRenderer.TEXT_SECONDARY);

        // Tabs.
        Tab[] tabs = Tab.values();
        int tabY = py + HEADER_H;
        for (int i = 0; i < tabs.length; i++) {
            int tx = tabX(i);
            boolean active = tabs[i] == activeTab;
            boolean hovered = mouseX >= tx && mouseX < tx + TAB_W && mouseY >= tabY && mouseY < tabY + TAB_H;
            UiRenderer.roundedRect(graphics, tx, tabY, tx + TAB_W, tabY + TAB_H, 5,
                    active || hovered ? UiRenderer.HEADER_BTN_BG_HOVER : UiRenderer.HEADER_BTN_BG);
            if (active) {
                graphics.fill(tx + 6, tabY + TAB_H - 2, tx + TAB_W - 6, tabY + TAB_H - 1,
                        UiRenderer.withOpacity(UiRenderer.ACCENT));
            }
            int textColor = active ? UiRenderer.ACCENT : UiRenderer.TEXT_SECONDARY;
            UiRenderer.text(graphics, tabs[i].label,
                    tx + (TAB_W - UiRenderer.textWidth(tabs[i].label)) / 2, tabY + (TAB_H - 8) / 2, textColor);
        }

        if (rows.isEmpty()) {
            String empty = activeTab == Tab.DIMENSION ? "No waypoints in this dimension" : "No waypoints yet";
            UiRenderer.text(graphics, empty, px + (PANEL_W - UiRenderer.textWidth(empty)) / 2,
                    viewportTop() + 20, UiRenderer.TEXT_SECONDARY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
