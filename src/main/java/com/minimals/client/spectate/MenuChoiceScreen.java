package com.minimals.client.spectate;

import com.minimals.client.MenuScreen;
import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Shown when RShift is pressed. Two stacked buttons: "Menu" (dark blue-black) opens the
 * normal ClickGUI, "Spectate" (dark red) opens the player list.
 */
public class MenuChoiceScreen extends Screen {

    private static final int BTN_W = 150;
    private static final int BTN_H = 30;
    private static final int GAP = 8;

    public static final int MENU_BG = 0xF0101828;
    public static final int MENU_BG_HOVER = 0xF01B2A45;
    public static final int SPECTATE_BG = 0xF0701212;
    public static final int SPECTATE_BG_HOVER = 0xF09A1A1A;

    public MenuChoiceScreen() {
        super(Component.literal("Minimals"));
    }

    @Override
    protected void init() {
        int x = (this.width - BTN_W) / 2;
        int y = (this.height - (BTN_H * 2 + GAP)) / 2;
        addRenderableWidget(new ChoiceButton(x, y, "Menu", MENU_BG, MENU_BG_HOVER,
                () -> Minecraft.getInstance().gui.setScreen(MenuScreen.create())));
        addRenderableWidget(new ChoiceButton(x, y + BTN_H + GAP, "Spectate", SPECTATE_BG, SPECTATE_BG_HOVER,
                () -> Minecraft.getInstance().gui.setScreen(new SpectateScreen())));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class ChoiceButton extends Button {
        private final int bg;
        private final int bgHover;
        private final Runnable action;
        private final String label;

        ChoiceButton(int x, int y, String label, int bg, int bgHover, Runnable action) {
            super(x, y, BTN_W, BTN_H, Component.literal(label), btn -> { }, DEFAULT_NARRATION);
            this.label = label;
            this.bg = bg;
            this.bgHover = bgHover;
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight(), 6,
                    isHovered() ? bgHover : bg);
            int tw = UiRenderer.textWidth(label);
            UiRenderer.text(graphics, label, getX() + (getWidth() - tw) / 2,
                    getY() + (getHeight() - 8) / 2, UiRenderer.TEXT_PRIMARY);
        }
    }
}
