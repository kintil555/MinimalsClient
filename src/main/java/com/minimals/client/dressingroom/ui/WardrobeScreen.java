package com.minimals.client.dressingroom.ui;

import com.minimals.client.dressingroom.DressingRoomManager;
import com.minimals.client.dressingroom.Outfit;
import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelType;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern Wardrobe UI: minimalist sidebar (left) with actions + live self-preview, card grid
 * (main area) of saved outfits. Mirrors Essential Mod's Wardrobe layout while reusing vanilla
 * PlayerSkinWidget for every 3D render (no custom model renderer needed).
 */
public class WardrobeScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 110;
    private static final int CARD_W = 96;
    private static final int CARD_H = 110;
    private static final int CARD_GAP = 8;
    private static final int GRID_PADDING = 14;

    private final Screen parent;
    private final List<OutfitCard> cards = new ArrayList<>();
    private PlayerSkinWidget selfPreview;
    private OutfitContextMenu activeContextMenu;

    public WardrobeScreen(Screen parent) {
        super(Component.literal("Wardrobe"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DressingRoomManager.get().load();
        buildSidebar();
        buildGrid();
    }

    private void buildSidebar() {
        int sx = 10;
        int sy = 10;

        selfPreview = new PlayerSkinWidget(SIDEBAR_WIDTH - 20, 100, Minecraft.getInstance().getEntityModels(),
            DressingRoomManager.get().activeSkinSupplierOrReal());
        selfPreview.setX(sx + 10);
        selfPreview.setY(sy);
        addRenderableWidget(selfPreview);

        int by = sy + 108;
        addRenderableWidget(Button.builder(Component.literal("New Outfit"), b -> createOutfitFromCurrent())
            .bounds(sx, by, SIDEBAR_WIDTH - 10, 20).build());
        by += 24;
        addRenderableWidget(Button.builder(Component.literal("Set Skin File"), b -> pickSkinFile())
            .bounds(sx, by, SIDEBAR_WIDTH - 10, 20).build());
        by += 24;
        addRenderableWidget(Button.builder(Component.literal("Set Cape File"), b -> pickCapeFile())
            .bounds(sx, by, SIDEBAR_WIDTH - 10, 20).build());
        by += 24;
        addRenderableWidget(Button.builder(Component.literal("Slim / Wide"), b -> toggleModelType())
            .bounds(sx, by, SIDEBAR_WIDTH - 10, 20).build());
        by += 30;
        addRenderableWidget(Button.builder(Component.literal("Unequip"), b -> {
                DressingRoomManager.get().unequip();
                refreshCards();
            })
            .bounds(sx, by, SIDEBAR_WIDTH - 10, 20).build());
        by += 30;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(sx, by, SIDEBAR_WIDTH - 10, 20).build());
    }

    private void buildGrid() {
        cards.clear();
        int gridX = SIDEBAR_WIDTH + GRID_PADDING;
        int gridY = GRID_PADDING;
        int columns = Math.max(1, (this.width - gridX - GRID_PADDING) / (CARD_W + CARD_GAP));

        List<Outfit> outfits = DressingRoomManager.get().outfits();
        for (int i = 0; i < outfits.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int x = gridX + col * (CARD_W + CARD_GAP);
            int y = gridY + row * (CARD_H + CARD_GAP);

            Outfit outfit = outfits.get(i);
            OutfitCard card = new OutfitCard(x, y, CARD_W, CARD_H, outfit,
                this::equipOutfit,
                this::openContextMenu);
            cards.add(card);
            addRenderableWidget(card);
        }
    }

    private void equipOutfit(Outfit outfit) {
        DressingRoomManager.get().equip(outfit);
    }

    private void openContextMenu(Outfit outfit) {
        double mx = Minecraft.getInstance().mouseHandler.xpos() * this.width / Minecraft.getInstance().getWindow().getScreenWidth();
        double my = Minecraft.getInstance().mouseHandler.ypos() * this.height / Minecraft.getInstance().getWindow().getScreenHeight();
        if (activeContextMenu != null) removeWidget(activeContextMenu);
        activeContextMenu = new OutfitContextMenu((int) mx, (int) my, outfit,
            () -> { removeWidget(activeContextMenu); activeContextMenu = null; },
            this::refreshCards);
        addRenderableWidget(activeContextMenu);
    }

    private void refreshCards() {
        clearWidgets();
        buildSidebar();
        buildGrid();
    }

    private void createOutfitFromCurrent() {
        DressingRoomManager mgr = DressingRoomManager.get();
        Outfit outfit = mgr.createOutfit("New Outfit " + (mgr.outfits().size() + 1), null, null, PlayerModelType.WIDE);
        refreshCards();
    }

    private void pickSkinFile() {
        // File dialog integration point: hook up to a platform file chooser (e.g. TinyFileDialogs)
        // and call DressingRoomManager.get().equipped().ifPresent(o -> { o.setSkinPath(path);
        // DressingRoomManager.get().invalidateTextureCache(o); }); Left as an extension point since
        // native file-picker wiring is project/OS specific.
    }

    private void pickCapeFile() {
        // See pickSkinFile() — same pattern for capePath.
    }

    private void toggleModelType() {
        DressingRoomManager.get().equipped().ifPresent(o -> {
            o.setModelType(o.modelType() == PlayerModelType.SLIM ? PlayerModelType.WIDE : PlayerModelType.SLIM);
            DressingRoomManager.get().invalidateTextureCache(o);
            DressingRoomManager.get().save();
        });
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        UiRenderer.fillBackground(graphics, this.width, this.height);
        UiRenderer.roundedRect(graphics, 4, 4, SIDEBAR_WIDTH, this.height - 8, 6, UiRenderer.PANEL_BG);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        DressingRoomManager.get().save();
        Minecraft.getInstance().setScreen(parent);
    }
}
