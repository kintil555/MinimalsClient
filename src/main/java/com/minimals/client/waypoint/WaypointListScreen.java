package com.minimals.client.waypoint;

import com.minimals.client.ui.UiRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The waypoint manager screen. Two tabs: "This Dimension" (what the world overlay shows right
 * now) and "All" (every waypoint saved for this world, with its dimension). Rows scroll with the
 * mouse wheel; each row can be deleted (an "x" opens a confirmation dialog in the middle of
 * the panel) and "Add Here" opens the create dialog at
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

    private static final int DIALOG_W = 220;
    private static final int DIALOG_H = 92;
    private static final int DIALOG_BTN_H = 20;
    private static final int DIALOG_BTN_GAP = 8;
    private static final int DANGER = 0xFFFF5555;
    private static final int DANGER_HOVER = 0xFFFF7777;

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
    /** Waypoint the confirmation dialog is asking about; null while no dialog is open. */
    private Waypoint pendingDelete;

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
            WaypointRowWidget row = new WaypointRowWidget(x, y, w, ROW_H, waypoint, distance, subtitle,
                    () -> pendingDelete = waypoint);
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
        if (pendingDelete != null) {
            return true;
        }
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
        // The dialog is modal: it swallows every click so nothing behind it can be pressed.
        if (pendingDelete != null) {
            if (event.button() == 0) {
                if (inside(event.x(), event.y(), deleteBtnX(), dialogBtnY(), dialogBtnW(), DIALOG_BTN_H)) {
                    confirmDelete();
                } else if (inside(event.x(), event.y(), cancelBtnX(), dialogBtnY(), dialogBtnW(), DIALOG_BTN_H)
                        || !inside(event.x(), event.y(), dialogX(), dialogY(), DIALOG_W, DIALOG_H)) {
                    // Cancel, or a click outside the dialog.
                    pendingDelete = null;
                }
            }
            return true;
        }
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

    private int dialogX() {
        return panelX() + (PANEL_W - DIALOG_W) / 2;
    }

    private int dialogY() {
        return panelY() + (PANEL_H - DIALOG_H) / 2;
    }

    private int dialogBtnW() {
        return (DIALOG_W - PAD * 2 - DIALOG_BTN_GAP) / 2;
    }

    private int dialogBtnY() {
        return dialogY() + DIALOG_H - PAD - DIALOG_BTN_H;
    }

    private int cancelBtnX() {
        return dialogX() + PAD;
    }

    private int deleteBtnX() {
        return dialogX() + PAD + dialogBtnW() + DIALOG_BTN_GAP;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void confirmDelete() {
        Waypoint target = pendingDelete;
        pendingDelete = null;
        if (target != null) {
            WaypointManager.remove(Minecraft.getInstance(), target.id());
            rebuildRows();
        }
    }

    private int tabX(int index) {
        return panelX() + PAD + index * (TAB_W + 6);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (pendingDelete != null) {
            int key = event.key();
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                confirmDelete();
            } else if (key == InputConstants.KEY_ESCAPE) {
                pendingDelete = null;
            }
            // Swallow everything else too, so Tab/Enter cannot reach the buttons behind.
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // While the dialog is open ESC only dismisses the dialog (handled in keyPressed).
        return pendingDelete == null;
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

        // Widgets behind a modal dialog must not light up under the cursor.
        boolean modal = pendingDelete != null;
        int hoverX = modal ? -1 : mouseX;
        int hoverY = modal ? -1 : mouseY;

        super.extractRenderState(graphics, hoverX, hoverY, delta);

        if (modal) {
            drawDeleteDialog(graphics, mouseX, mouseY);
        }
    }

    /** Centred confirmation card over a dimmed panel; drawn after all widgets so it is on top. */
    private void drawDeleteDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int px = panelX();
        int py = panelY();
        // Dim only the panel (rounded like it), so the dialog reads as belonging to it.
        UiRenderer.roundedRect(graphics, px, py, px + PANEL_W, py + PANEL_H, PANEL_RADIUS, 0xAA000000);

        int dx = dialogX();
        int dy = dialogY();
        UiRenderer.roundedRect(graphics, dx, dy, dx + DIALOG_W, dy + DIALOG_H, 8, UiRenderer.HEADER_BTN_BG);
        graphics.fill(dx + 10, dy + 10, dx + 13, dy + 24, DANGER);

        String title = "Delete waypoint?";
        UiRenderer.text(graphics, title, dx + 20, dy + 13, UiRenderer.TEXT_PRIMARY);

        String name = pendingDelete.name().isEmpty() ? "(unnamed)" : pendingDelete.name();
        String line = clipText(name, DIALOG_W - PAD * 2);
        UiRenderer.text(graphics, line, dx + (DIALOG_W - UiRenderer.textWidth(line)) / 2, dy + 38,
                UiRenderer.TEXT_SECONDARY);

        int by = dialogBtnY();
        int bw = dialogBtnW();
        boolean overCancel = inside(mouseX, mouseY, cancelBtnX(), by, bw, DIALOG_BTN_H);
        boolean overDelete = inside(mouseX, mouseY, deleteBtnX(), by, bw, DIALOG_BTN_H);

        UiRenderer.roundedRect(graphics, cancelBtnX(), by, cancelBtnX() + bw, by + DIALOG_BTN_H, 5,
                overCancel ? UiRenderer.HEADER_BTN_BG : UiRenderer.PANEL_BG);
        UiRenderer.text(graphics, "Cancel", cancelBtnX() + (bw - UiRenderer.textWidth("Cancel")) / 2,
                by + (DIALOG_BTN_H - 8) / 2, UiRenderer.TEXT_PRIMARY);

        UiRenderer.roundedRect(graphics, deleteBtnX(), by, deleteBtnX() + bw, by + DIALOG_BTN_H, 5,
                overDelete ? DANGER_HOVER : DANGER);
        UiRenderer.text(graphics, "Delete", deleteBtnX() + (bw - UiRenderer.textWidth("Delete")) / 2,
                by + (DIALOG_BTN_H - 8) / 2, 0xFFFFFFFF);
    }

    /** Shortens text with "..." until it fits in maxWidth pixels. */
    private static String clipText(String text, int maxWidth) {
        if (UiRenderer.textWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && UiRenderer.textWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
