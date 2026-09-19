package com.minimals.client;

import com.minimals.client.module.Module;
import com.minimals.client.module.ModuleManager;
import com.minimals.client.ui.CategoryTabWidget;
import com.minimals.client.ui.ModuleRowWidget;
import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class MenuScreen extends Screen {

    private static final int PANEL_W = 480;
    private static final int PANEL_H = 300;
    private static final int PANEL_RADIUS = 10;

    private static final int SIDEBAR_W = 130;
    private static final int HEADER_H = 44;
    private static final int TAB_H = 26;
    private static final int ROW_H = 24;
    private static final int ROW_GAP = 4;
    private static final int CONTENT_PADDING = 14;

    private static Module.Category activeCategory = Module.Category.COMBAT;

    private final List<ModuleRowWidget> moduleRows = new ArrayList<>();

    protected MenuScreen() {
        super(Component.literal("Minimals"));
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_H) / 2;
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
                        rebuildModuleRows();
                    }
            );
            addRenderableWidget(tab);
            i++;
        }

        rebuildModuleRows();
    }

    private void rebuildModuleRows() {
        for (ModuleRowWidget row : moduleRows) {
            removeWidget(row);
        }
        moduleRows.clear();

        int px = panelX();
        int py = panelY();
        int contentX = px + SIDEBAR_W + CONTENT_PADDING;
        int contentW = PANEL_W - SIDEBAR_W - CONTENT_PADDING * 2;
        int rowY = py + HEADER_H;

        List<Module> modules = ModuleManager.getModules(activeCategory);
        for (Module module : modules) {
            ModuleRowWidget row = new ModuleRowWidget(contentX, rowY, contentW, ROW_H, module);
            addRenderableWidget(row);
            moduleRows.add(row);
            rowY += ROW_H + ROW_GAP;
        }
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
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
