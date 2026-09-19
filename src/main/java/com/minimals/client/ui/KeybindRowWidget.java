package com.minimals.client.ui;

import com.minimals.client.module.Module;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * "Keybind" row shown at the bottom of every module dropdown. Click to start listening,
 * then press a key. ESC cancels, Delete/Backspace clears the binding.
 */
public class KeybindRowWidget extends Button {

    private final Module module;
    private boolean listening;

    public KeybindRowWidget(int x, int y, int width, int height, Module module) {
        super(x, y, width, height, Component.literal("Keybind"), btn -> { }, DEFAULT_NARRATION);
        this.module = module;
    }

    public boolean isListening() {
        return listening;
    }

    public void stopListening() {
        listening = false;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        listening = !listening;
    }

    /**
     * Feeds a key press to this row. Returns true if the event was consumed.
     */
    public boolean handleKey(KeyEvent event) {
        if (!listening) {
            return false;
        }
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            // cancel, keep the previous binding
        } else if (key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
            module.setKeyBind(Module.KEY_NONE);
        } else {
            module.setKeyBind(key);
        }
        listening = false;
        return true;
    }

    private String keyLabel() {
        if (listening) {
            return "Press a key...";
        }
        if (!module.hasKeyBind()) {
            return "None";
        }
        return InputConstants.getKey(new KeyEvent(module.getKeyBind(), 0, 0)).getDisplayName().getString();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int w = getWidth();
        int h = getHeight();
        if (isHovered() || listening) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + h, 5, UiRenderer.ROW_BG_HOVER);
        }

        int textY = getY() + (h - 8) / 2;
        UiRenderer.text(graphics, module.isHoldKeybind() ? "Hold Key" : "Keybind", getX() + 10, textY, UiRenderer.TEXT_SECONDARY);

        String label = keyLabel();
        int color = listening ? UiRenderer.ACCENT : UiRenderer.TEXT_PRIMARY;
        UiRenderer.text(graphics, label, getX() + w - UiRenderer.textWidth(label) - 10, textY, color);
    }
}
