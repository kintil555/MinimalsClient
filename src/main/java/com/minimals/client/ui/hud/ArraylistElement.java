package com.minimals.client.ui.hud;

import com.minimals.client.ClientSettings;
import com.minimals.client.MinimalClientMod;
import com.minimals.client.module.Module;
import com.minimals.client.module.ModuleManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The list of active module names in the corner. Same look as before the HUD editor existed;
 * only the position is now configurable.
 */
public class ArraylistElement extends HudElement {

    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 10;

    private int lastWidth = 60;
    private int lastHeight = LINE_HEIGHT + PADDING * 2 - 2;

    public ArraylistElement() {
        super("arraylist", "Arraylist", 0.01f, 0.01f);
    }

    /** Reused every frame: the old stream().toList() built a new list up to 3x per frame. */
    private final List<Module> scratch = new ArrayList<>();

    private List<Module> activeModules() {
        scratch.clear();
        for (Module module : ModuleManager.getAllModules()) {
            if (module.isEnabled()) {
                scratch.add(module);
            }
        }
        return scratch;
    }

    @Override
    public boolean isActive() {
        if (!ClientSettings.ARRAYLIST.get()) {
            return false;
        }
        for (Module module : ModuleManager.getAllModules()) {
            if (module.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getWidth() {
        return lastWidth;
    }

    @Override
    public int getHeight() {
        return lastHeight;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int x, int y) {
        Font font = Minecraft.getInstance().font;
        List<Module> active = activeModules();
        if (active.isEmpty()) {
            return;
        }

        int maxWidth = 0;
        for (Module module : active) {
            maxWidth = Math.max(maxWidth, font.width(module.getName()));
        }

        int boxW = maxWidth + PADDING * 2;
        int boxH = active.size() * LINE_HEIGHT + PADDING * 2 - 2;
        lastWidth = boxW;
        lastHeight = boxH;

        MinimalClientMod.drawRoundedBox(graphics, x, y, x + boxW, y + boxH, 6, 0x99000000);

        int textY = y + PADDING;
        for (Module module : active) {
            graphics.text(font, module.getName(), x + PADDING, textY, ClientSettings.ARRAYLIST_COLOR.get(), true);
            textY += LINE_HEIGHT;
        }
    }
}
