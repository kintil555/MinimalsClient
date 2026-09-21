package com.minimals.client;

import com.minimals.client.module.Module;
import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.setting.Setting;
import com.minimals.client.module.setting.StringSetting;
import com.minimals.client.ui.Animation;
import com.minimals.client.ui.CategoryTabWidget;
import com.minimals.client.ui.SettingWidgets;
import com.minimals.client.ui.SettingsPanelWidget;
import com.minimals.client.ui.TextFieldRowWidget;
import com.minimals.client.ui.GearButtonWidget;
import com.minimals.client.ui.HeaderIconButtonWidget;
import com.minimals.client.ui.InfoRowWidget;
import com.minimals.client.ui.KeybindRowWidget;
import com.minimals.client.ui.ModuleRowWidget;
import com.minimals.client.ui.SearchFieldWidget;
import com.minimals.client.ui.UiRenderer;
import com.minimals.client.ui.hud.HudEditorScreen;
import com.minimals.client.waypoint.WaypointListScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
    private static final int HEADER_BTN = 26;
    private static final int ROW_H = 24;
    private static final int SETTING_ROW_H = 22;
    private static final int ROW_GAP = 4;
    private static final int CONTENT_PADDING = 14;
    private static final int SETTING_INDENT = 12;
    private static final int SCROLL_STEP = 16;
    private static final int GEAR_SIZE = 22;

    /** Selected category; null means the synthetic "All" tab (every module). */
    private static Module.Category activeCategory = Module.Category.COMBAT;
    /** Category to return to when the search box is cleared. Never null. */
    private static Module.Category lastCategory = Module.Category.COMBAT;
    /** Text in the header search box; kept across reopening the menu. */
    private static String searchQuery = "";

    /** Name typed in the Config row; Save/Load act on it. Survives reopening the menu. */
    private static final StringSetting CONFIG_NAME = new StringSetting("Config", ConfigManager.DEFAULT_NAME, 24);
    /** One-line result of the last Save/Load, shown under the buttons. */
    private static String configStatus = "";

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
    private SearchFieldWidget searchField;
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

    /** Factory for other packages (the RShift choice screen) since the constructor is protected. */
    public static MenuScreen create() {
        return new MenuScreen();
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

        // Header: search field + HUD editor button, right of the "Minimals" title area.
        int headerX = px + SIDEBAR_W + CONTENT_PADDING;
        int headerRight = px + PANEL_W - CONTENT_PADDING;
        int headerY = py + (HEADER_H - HEADER_BTN) / 2;
        // Two icon buttons at the right (HUD editor, Waypoints); the search box takes the rest.
        int searchW = headerRight - headerX - (HEADER_BTN + ROW_GAP) * 2;
        searchField = new SearchFieldWidget(headerX, headerY, searchW, HEADER_BTN, searchQuery, this::onSearchChanged);
        addRenderableWidget(searchField);
        chromeWidgets.add(searchField);

        HeaderIconButtonWidget waypoints = new HeaderIconButtonWidget(
                headerRight - HEADER_BTN * 2 - ROW_GAP, headerY, HEADER_BTN,
                "waypoint/locate", "Waypoints",
                () -> Minecraft.getInstance().gui.setScreen(new WaypointListScreen(this)));
        addRenderableWidget(waypoints);
        chromeWidgets.add(waypoints);

        HeaderIconButtonWidget hudEditor = new HeaderIconButtonWidget(
                headerRight - HEADER_BTN, headerY, HEADER_BTN,
                "editor", "HUD Editor",
                () -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen()));
        addRenderableWidget(hudEditor);
        chromeWidgets.add(hudEditor);

        // Sidebar tabs. "All" only exists while there is a search query.
        int tabY = py + HEADER_H;
        int i = 0;
        if (!searchQuery.isEmpty()) {
            CategoryTabWidget allTab = CategoryTabWidget.all(
                    px + 10, tabY + i * (TAB_H + 2), SIDEBAR_W - 20, TAB_H,
                    () -> !settingsOpen && activeCategory == null,
                    () -> {
                        activeCategory = null;
                        settingsOpen = false;
                        scrollOffset = 0;
                        rebuildContent();
                    });
            addRenderableWidget(allTab);
            chromeWidgets.add(allTab);
            i++;
        }
        for (Module.Category category : Module.Category.values()) {
            CategoryTabWidget tab = new CategoryTabWidget(
                    px + 10, tabY + i * (TAB_H + 2), SIDEBAR_W - 20, TAB_H,
                    category,
                    () -> !settingsOpen && activeCategory == category,
                    selected -> {
                        activeCategory = selected;
                        lastCategory = selected;
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

    /**
     * Search text changed. Typing switches to the All tab; clearing the box goes back to the
     * last real category. Tabs are rebuilt because "All" appears/disappears with the query.
     */
    private void onSearchChanged(String query) {
        boolean hadQuery = !searchQuery.isEmpty();
        searchQuery = query;
        boolean hasQuery = !query.isEmpty();
        settingsOpen = false;
        scrollOffset = 0;
        if (hasQuery && !hadQuery) {
            activeCategory = null;
        } else if (!hasQuery) {
            activeCategory = lastCategory;
        }
        if (hasQuery != hadQuery) {
            // "All" tab added/removed: rebuild the whole screen, keeping the field focused.
            this.rebuildWidgets();
            if (searchField != null) {
                setFocused(searchField);
                searchField.setFocused(true);
            }
        } else {
            rebuildContent();
        }
    }

    /** Modules shown in the list: the active category, or everything on All; filtered by search. */
    private List<Module> visibleModules() {
        List<Module> base = activeCategory == null
                ? ModuleManager.getAllModules()
                : ModuleManager.getModules(activeCategory);
        if (searchQuery.isEmpty()) {
            return base;
        }
        String needle = searchQuery.toLowerCase(java.util.Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Module module : base) {
            if (module.getName().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                result.add(module);
            }
        }
        return result;
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

        for (Module module : visibleModules()) {
            ModuleRowWidget row = new ModuleRowWidget(contentX, y, contentW, ROW_H, module,
                    () -> EXPANDED.contains(module), () -> toggleExpanded(module));
            track(row);
            y += ROW_H + ROW_GAP;

            if (EXPANDED.contains(module)) {
                int settingX = contentX + SETTING_INDENT;
                int settingW = contentW - SETTING_INDENT;
                int panelTop = y - ROW_GAP / 2;
                SettingsPanelWidget backdrop = new SettingsPanelWidget(settingX - 4, panelTop, settingW + 4, 0);
                track(backdrop);   // tracked first so it draws behind its rows
                for (Setting<?> setting : module.getSettings()) {
                    track(SettingWidgets.create(settingX + 4, y, settingW - 4, SETTING_ROW_H, setting));
                    y += SettingWidgets.heightOf(setting, SETTING_ROW_H) + ROW_GAP;
                }
                KeybindRowWidget keybind = new KeybindRowWidget(settingX + 4, y, settingW - 4, SETTING_ROW_H, module);
                keybindRows.add(keybind);
                track(keybind);
                y += SETTING_ROW_H + ROW_GAP;
                backdrop.setHeight(y - panelTop - ROW_GAP / 2);
            }
        }

        if (visibleModules().isEmpty()) {
            track(new InfoRowWidget(contentX, y, contentW, SETTING_ROW_H,
                    searchQuery.isEmpty() ? "No modules" : "No modules match '" + searchQuery + "'"));
            y += SETTING_ROW_H + ROW_GAP;
        }

        contentHeight = y - viewportTop();
        clampScroll();
        applyScroll();
    }

    /**
     * Global settings page (opened with the gear): animations, font, opacity, smooth GUI
     * (anti-aliased rounded corners) and the client-side nickname with its style and colour.
     */
    private void buildSettingsPage(int contentX, int y, int contentW) {
        for (Setting<?> setting : ClientSettings.ALL) {
            track(SettingWidgets.create(contentX, y, contentW, SETTING_ROW_H, setting));
            y += SettingWidgets.heightOf(setting, SETTING_ROW_H) + ROW_GAP;
        }
        // Config: named files in <config dir>/minimals/, one file per config so it can be shared.
        track(new TextFieldRowWidget(contentX, y, contentW, SETTING_ROW_H, CONFIG_NAME, "default"));
        y += SETTING_ROW_H + ROW_GAP;

        int half = (contentW - ROW_GAP) / 2;
        Button saveBtn = Button.builder(Component.literal("Save Config"), btn -> {
            String name = ConfigManager.sanitize(CONFIG_NAME.get());
            configStatus = ConfigManager.save(name) ? "Saved '" + name + "'" : "Could not save '" + name + "'";
        }).bounds(contentX, y, half, SETTING_ROW_H).build();
        track(saveBtn);
        Button loadBtn = Button.builder(Component.literal("Load Config"), btn -> {
            String name = ConfigManager.sanitize(CONFIG_NAME.get());
            configStatus = ConfigManager.load(name) ? "Loaded '" + name + "'" : "No config named '" + name + "'";
            rebuildContent();
        }).bounds(contentX + half + ROW_GAP, y, contentW - half - ROW_GAP, SETTING_ROW_H).build();
        track(loadBtn);
        y += SETTING_ROW_H + ROW_GAP;

        String available = String.join(", ", ConfigManager.list());
        String info = configStatus.isEmpty() ? "Files: config/minimals/" : configStatus;
        track(new InfoRowWidget(contentX, y, contentW, SETTING_ROW_H, info));
        y += SETTING_ROW_H + ROW_GAP;
        if (!available.isEmpty()) {
            track(new InfoRowWidget(contentX, y, contentW, SETTING_ROW_H, "Available: " + available));
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
            // Visible while it overlaps the viewport (clipped by scissor when drawn), so tall
            // rows like the colour wheel no longer vanish until fully scrolled into view.
            boolean inside = widget.getY() + widget.getHeight() > top && widget.getY() < bottom;
            widget.visible = inside;
            // SettingsPanelWidget (the backdrop behind an expanded module's rows) is permanently
            // non-interactive: its constructor sets active = false so isMouseOver() never fires
            // for it and it never intercepts a click meant for the rows drawn on top of it. This
            // used to blindly overwrite that with `inside`, which is true almost the whole time
            // the panel is on screen (it spans every row inside it) - so after the very first
            // rebuild, the backdrop went active again, got picked first by getChildAt() (it was
            // tracked before its rows) and its own mouseClicked() always returns false, which
            // stops the click there and makes every setting/keybind row in every module
            // unclickable. Keep it permanently inactive instead of re-deriving it here.
            if (!(widget instanceof SettingsPanelWidget)) {
                widget.active = inside;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Partially scrolled rows are clipped visually; don't let their hidden part take clicks.
        if (event.y() < viewportTop() || event.y() > viewportBottom()) {
            for (AbstractWidget widget : contentWidgets) {
                if (widget.isMouseOver(event.x(), event.y())) {
                    return false;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // The wheel only scrolls the list; sliders are drag-only so scrolling never edits them.
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
        if (searchField != null && searchField.isFocused()) {
            return true;
        }
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

            // hairline separating the header from the module list
            graphics.fill(px + SIDEBAR_W, py + HEADER_H - 1, px + PANEL_W - PANEL_RADIUS, py + HEADER_H,
                    UiRenderer.withOpacity(0x22FFFFFF));

            for (AbstractWidget widget : chromeWidgets) {
                widget.extractRenderState(graphics, mouseX, mouseY, delta);
            }
            graphics.enableScissor(px + SIDEBAR_W, viewportTop(), px + PANEL_W, viewportBottom());
            for (AbstractWidget widget : contentWidgets) {
                Animation row = rowFade.get(widget);
                UiRenderer.setFade(openFade.get() * (row == null ? 1f : row.get()));
                widget.extractRenderState(graphics, mouseX, mouseY, delta);
            }
            graphics.disableScissor();
        } finally {
            UiRenderer.setFade(1f);
        }
    }

    @Override
    public void removed() {
        UiRenderer.setFade(1f);
        ConfigManager.autoSave();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
