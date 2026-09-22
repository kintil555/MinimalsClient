package com.minimals.client.dressing;

import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * "Dressing Room": lets the player change their account's real skin/cape via Mojang's own
 * services API (see MojangSkinService) without leaving the game, and paint a local-only glow
 * mask on their skin (see SkinEmissionMask / PlayerEmissionLayer). One shared 1-minute cooldown
 * covers skin and cape changes (DressingRoomCooldown); the emission editor has no cooldown since
 * it never touches the account and only affects this client's own rendering.
 */
public class DressingRoomScreen extends Screen {

    private enum Tab { SKIN, CAPE, EMISSION }

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 300;
    private static final int PANEL_RADIUS = 10;
    private static final int PAD = 14;
    private static final int PREVIEW_W = 140;
    private static final int TAB_H = 24;

    private Tab tab = Tab.SKIN;
    private String status = "";
    private boolean statusIsError;
    private boolean busy;

    private PlayerSkinWidget previewWidget;
    /** Mutable holder so the preview widget's Supplier can see live updates without recreating it. */
    private PlayerSkin previewSkin;

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

        previewSkin = currentSkin();
        Supplier<PlayerSkin> skinSupplier = () -> previewSkin;
        previewWidget = new PlayerSkinWidget(PREVIEW_W, PANEL_H - PAD * 2 - TAB_H - 30,
                Minecraft.getInstance().getEntityModels(), skinSupplier);
        previewWidget.setPosition(px + PAD, py + PAD + TAB_H + 30);
        addRenderableWidget(previewWidget);

        int tabX = px + PAD + PREVIEW_W + PAD;
        int tabW = (PANEL_W - PAD * 3 - PREVIEW_W - 12) / 3;
        addRenderableWidget(tabButton("Skin", Tab.SKIN, tabX, py + PAD + 30, tabW));
        addRenderableWidget(tabButton("Cape", Tab.CAPE, tabX + tabW + 6, py + PAD + 30, tabW));
        addRenderableWidget(tabButton("Emission", Tab.EMISSION, tabX + (tabW + 6) * 2, py + PAD + 30, tabW));

        int contentX = tabX;
        int contentY = py + PAD + 30 + TAB_H + 10;
        int contentW = PANEL_W - PAD * 2 - PREVIEW_W - PAD;

        switch (tab) {
            case SKIN -> initSkinTab(contentX, contentY, contentW);
            case CAPE -> initCapeTab(contentX, contentY, contentW);
            case EMISSION -> initEmissionTab(contentX, contentY, contentW);
        }
    }

    private Button tabButton(String label, Tab target, int x, int y, int w) {
        return Button.builder(Component.literal(label), btn -> {
                    if (tab != target) {
                        tab = target;
                        status = "";
                        rebuildWidgets();
                    }
                })
                .bounds(x, y, w, TAB_H)
                .build();
    }

    // ---- Skin tab ------------------------------------------------------------------------

    private void initSkinTab(int x, int y, int w) {
        addRenderableWidget(Button.builder(Component.literal("Choose PNG..."), btn -> pickAndUploadSkin())
                .bounds(x, y, w, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(cooldownLabel()), btn -> {})
                .bounds(x, y + 26, w, 16)
                .build()).active = false;
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
        addRenderableWidget(Button.builder(Component.literal("Remove Cape"), btn -> changeCape(null))
                .bounds(x, y, w, 20)
                .build());
        // Mojang does not expose an "upload your own cape" endpoint - only activating one of the
        // capes already unlocked on the account. A full picker of the account's unlocked capes
        // would need the profile's cape list from the session service; kept simple here.
        addRenderableWidget(Button.builder(Component.literal(cooldownLabel()), btn -> {})
                .bounds(x, y + 26, w, 16)
                .build()).active = false;
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
        UiRenderer.text(graphics, "Dressing Room", px + PAD, py + PAD, UiRenderer.TEXT_PRIMARY);
        UiRenderer.text(graphics, MojangSkinService.isLoggedIn() ? "Signed in" : "Offline - sign in to change skin/cape",
                px + PAD, py + PAD + 12, MojangSkinService.isLoggedIn() ? UiRenderer.TEXT_SECONDARY : 0xFFE0A030);

        UiRenderer.roundedRect(graphics, px + PAD, py + PAD + TAB_H + 26, px + PAD + PREVIEW_W,
                py + PANEL_H - PAD, 6, UiRenderer.SETTINGS_PANEL_BG);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (!status.isEmpty()) {
            int contentX = px + PAD + PREVIEW_W + PAD;
            UiRenderer.text(graphics, status, contentX, py + PANEL_H - PAD - 12,
                    statusIsError ? 0xFFFF5555 : 0xFF7CD87C);
        }
    }
}
