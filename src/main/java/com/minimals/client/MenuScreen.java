package com.minimals.client;

import com.minimals.client.module.Module;
import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.setting.Setting;
import com.minimals.client.module.setting.StringSetting;
import com.minimals.client.ui.Animation;
import com.minimals.client.ui.CategoryTabWidget;
import com.minimals.client.ui.GearButtonWidget;
import com.minimals.client.ui.KeybindRowWidget;
import com.minimals.client.ui.ModuleRowWidget;
import com.minimals.client.ui.SettingRowWidget;
import com.minimals.client.ui.TextFieldRowWidget;
import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
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
    private static final int GEAR_SIZE = 22;

    private static Module.Category activeCategory = Module.Category.COMBAT;

    /** True while the global settings page replaces the module list. Survives reopening. */
    private static boolean settingsOpen = false;

    /** Modules whose dropdown is open. Static so it survives closing/reopening the menu. */
    private static final Set<Module> EXPANDED = new HashSet<>();

    private static final long OPEN_FADE_MS = 160;
    private static final long ROW_FADE_MS = 140;

    /** Fade-in of the whole panel; restarted every time the menu opens. */
    private final Animation openFade = new Animation(0f, OPEN_FADE_MS);
    /** Sidebar tabs and gear: static widgets, rendered by us so the panel fade covers them. */
    private final List<AbstractWidget> chromeWidgets = new ArrayList<>();
    /** Per-row fade for rows created by the latest rebuild (dropdown open, page switch). */
    private final Map<AbstractWidget, Animation> rowFade = new HashMap<>();

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
        // init() also runs on window resize; screen widgets were cleared by then.
        chromeWidgets.clear();
        int px = panelX();
        int py = panelY();

        int tabY = py + HEADER_H;
        int i = 0;
        for (Module.Category category : Module.Category.values()) {
            CategoryTabWidget tab = new CategoryTabWidget(
                    px + 10, tabY + i * (TAB_H + 2), SIDEBAR_W - 20, TAB_H,
                    category,
                    () -> !settingsOpen && activeCategory == category,
                    selected -> {
                        activeCategory = selected;
                        settingsOpen = false;
                        scrollOffset = 0;
                        rebuildContent();
                    }
            );
            addRenderableWidget(tab);
            chromeWidgets.add(tab);
            i++;
        }

        GearButtonWidget gear = new GearButtonWidget(
                px + 10, py + PANEL_H - GEAR_SIZE - 10, GEAR_SIZE,
                () -> settingsOpen,
                () -> {
                    settingsOpen = !settingsOpen;
                    scrollOffset = 0;
                    rebuildContent();
                });
        addRenderableWidget(gear);
        chromeWidgets.add(gear);

        openFade.setTarget(1f);
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
        rowFade.clear();

        int px = panelX();
        int contentX = px + SIDEBAR_W + CONTENT_PADDING;
        int contentW = PANEL_W - SIDEBAR_W - CONTENT_PADDING * 2;
        int y = viewportTop();

        if (settingsOpen) {
            buildSettingsPage(contentX, y, contentW);
            return;
        }

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

    /**
     * Global settings page (opened with the gear): animations, font, opacity and the
     * client-side nickname with its style and colour.
     */
    private void buildSettingsPage(int contentX, int y, int contentW) {
        for (Setting<?> setting : ClientSettings.ALL) {
            if (setting instanceof StringSetting text) {
                track(new TextFieldRowWidget(contentX, y, contentW, SETTING_ROW_H, text));
            } else {
                track(new SettingRowWidget(contentX, y, contentW, SETTING_ROW_H, setting));
            }
            y += SETTING_ROW_H + ROW_GAP;
        }
        contentHeight = y - viewportTop();
        clampScroll();
        applyScroll();
    }

    private void track(AbstractWidget widget) {
        addRenderableWidget(widget);
        contentWidgets.add(widget);
        baseY.put(widget, widget.getY());
        Animation fade = new Animation(0f, ROW_FADE_MS);
        fade.setTarget(1f);
        rowFade.put(widget, fade);
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
        // Same for the nickname field: ESC leaves the field, it must not close the menu.
        return !isTextFieldFocused();
    }

    /**
     * True while the player is typing into a text field of the menu. The tick loop uses this
     * to ignore the HUD/menu hotkeys so typing "h" or pressing RSHIFT cannot fire them.
     */
    public static boolean isTypingInMenu() {
        return Minecraft.getInstance().gui.screen() instanceof MenuScreen menu && menu.isTextFieldFocused();
    }

    private boolean isTextFieldFocused() {
        for (AbstractWidget widget : contentWidgets) {
            if (widget instanceof TextFieldRowWidget && widget.isFocused()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int px = panelX();
        int py = panelY();

        try {
            UiRenderer.setFade(openFade.get());
            UiRenderer.roundedRect(graphics, px, py, px + PANEL_W, py + PANEL_H, PANEL_RADIUS, UiRenderer.PANEL_BG);
            UiRenderer.roundedRect(graphics, px, py, px + SIDEBAR_W, py + PANEL_H, PANEL_RADIUS, UiRenderer.SIDEBAR_BG);
            // square off the inner edge of the sidebar so it doesn't look like a floating rounded pill
            graphics.fill(px + SIDEBAR_W - PANEL_RADIUS, py, px + SIDEBAR_W, py + PANEL_H,
                    UiRenderer.withOpacity(UiRenderer.SIDEBAR_BG));

            graphics.fill(px + 10, py + 8, px + 13, py + HEADER_H - 10, UiRenderer.withOpacity(UiRenderer.ACCENT));
            UiRenderer.text(graphics, "Minimals", px + 20, py + 16, UiRenderer.TEXT_PRIMARY);

            for (AbstractWidget widget : chromeWidgets) {
                widget.extractRenderState(graphics, mouseX, mouseY, delta);
            }
            for (AbstractWidget widget : contentWidgets) {
                Animation row = rowFade.get(widget);
                UiRenderer.setFade(openFade.get() * (row == null ? 1f : row.get()));
                widget.extractRenderState(graphics, mouseX, mouseY, delta);
            }
        } finally {
            UiRenderer.setFade(1f);
        }
    }

    @Override
    public void removed() {
        UiRenderer.setFade(1f);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
