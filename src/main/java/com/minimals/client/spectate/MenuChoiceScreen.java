package com.minimals.client.spectate;

import com.minimals.client.MenuScreen;
import com.minimals.client.MinimalClientMod;
import com.minimals.client.replay.ReplayRecorder;
import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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
    public static final int RECORD_BG = 0xF0141417;
    public static final int RECORD_BG_HOVER = 0xF0262630;
    /** Red while a clip is being recorded. */
    public static final int RECORDING_TINT = 0xFFE23B3B;

    public MenuChoiceScreen() {
        super(Component.literal("Minimals"));
    }

    @Override
    protected void init() {
        int x = (this.width - BTN_W) / 2;
        int y = (this.height - (BTN_H * 2 + GAP)) / 2;
        addRenderableWidget(new ChoiceButton(x, y, "Menu", MENU_BG, MENU_BG_HOVER,
                () -> Minecraft.getInstance().gui.setScreen(MenuScreen.create())));
        int spectateY = y + BTN_H + GAP;
        addRenderableWidget(new ChoiceButton(x, spectateY, "Spectate", SPECTATE_BG, SPECTATE_BG_HOVER,
                () -> Minecraft.getInstance().gui.setScreen(new SpectateScreen())));
        // Record: square icon button right of Spectate, same height.
        addRenderableWidget(new RecordButton(x + BTN_W + GAP, spectateY));
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

    /**
     * Square button, right of Spectate. Shows the play icon when idle (click starts a clip) and
     * the pause icon, tinted red, while recording (click saves the clip). Unavailable when there is
     * no live world to record or a replay is playing.
     */
    private static final class RecordButton extends Button {
        private static final int ICON = 16;
        private static final Identifier START = Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID,
                "textures/gui/record.png");
        private static final Identifier STOP = Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID,
                "textures/gui/recording.png");

        RecordButton(int x, int y) {
            super(x, y, BTN_H, BTN_H, Component.literal("Record"), btn -> { }, DEFAULT_NARRATION);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            if (ReplayRecorder.isRecording()) {
                ReplayRecorder.stop();
            } else if (ReplayRecorder.canRecord()) {
                ReplayRecorder.start();
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean recording = ReplayRecorder.isRecording();
            boolean usable = recording || ReplayRecorder.canRecord();
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight(), 6,
                    isHovered() && usable ? RECORD_BG_HOVER : RECORD_BG);
            int tint = !usable ? UiRenderer.TEXT_SECONDARY
                    : recording ? RECORDING_TINT : UiRenderer.TEXT_PRIMARY;
            int ix = getX() + (getWidth() - ICON) / 2;
            int iy = getY() + (getHeight() - ICON) / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, recording ? STOP : START, ix, iy,
                    0f, 0f, ICON, ICON, ICON, ICON, UiRenderer.withOpacity(tint));
        }
    }
}
