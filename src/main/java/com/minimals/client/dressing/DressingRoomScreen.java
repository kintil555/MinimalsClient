package com.minimals.client.dressing;

import com.minimals.client.ui.DropdownWidget;
import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * "Dressing Room": lets the player change their account's real skin/cape via Mojang's own
 * services API (see MojangSkinService) without leaving the game, keep a local library of skins
 * they've used before (see SkinLibrary), save/apply named presets bundling skin+model+cape+glow
 * mask together (see PresetManager), and paint a local-only glow mask on their skin (see
 * SkinEmissionMask / PlayerEmissionLayer). One shared 1-minute cooldown covers skin and cape
 * changes (DressingRoomCooldown); the emission editor and library/preset bookkeeping have no
 * cooldown since they never touch the account by themselves.
 */
public class DressingRoomScreen extends Screen {

    private enum Tab { SKIN, CAPE, PRESETS, EMISSION }

    // +20% over the original 420x300 / 140 preview.
    private static final int PANEL_W = 504;
    private static final int PANEL_H = 360;
    private static final int PANEL_RADIUS = 10;
    private static final int PAD = 14;
    private static final int PREVIEW_W = 168;
    private static final int TAB_H = 24;

    private Tab tab = Tab.SKIN;
    private String status = "";
    private boolean statusIsError;
    private boolean busy;

    private PlayerSkinWidget previewWidget;
    /** Mutable holder so the preview widget's Supplier can see live updates without recreating it. */
    private PlayerSkin previewSkin;

    /** Every dropdown currently laid out, so open/close-on-outside-click can be handled generically
     *  instead of one if-chain per tab. */
    private final List<DropdownWidget> activeDropdowns = new ArrayList<>();

    // Skin tab: three sources. Upload File / Fetch by Username apply directly (unchanged
    // behaviour); Library re-applies a previously saved skin with no network round trip.
    private enum SkinSource { UPLOAD_FILE, FETCH_USERNAME, LIBRARY }

    private static final List<String> SKIN_SOURCE_NAMES =
            List.of("Upload File", "Fetch by Username", "Library");

    private SkinSource skinSource = SkinSource.UPLOAD_FILE;
    private DropdownWidget skinSourceDropdown;
    private EditBox usernameField;
    private MojangSkinService.FetchedSkin fetchedSkin;
    private boolean fetchingSkin;
    private Path lastLocalSkinFile;

    private DropdownWidget libraryDropdown;
    private List<SkinLibrary.Entry> librarySkins = List.of();
    private SkinLibrary.Entry selectedLibrarySkin;

    // Cape tab: dropdown of capes already unlocked on this account (Mojang has no "upload a
    // cape" endpoint - only activating one already granted to the account).
    private DropdownWidget capeDropdown;
    private List<MojangSkinService.Cape> ownedCapes = List.of();
    private boolean capesLoaded;

    // Presets tab: apply/delete a saved bundle; "Save current as preset" captures whatever the
    // account is wearing right now (skin left unset unless it came from the Library, since a
    // freshly-applied skin has no library id to reference until saved there too).
    private DropdownWidget presetDropdown;
    private List<PresetManager.Preset> presets = List.of();
    private PresetManager.Preset selectedPreset;
    private EditBox presetNameField;

    public DressingRoomScreen() {
        super(Component.literal("Dressing Room"));
    }

    private int panelX() {
        return (width - PANEL_W) / 2;
    }

    private int panelY() {
        return (height - PANEL_H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int px = panelX();
        int py = panelY();
        activeDropdowns.clear();

        previewSkin = currentSkin();
        Supplier<PlayerSkin> skinSupplier = () -> previewSkin;
        previewWidget = new PlayerSkinWidget(PREVIEW_W, PANEL_H - PAD * 2 - TAB_H - 30,
                Minecraft.getInstance().getEntityModels(), skinSupplier);
        previewWidget.setPosition(px + PAD, py + PAD + TAB_H + 30);
        addRenderableWidget(previewWidget);

        int tabX = px + PAD + PREVIEW_W + PAD;
        int tabW = (PANEL_W - PAD * 3 - PREVIEW_W - 18) / 4;
        addRenderableWidget(tabButton("Skin", Tab.SKIN, tabX, py + PAD + 30, tabW));
        addRenderableWidget(tabButton("Cape", Tab.CAPE, tabX + (tabW + 6), py + PAD + 30, tabW));
        addRenderableWidget(tabButton("Presets", Tab.PRESETS, tabX + (tabW + 6) * 2, py + PAD + 30, tabW));
        addRenderableWidget(tabButton("Emission", Tab.EMISSION, tabX + (tabW + 6) * 3, py + PAD + 30, tabW));

        int contentX = tabX;
        int contentY = py + PAD + 30 + TAB_H + 10;
        int contentW = PANEL_W - PAD * 2 - PREVIEW_W - PAD;

        switch (tab) {
            case SKIN -> initSkinTab(contentX, contentY, contentW);
            case CAPE -> initCapeTab(contentX, contentY, contentW);
            case PRESETS -> initPresetsTab(contentX, contentY, contentW);
            case EMISSION -> initEmissionTab(contentX, contentY, contentW);
        }
    }

    private Button tabButton(String label, Tab target, int x, int y, int w) {
        boolean active = tab == target;
        Button button = Button.builder(Component.literal(label), btn -> {
                    if (tab != target) {
                        tab = target;
                        status = "";
                        rebuildWidgets();
                    }
                })
                .bounds(x, y, w, TAB_H)
                .build();
        return new TabPillButton(button, active);
    }

    /**
     * Wraps a vanilla Button with an Essential-style pill: flat rows read as dull vanilla UI, so
     * the active tab gets a filled accent pill + glow underline and inactive tabs get a soft
     * hover tint instead of the stock 3-slice button texture. Delegates everything else to the
     * wrapped button so click handling/state stay untouched.
     */
    private static final class TabPillButton extends Button {
        private final Button delegate;
        private final boolean active;

        TabPillButton(Button delegate, boolean active) {
            super(delegate.getX(), delegate.getY(), delegate.getWidth(), delegate.getHeight(),
                    delegate.getMessage(), b -> delegate.onPress(), DEFAULT_NARRATION);
            this.delegate = delegate;
            this.active = active;
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            delegate.onPress(input);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean hovered = isHoveredOrFocused();
            int bg = active ? UiRenderer.ACCENT
                    : hovered ? UiRenderer.HEADER_BTN_BG_HOVER : UiRenderer.HEADER_BTN_BG;
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight(), 6, bg);
            if (active) {
                UiRenderer.roundedRect(graphics, getX() + 4, getY() + getHeight() - 2,
                        getX() + getWidth() - 4, getY() + getHeight(), 1, 0xFFFFFFFF);
            }
            int color = active ? 0xFFFFFFFF : UiRenderer.TEXT_SECONDARY;
            UiRenderer.centeredText(graphics, getMessage().getString(),
                    getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
        }
    }

    private <T extends DropdownWidget> T track(T dropdown) {
        activeDropdowns.add(dropdown);
        addRenderableWidget(dropdown);
        return dropdown;
    }

    // ---- Skin tab ------------------------------------------------------------------------

    private void initSkinTab(int x, int y, int w) {
        skinSourceDropdown = track(new DropdownWidget(x, y, w, "Upload File", SKIN_SOURCE_NAMES, choice -> {
            skinSource = switch (choice) {
                case "Fetch by Username" -> SkinSource.FETCH_USERNAME;
                case "Library" -> SkinSource.LIBRARY;
                default -> SkinSource.UPLOAD_FILE;
            };
            status = "";
            fetchedSkin = null;
            rebuildWidgets();
        }));
        skinSourceDropdown.setSelected(switch (skinSource) {
            case FETCH_USERNAME -> "Fetch by Username";
            case LIBRARY -> "Library";
            default -> "Upload File";
        });

        int belowY = y + 22;
        switch (skinSource) {
            case UPLOAD_FILE -> {
                addRenderableWidget(Button.builder(Component.literal("Choose PNG..."), btn -> pickAndUploadSkin())
                        .bounds(x, belowY, w, 20)
                        .build());
                if (lastLocalSkinFile != null) {
                    addRenderableWidget(Button.builder(Component.literal("Save to Library"),
                                    btn -> saveLastToLibrary())
                            .bounds(x, belowY + 24, w, 18)
                            .build());
                }
            }
            case FETCH_USERNAME -> {
                usernameField = new EditBox(UiRenderer.font(), x, belowY, w - 56, 20, Component.literal("Username"));
                usernameField.setMaxLength(16);
                usernameField.setHint(Component.literal("Player username"));
                addRenderableWidget(usernameField);
                addRenderableWidget(Button.builder(Component.literal("Find"), btn -> fetchSkinPreview())
                        .bounds(x + w - 52, belowY, 52, 20)
                        .build());
                belowY += 24;
                if (fetchedSkin != null && !fetchingSkin) {
                    addRenderableWidget(Button.builder(Component.literal("Apply this skin"), btn -> applyFetchedSkin())
                            .bounds(x, belowY, w, 18)
                            .build());
                    addRenderableWidget(Button.builder(Component.literal("Save to Library"),
                                    btn -> saveFetchedToLibrary())
                            .bounds(x, belowY + 22, w, 18)
                            .build());
                }
            }
            case LIBRARY -> {
                librarySkins = SkinLibrary.list();
                List<String> names = librarySkins.stream().map(SkinLibrary.Entry::name).toList();
                libraryDropdown = track(new DropdownWidget(x, belowY, w,
                        names.isEmpty() ? "No saved skins" : "Choose a skin...", names, this::onLibrarySkinChosen));
                if (selectedLibrarySkin != null) {
                    libraryDropdown.setSelected(selectedLibrarySkin.name());
                    addRenderableWidget(Button.builder(Component.literal("Apply this skin"),
                                    btn -> applyLibrarySkin())
                            .bounds(x, belowY + 22, w - 56, 18)
                            .build());
                    addRenderableWidget(Button.builder(Component.literal("Remove"), btn -> removeLibrarySkin())
                            .bounds(x + w - 52, belowY + 22, 52, 18)
                            .build());
                }
            }
        }
        addRenderableWidget(Button.builder(Component.literal(cooldownLabel()), btn -> {})
                .bounds(x, panelY() + PANEL_H - PAD - 34, w, 16)
                .build()).active = false;
    }

    private void onLibrarySkinChosen(String name) {
        selectedLibrarySkin = librarySkins.stream().filter(e -> e.name().equals(name)).findFirst().orElse(null);
        rebuildWidgets();
    }

    private void applyLibrarySkin() {
        if (selectedLibrarySkin == null || !DressingRoomCooldown.isReady() || busy) {
            return;
        }
        if (!MojangSkinService.isLoggedIn()) {
            setStatus("Not logged in to a Microsoft account.", true);
            return;
        }
        busy = true;
        setStatus("Applying " + selectedLibrarySkin.name() + "...", false);
        Path png = SkinLibrary.pngPath(selectedLibrarySkin);
        MojangSkinService.uploadSkin(png, selectedLibrarySkin.model())
                .thenAccept(result -> Minecraft.getInstance().execute(() -> {
                    busy = false;
                    setStatus(result.message(), !result.success());
                    if (result.success()) {
                        DressingRoomCooldown.start();
                        refreshLocalPreviewBestEffort();
                    }
                }));
    }

    private void removeLibrarySkin() {
        if (selectedLibrarySkin == null) {
            return;
        }
        SkinLibrary.remove(selectedLibrarySkin.id());
        selectedLibrarySkin = null;
        setStatus("Removed from library.", false);
        rebuildWidgets();
    }

    private void saveLastToLibrary() {
        if (lastLocalSkinFile == null) {
            return;
        }
        String name = "Skin " + (SkinLibrary.list().size() + 1);
        SkinLibrary.Entry saved = SkinLibrary.saveFromFile(lastLocalSkinFile, name, detectModelOrAsk());
        setStatus(saved != null ? "Saved to library as \"" + name + "\"." : "Could not save to library.",
                saved == null);
    }

    private void saveFetchedToLibrary() {
        if (fetchedSkin == null) {
            return;
        }
        String name = fetchedSkin.username();
        setStatus("Saving " + name + " to library...", false);
        SkinLibrary.saveFromUrl(fetchedSkin.textureUrl(), name, detectModelOrAsk())
                .thenAccept(saved -> Minecraft.getInstance().execute(() ->
                        setStatus(saved != null ? "Saved to library as \"" + name + "\"." : "Could not save to library.",
                                saved == null)));
    }

    private void fetchSkinPreview() {
        String username = usernameField == null ? "" : usernameField.getValue().trim();
        if (username.isEmpty() || fetchingSkin) {
            return;
        }
        fetchingSkin = true;
        fetchedSkin = null;
        setStatus("Looking up " + username + "...", false);
        MojangSkinService.fetchSkinByUsername(username).thenAccept(result -> Minecraft.getInstance().execute(() -> {
            fetchingSkin = false;
            if (result == null) {
                setStatus("No skin found for that username.", true);
            } else {
                fetchedSkin = result;
                setStatus("Found " + result.username() + "'s skin.", false);
            }
            rebuildWidgets();
        }));
    }

    private void applyFetchedSkin() {
        if (fetchedSkin == null || !DressingRoomCooldown.isReady() || busy) {
            return;
        }
        if (!MojangSkinService.isLoggedIn()) {
            setStatus("Not logged in to a Microsoft account.", true);
            return;
        }
        busy = true;
        setStatus("Applying skin...", false);
        MojangSkinService.applyFetchedSkin(fetchedSkin, detectModelOrAsk())
                .thenAccept(result -> Minecraft.getInstance().execute(() -> {
                    busy = false;
                    setStatus(result.message(), !result.success());
                    if (result.success()) {
                        DressingRoomCooldown.start();
                        refreshLocalPreviewBestEffort();
                    }
                }));
    }

    private void pickAndUploadSkin() {
        if (!DressingRoomCooldown.isReady() || busy) {
            return;
        }
        if (!MojangSkinService.isLoggedIn()) {
            setStatus("Not logged in to a Microsoft account.", true);
            return;
        }
        Path file = NativeFilePicker.pickPng("Choose a skin (64x64 PNG)");
        if (file == null) {
            return;
        }
        busy = true;
        setStatus("Uploading skin...", false);
        MojangSkinService.Model model = detectModelOrAsk();
        MojangSkinService.uploadSkin(file, model).thenAccept(result -> Minecraft.getInstance().execute(() -> {
            busy = false;
            setStatus(result.message(), !result.success());
            if (result.success()) {
                DressingRoomCooldown.start();
                refreshLocalPreviewBestEffort();
                lastLocalSkinFile = file;
                rebuildWidgets();
            }
        }));
    }

    private MojangSkinService.Model detectModelOrAsk() {
        // Best-effort default: keep whatever model the account currently uses.
        PlayerSkin current = currentSkin();
        return MojangSkinService.Model.from(current.model());
    }

    // ---- Cape tab --------------------------------------------------------------------------

    private void initCapeTab(int x, int y, int w) {
        if (!capesLoaded && MojangSkinService.isLoggedIn()) {
            capesLoaded = true; // guard against re-triggering the fetch on every rebuildWidgets()
            MojangSkinService.fetchOwnCapes().thenAccept(capes -> Minecraft.getInstance().execute(() -> {
                ownedCapes = capes;
                rebuildWidgets();
            }));
        }

        List<String> names = new ArrayList<>();
        names.add("(No cape)");
        for (var cape : ownedCapes) {
            names.add(cape.name());
        }
        capeDropdown = track(new DropdownWidget(x, y, w,
                ownedCapes.isEmpty() ? "No capes unlocked" : "Choose a cape...", names, this::onCapeChosen));
        String current = ownedCapes.stream().filter(MojangSkinService.Cape::active)
                .map(MojangSkinService.Cape::name).findFirst().orElse("(No cape)");
        capeDropdown.setSelected(current);

        addRenderableWidget(Button.builder(Component.literal(cooldownLabel()), btn -> {})
                .bounds(x, y + 24, w, 16)
                .build()).active = false;
    }

    private void onCapeChosen(String name) {
        if ("(No cape)".equals(name)) {
            changeCape(null);
            return;
        }
        ownedCapes.stream().filter(c -> c.name().equals(name)).findFirst()
                .ifPresent(c -> changeCape(c.id()));
    }

    private void changeCape(String capeId) {
        if (!DressingRoomCooldown.isReady() || busy) {
            return;
        }
        if (!MojangSkinService.isLoggedIn()) {
            setStatus("Not logged in to a Microsoft account.", true);
            return;
        }
        busy = true;
        setStatus("Updating cape...", false);
        var future = capeId == null ? MojangSkinService.clearCape() : MojangSkinService.activateCape(capeId);
        future.thenAccept(result -> Minecraft.getInstance().execute(() -> {
            busy = false;
            setStatus(result.message(), !result.success());
            if (result.success()) {
                DressingRoomCooldown.start();
                refreshLocalPreviewBestEffort();
            }
        }));
    }

    // ---- Presets tab -----------------------------------------------------------------------

    private void initPresetsTab(int x, int y, int w) {
        presets = PresetManager.list();
        List<String> names = presets.stream().map(PresetManager.Preset::name).toList();
        presetDropdown = track(new DropdownWidget(x, y, w,
                names.isEmpty() ? "No presets yet" : "Choose a preset...", names, this::onPresetChosen));
        if (selectedPreset != null) {
            presetDropdown.setSelected(selectedPreset.name());
        }

        int belowY = y + 24;
        if (selectedPreset != null) {
            addRenderableWidget(Button.builder(Component.literal("Apply preset"), btn -> applyPreset())
                    .bounds(x, belowY, w - 56, 18)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Delete"), btn -> deletePreset())
                    .bounds(x + w - 52, belowY, 52, 18)
                    .build());
            belowY += 24;
        }

        belowY += 6;
        presetNameField = new EditBox(UiRenderer.font(), x, belowY, w, 20, Component.literal("Preset name"));
        presetNameField.setMaxLength(24);
        presetNameField.setHint(Component.literal("New preset name"));
        addRenderableWidget(presetNameField);
        addRenderableWidget(Button.builder(Component.literal("Save current look as preset"), btn -> saveCurrentPreset())
                .bounds(x, belowY + 24, w, 20)
                .build());
    }

    private void onPresetChosen(String name) {
        selectedPreset = presets.stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
        rebuildWidgets();
    }

    private void saveCurrentPreset() {
        String name = presetNameField == null ? "" : presetNameField.getValue().trim();
        if (name.isEmpty()) {
            setStatus("Enter a name for the preset first.", true);
            return;
        }
        // Only references a library skin (uploads/fetches aren't kept as files unless saved
        // there), the account's current model, the active cape if any, and the current skin's
        // glow mask key.
        String skinId = skinSource == SkinSource.LIBRARY && selectedLibrarySkin != null
                ? selectedLibrarySkin.id() : null;
        String capeId = ownedCapes.stream().filter(MojangSkinService.Cape::active)
                .map(MojangSkinService.Cape::id).findFirst().orElse(null);
        MojangSkinService.Model model = detectModelOrAsk();
        String maskKey = skinKey();
        PresetManager.save(name, skinId, model, capeId, maskKey);
        setStatus(skinId == null
                ? "Preset saved (skin from Library only - pick a Library skin first to include one)."
                : "Preset \"" + name + "\" saved.", false);
        presetNameField.setValue("");
        rebuildWidgets();
    }

    private void applyPreset() {
        if (selectedPreset == null || !DressingRoomCooldown.isReady() || busy) {
            return;
        }
        if (!MojangSkinService.isLoggedIn()) {
            setStatus("Not logged in to a Microsoft account.", true);
            return;
        }
        PresetManager.Preset preset = selectedPreset;
        busy = true;
        setStatus("Applying preset \"" + preset.name() + "\"...", false);

        java.util.concurrent.CompletableFuture<MojangSkinService.Result> skinStep;
        if (preset.skinId() != null) {
            SkinLibrary.Entry entry = SkinLibrary.byId(preset.skinId());
            skinStep = entry != null
                    ? MojangSkinService.uploadSkin(SkinLibrary.pngPath(entry), preset.model())
                    : java.util.concurrent.CompletableFuture.completedFuture(
                            MojangSkinService.Result.fail("Preset's saved skin was removed from the library."));
        } else {
            skinStep = java.util.concurrent.CompletableFuture.completedFuture(MojangSkinService.Result.ok(""));
        }

        skinStep.thenCompose(skinResult -> {
            var capeFuture = preset.capeId() == null
                    ? MojangSkinService.clearCape() : MojangSkinService.activateCape(preset.capeId());
            return capeFuture.thenApply(capeResult -> new MojangSkinService.Result(
                    skinResult.success() && capeResult.success(),
                    skinResult.message().isEmpty() ? capeResult.message() : skinResult.message()));
        }).thenAccept(result -> Minecraft.getInstance().execute(() -> {
            busy = false;
            if (preset.maskKey() != null) {
                // Copy the preset's saved mask onto whatever skin key is now active, so the glow
                // reappears under the skin the preset just applied.
                SkinEmissionMask mask = SkinEmissionMask.load(preset.maskKey(), 64, 64);
                mask.save(skinKey());
            }
            setStatus(result.success() ? "Preset applied." : result.message(), !result.success());
            DressingRoomCooldown.start();
            refreshLocalPreviewBestEffort();
        }));
    }

    private void deletePreset() {
        if (selectedPreset == null) {
            return;
        }
        PresetManager.remove(selectedPreset.id());
        selectedPreset = null;
        setStatus("Preset deleted.", false);
        rebuildWidgets();
    }

    // ---- Emission tab --------------------------------------------------------------------

    private void initEmissionTab(int x, int y, int w) {
        addRenderableWidget(Button.builder(Component.literal("Open Skin Editor"), btn ->
                        Minecraft.getInstance().gui.setScreen(new SkinEditorScreen(this)))
                .bounds(x, y, w, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Clear Glow Mask"), btn -> clearMask())
                .bounds(x, y + 26, w, 20)
                .build());
    }

    private void clearMask() {
        String key = skinKey();
        SkinEmissionMask mask = SkinEmissionMask.load(key, 64, 64);
        mask.clear();
        mask.save(key);
        setStatus("Glow mask cleared.", false);
    }

    // ---- shared helpers --------------------------------------------------------------------

    private PlayerSkin currentSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return mc.player.getSkin();
        }
        return mc.getSkinManager().createLookup(mc.getGameProfile(), false).get();
    }

    /** Identifies the current skin's mask file: the texture path is stable per uploaded skin. */
    private String skinKey() {
        return currentSkin().body().texturePath().toString();
    }

    /**
     * Mojang's textures property is only re-sent by a server on join, so a freshly uploaded skin
     * will not visually update mid-session without reconnecting. This re-reads the account's own
     * profile (bypassing the 15s SkinManager cache) as a best-effort local refresh; it may still
     * not be reflected to other players, or even to this client's own third-person view, until
     * the next join.
     */
    private void refreshLocalPreviewBestEffort() {
        previewSkin = currentSkin();
    }

    private void setStatus(String message, boolean error) {
        this.status = message;
        this.statusIsError = error;
    }

    private String cooldownLabel() {
        return DressingRoomCooldown.isReady() ? "Ready" : "Cooldown: " + DressingRoomCooldown.remainingLabel();
    }

    private DropdownWidget openDropdown() {
        for (DropdownWidget d : activeDropdowns) {
            if (d.isOpen()) {
                return d;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        DropdownWidget open = openDropdown();
        if (open != null && !open.hitTest(event.x(), event.y())) {
            open.closeIfOutside(event.x(), event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x88000000);

        int px = panelX();
        int py = panelY();
        UiRenderer.roundedRect(graphics, px, py, px + PANEL_W, py + PANEL_H, PANEL_RADIUS, UiRenderer.PANEL_BG);
        // Thin accent bar under the title, Essential-style, instead of plain flat text.
        UiRenderer.roundedRect(graphics, px + PAD, py + PAD + 13, px + PAD + 28, py + PAD + 15, 1, UiRenderer.ACCENT);
        UiRenderer.text(graphics, "Dressing Room", px + PAD, py + PAD, UiRenderer.TEXT_PRIMARY);
        boolean loggedIn = MojangSkinService.isLoggedIn();
        String badge = loggedIn ? "● Signed in" : "● Offline - sign in to change skin/cape";
        UiRenderer.text(graphics, badge, px + PAD, py + PAD + 12, loggedIn ? 0xFF7CD87C : 0xFFE0A030);

        UiRenderer.roundedRect(graphics, px + PAD, py + PAD + TAB_H + 26, px + PAD + PREVIEW_W,
                py + PANEL_H - PAD, 6, UiRenderer.SETTINGS_PANEL_BG);
        // 1px accent-tinted rim so the preview frame doesn't read as a flat grey box.
        UiRenderer.roundedRect(graphics, px + PAD, py + PAD + TAB_H + 26, px + PAD + PREVIEW_W,
                py + PAD + TAB_H + 27, 0, 0x338B5CF6);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Redraw an open dropdown's list last so its options aren't covered by buttons/fields
        // laid out below it (widgets otherwise render in the order they were added).
        DropdownWidget open = openDropdown();
        if (open != null) {
            open.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        if (!status.isEmpty()) {
            int contentX = px + PAD + PREVIEW_W + PAD;
            int color = statusIsError ? 0xFFFF5555 : 0xFF7CD87C;
            int sy = py + PANEL_H - PAD - 14;
            int sw = UiRenderer.font().width(status) + 12;
            UiRenderer.roundedRect(graphics, contentX - 4, sy - 2, contentX + sw, sy + 10, 4,
                    statusIsError ? 0x26FF5555 : 0x267CD87C);
            UiRenderer.text(graphics, status, contentX, sy, color);
        }
    }
}
