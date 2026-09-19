package com.minimals.client.ui;

import com.minimals.client.MinimalClientMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;

import java.util.function.Consumer;

/**
 * Module search box for the ClickGUI header: magnifier icon, typed text (or a placeholder),
 * blinking caret. Click to focus; Backspace deletes, Enter/Escape leaves the field. Every
 * change is reported through {@code onChange} so the module list can filter live.
 */
public class SearchFieldWidget extends Button {

    private static final Identifier ICON =
            Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID, "textures/gui/search.png");
    private static final int ICON_SIZE = 16;
    private static final int MAX_LENGTH = 24;

    private final Consumer<String> onChange;
    private String query;

    public SearchFieldWidget(int x, int y, int width, int height, String initialQuery, Consumer<String> onChange) {
        super(x, y, width, height, Component.literal("Search"), btn -> { }, DEFAULT_NARRATION);
        this.query = initialQuery;
        this.onChange = onChange;
    }

    public String getQuery() {
        return query;
    }

    private void setQuery(String value) {
        this.query = value;
        onChange.accept(value);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        // Focus is handled through onClick/mouseClicked -> setFocused.
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
        if (query.length() < MAX_LENGTH) {
            setQuery(query + event.codepointAsString());
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused()) {
            return false;
        }
        int key = event.key();
        if (key == InputConstants.KEY_BACKSPACE) {
            if (!query.isEmpty()) {
                setQuery(query.substring(0, query.length() - 1));
            }
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_ESCAPE) {
            setFocused(false);
            return true;
        }
        // Swallow everything else so typing never triggers menu shortcuts.
        return true;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean focused = isFocused();

        UiRenderer.roundedRect(graphics, x, y, x + w, y + h, 5,
                isHovered() || focused ? UiRenderer.HEADER_BTN_BG_HOVER : UiRenderer.HEADER_BTN_BG);
        if (focused) {
            // thin accent underline so the active field is obvious
            graphics.fill(x + 6, y + h - 2, x + w - 6, y + h - 1, UiRenderer.withOpacity(UiRenderer.ACCENT));
        }

        int iconTint = focused ? UiRenderer.ACCENT : UiRenderer.TEXT_SECONDARY;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, x + 5, y + (h - ICON_SIZE) / 2,
                0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, UiRenderer.withOpacity(iconTint));

        int textX = x + 5 + ICON_SIZE + 4;
        int textY = y + (h - 8) / 2;
        boolean caretOn = focused && (Util.getMillis() / 500) % 2 == 0;
        if (query.isEmpty() && !focused) {
            UiRenderer.text(graphics, "Search modules...", textX, textY, UiRenderer.TEXT_SECONDARY);
        } else {
            String shown = caretOn ? query + "_" : query;
            UiRenderer.text(graphics, shown, textX, textY, UiRenderer.TEXT_PRIMARY);
        }
    }
}
