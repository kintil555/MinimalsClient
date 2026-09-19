package com.minimals.client;

import com.minimals.client.module.Module;
import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.setting.Setting;
import com.minimals.client.ui.CategoryTabWidget;
import com.minimals.client.ui.KeybindRowWidget;
import com.minimals.client.ui.ModuleRowWidget;
import com.minimals.client.ui.SettingRowWidget;
import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MenuScreen extends Screen {

    private static final int PANEL_W = 480;
    private static final int PANEL_H = 300;
    private static final int PANEL_RADIUS = 10;

    private static final int SIDEBAR_W = 130;
    private static final int HEADER_H = 44;
    private static final int TAB_H = 26;
    private static final int ROW_H = 24;
    private static final int SETTING_ROW_H = 22;
    private static final int ROW_GAP = 4;
    private static final int CONTENT_PADDING = 14;
    private static final int SETTING_INDENT = 12;
    private static final int SCROLL_STEP = 16;

    private static Module.Category activeCategory = Module.Category.COMBAT;

    /** Modules whose dropdown is open. Static so it survives closing/reopening the menu. */
    private static final Set<Module> EXPANDED = new HashSet<>();

    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private final List<KeybindRowWidget> keybindRows = new ArrayList<>();
    /** Unscrolled Y of each content widget; scroll is applied on top of this. */
    private final Map<AbstractWidget, Integer> baseY = new HashMap<>();

    private int scrollOffset;
    private int contentHeight;

    protected MenuScreen() {
        super(Component.literal("Minimals"));
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_H) / 2;
    }

    private int viewportTop() {
        return panelY() + HEADER_H;
    }

    private int viewportBottom() {
        return panelY() + PANEL_H - CONTENT_PADDING;
    }

    @Override
    protected void init() {
        int px = panelX();
        int py = panelY();

        int tabY = py + HEADER_H;
        int i = 0;
        for (Module.Category category : Module.Category.values()) {
            CategoryTabWidget tab = new CategoryTabWidget(
                    px + 10, tabY + i * (TAB_H + 2), SIDEBAR_W - 20, TAB_H,
                    category,
                    () -> activeCategory,
                    selected -> {
                        activeCategory = selected;
                        scrollOffset = 0;
                        rebuildContent();
                    }
            );
            addRenderableWidget(tab);
            i++;
        }

        rebuildContent();
    }

    private void toggleExpanded(Module module) {
        if (!EXPANDED.remove(module)) {
            EXPANDED.add(module);
        }
        rebuildContent();
    }

    /**
     * Rebuilds module rows and, for every expanded module, its setting rows and keybind row.
     * Every module gets a keybind row; modules without settings only show that.
     */
    private void rebuildContent() {
        for (AbstractWidget widget : contentWidgets) {
            removeWidget(widget);
        }
        contentWidgets.clear();
        keybindRows.clear();
        baseY.clear();

        int px = panelX();
        int contentX = px + SIDEBAR_W + CONTENT_PADDING;
        int contentW = PANEL_W - SIDEBAR_W - CONTENT_PADDING * 2;
        int y = viewportTop();

        for (Module module : ModuleManager.getModules(activeCategory)) {
            ModuleRowWidget row = new ModuleRowWidget(contentX, y, contentW, ROW_H, module,
                    () -> EXPANDED.contains(module), () -> toggleExpanded(module));
            track(row);
            y += ROW_H + ROW_GAP;

            if (EXPANDED.contains(module)) {
                int settingX = contentX + SETTING_INDENT;
                int settingW = contentW - SETTING_INDENT;
                for (Setting<?> setting : module.getSettings()) {
                    track(new SettingRowWidget(settingX, y, settingW, SETTING_ROW_H, setting));
                    y += SETTING_ROW_H + ROW_GAP;
                }
                KeybindRowWidget keybind = new KeybindRowWidget(settingX, y, settingW, SETTING_ROW_H, module);
                keybindRows.add(keybind);
                track(keybind);
                y += SETTING_ROW_H + ROW_GAP;
            }
        }

        contentHeight = y - viewportTop();
        clampScroll();
        applyScroll();
    }

    private void track(AbstractWidget widget) {
        addRenderableWidget(widget);
        contentWidgets.add(widget);
        baseY.put(widget, widget.getY());
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (viewportBottom() - viewportTop()));
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset));
    }

    /**
     * Positions content widgets from their unscrolled Y minus the scroll offset and hides
     * the ones outside the viewport, so they can neither be seen nor clicked.
     */
    private void applyScroll() {
        int top = viewportTop();
        int bottom = viewportBottom();
        for (AbstractWidget widget : contentWidgets) {
            widget.setY(baseY.get(widget) - scrollOffset);
            boolean inside = widget.getY() >= top && widget.getY() + widget.getHeight() <= bottom;
            widget.visible = inside;
            widget.active = inside;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // A setting slider under the cursor gets the wheel first; otherwise scroll the list.
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
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
    public boolean keyPressed(KeyEvent event) {
        for (KeybindRowWidget row : keybindRows) {
            if (row.handleKey(event)) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // While a keybind row is listening, ESC must cancel the rebind, not close the menu.
        for (KeybindRowWidget row : keybindRows) {
            if (row.isListening()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int px = panelX();
        int py = panelY();

        UiRenderer.roundedRect(graphics, px, py, px + PANEL_W, py + PANEL_H, PANEL_RADIUS, UiRenderer.PANEL_BG);
        UiRenderer.roundedRect(graphics, px, py, px + SIDEBAR_W, py + PANEL_H, PANEL_RADIUS, UiRenderer.SIDEBAR_BG);
        // square off the inner edge of the sidebar so it doesn't look like a floating rounded pill
        graphics.fill(px + SIDEBAR_W - PANEL_RADIUS, py, px + SIDEBAR_W, py + PANEL_H, UiRenderer.SIDEBAR_BG);

        graphics.fill(px + 10, py + 8, px + 13, py + HEADER_H - 10, UiRenderer.ACCENT);
        graphics.text(this.font, "Minimals", px + 20, py + 16, UiRenderer.TEXT_PRIMARY, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
