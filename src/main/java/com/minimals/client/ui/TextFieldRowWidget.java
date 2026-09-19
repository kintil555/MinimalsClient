package com.minimals.client.ui;

import com.minimals.client.module.setting.StringSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;

/**
 * Single-line text field bound to a {@link StringSetting}. Click to focus and type;
 * Backspace deletes, Enter or Escape leaves the field. Leaving it empty means "not set".
 */
public class TextFieldRowWidget extends Button {

    private final StringSetting setting;

    public TextFieldRowWidget(int x, int y, int width, int height, StringSetting setting) {
        super(x, y, width, height, Component.literal(setting.getName()), btn -> { }, DEFAULT_NARRATION);
        this.setting = setting;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        // Focus is handled by the screen through mouseClicked -> setFocused.
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        setFocused(true);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!isFocused() || !StringUtil.isAllowedChatCharacter(event.codepoint())) {
            return false;
        }
        if (setting.get().length() >= setting.getMaxLength()) {
            return true;
        }
        setting.set(setting.get() + event.codepointAsString());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused()) {
            return false;
        }
        int key = event.key();
        if (key == InputConstants.KEY_BACKSPACE) {
            String value = setting.get();
            if (!value.isEmpty()) {
                setting.set(value.substring(0, value.length() - 1));
            }
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_ESCAPE) {
            setFocused(false);
            return true;
        }
        // Swallow everything else so typing never triggers other menu shortcuts.
        return true;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int w = getWidth();
        int h = getHeight();
        boolean focused = isFocused();
        if (isHovered() || focused) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + h, 5, UiRenderer.ROW_BG_HOVER);
        }

        int textY = getY() + (h - 8) / 2;
        UiRenderer.text(graphics, setting.getName(), getX() + 10, textY, UiRenderer.TEXT_SECONDARY);

        String shown = setting.get();
        boolean caretOn = focused && (Util.getMillis() / 500) % 2 == 0;
        if (shown.isEmpty() && !focused) {
            shown = "(real name)";
        } else if (caretOn) {
            shown = shown + "_";
        }
        int color = focused ? UiRenderer.ACCENT
                : setting.isEmpty() ? UiRenderer.TEXT_SECONDARY : UiRenderer.TEXT_PRIMARY;
        UiRenderer.text(graphics, shown, getX() + w - UiRenderer.textWidth(shown) - 10, textY, color);
    }
}
