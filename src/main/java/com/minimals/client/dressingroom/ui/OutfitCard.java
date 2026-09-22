package com.minimals.client.dressingroom.ui;

import com.minimals.client.dressingroom.DressingRoomManager;
import com.minimals.client.dressingroom.Outfit;
import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.function.Consumer;

/**
 * A single card in the Outfit grid: a live 3D PlayerSkinWidget preview + name label, with
 * left-click = Equip, right-click = context menu (Rename / Delete).
 */
public class OutfitCard extends AbstractWidget {
    private static final int PREVIEW_SIZE = 78;

    private final Outfit outfit;
    private final PlayerSkinWidget previewWidget;
    private final Consumer<Outfit> onEquip;
    private final Consumer<Outfit> onContextMenu;

    public OutfitCard(int x, int y, int width, int height, Outfit outfit,
                       Consumer<Outfit> onEquip, Consumer<Outfit> onContextMenu) {
        super(x, y, width, height, CommonComponents.EMPTY);
        this.outfit = outfit;
        this.onEquip = onEquip;
        this.onContextMenu = onContextMenu;

        // Resolve the skin this card should render: the outfit's own composed skin if it's the
        // equipped one, otherwise compose it fresh from the real skin + this outfit's overrides.
        this.previewWidget = new PlayerSkinWidget(
            PREVIEW_SIZE, PREVIEW_SIZE,
            Minecraft.getInstance().getEntityModels(),
            () -> resolvePreviewSkin()
        );
        this.previewWidget.setX(x + (width - PREVIEW_SIZE) / 2);
        this.previewWidget.setY(y + 6);
    }

    private PlayerSkin resolvePreviewSkin() {
        DressingRoomManager mgr = DressingRoomManager.get();
        if (mgr.isEquipped(outfit)) {
            return mgr.activeSkin().orElse(fallbackRealSkin());
        }
        // Not equipped: still show a live composite so the card never shows a static icon.
        return mgr.composePreview(outfit).orElse(fallbackRealSkin());
    }

    private PlayerSkin fallbackRealSkin() {
        var mc = Minecraft.getInstance();
        if (mc.getPlayer() == null || mc.getConnection() == null) return null;
        return mc.getConnection().getPlayerInfo(mc.getPlayer().getUUID()).getSkin();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean equipped = DressingRoomManager.get().isEquipped(outfit);
        boolean hovered = this.isHovered();

        UiRenderer.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), 6,
            equipped ? UiRenderer.ACCENT_BG : (hovered ? UiRenderer.ROW_HOVER : UiRenderer.ROW_BG));
        if (equipped) {
            UiRenderer.roundedRectOutline(graphics, getX(), getY(), getWidth(), getHeight(), 6, UiRenderer.ACCENT);
        }

        previewWidget.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(Minecraft.getInstance().font, outfit.name(),
            getX() + getWidth() / 2, getY() + getHeight() - 14, UiRenderer.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Let the live preview handle drag-to-rotate first.
        if (previewWidget.isMouseOver(event.x(), event.y()) && event.button() != 1) {
            return previewWidget.mouseClicked(event, doubleClick);
        }
        if (event.button() == 1) { // right click
            onContextMenu.accept(outfit);
            return true;
        }
        if (event.button() == 0) { // left click
            onEquip.accept(outfit);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return previewWidget.mouseDragged(event, dragX, dragY);
    }

    public Outfit outfit() { return outfit; }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, outfit.name());
    }
}
