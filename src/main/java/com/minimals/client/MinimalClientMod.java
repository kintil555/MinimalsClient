package com.minimals.client;

import com.minimals.client.module.Module;
import com.minimals.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MinimalClientMod implements ClientModInitializer {

    public static final String MOD_ID = "minimals";

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "category"));

    private static KeyMapping menuKey;
    private static KeyMapping hudToggleKey;
    public static boolean hudVisible = true;

    /** Modules whose keybind was already down last tick, so a held key toggles only once. */
    private static final Set<Module> HELD_KEYBINDS = new HashSet<>();

    @Override
    public void onInitializeClient() {
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minimals.open_menu",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_RSHIFT,
                CATEGORY
        ));

        hudToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minimals.toggle_hud",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_H,
                CATEGORY
        ));

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(MOD_ID, "minimals_hud"),
                MinimalClientMod::renderHud
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (MenuScreen.isTypingInMenu()) {
                // Drain queued presses so they don't fire the moment the text field loses focus.
                while (menuKey.consumeClick()) { }
                while (hudToggleKey.consumeClick()) { }
                return;
            }
            while (menuKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new MenuScreen());
                } else if (client.gui.screen() instanceof MenuScreen) {
                    client.gui.setScreen((Screen) null);
                }
            }
            while (hudToggleKey.consumeClick()) {
                hudVisible = !hudVisible;
            }

            pollModuleKeybinds(client);

            // Sprint module: force sprint while moving forward, no double-tap needed.
            if (ModuleManager.isEnabled("Sprint") && client.player != null) {
                if (client.options.keyUp.isDown() && !client.player.isSprinting()
                        && client.player.getFoodData().getFoodLevel() > 6) {
                    client.player.setSprinting(true);
                }
            }
        });
    }

    /**
     * Toggles modules whose user-assigned key was just pressed. Skipped while any screen is
     * open (chat, menu rebinding, inventory) so typing never flips a module by accident.
     */
    private static void pollModuleKeybinds(Minecraft client) {
        if (client.gui.screen() != null) {
            HELD_KEYBINDS.clear();
            return;
        }
        for (Module module : ModuleManager.getAllModules()) {
            if (!module.hasKeyBind()) {
                continue;
            }
            boolean down = InputConstants.isKeyDown(client.getWindow(), module.getKeyBind());
            if (down && HELD_KEYBINDS.add(module)) {
                module.toggle();
            } else if (!down) {
                HELD_KEYBINDS.remove(module);
            }
        }
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!hudVisible) return;
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;

        List<Module> active = ModuleManager.getAllModules().stream()
                .filter(Module::isEnabled)
                .toList();

        if (active.isEmpty()) return;

        int padding = 5;
        int lineHeight = 10;
        int x = 6;
        int y = 6;

        int maxWidth = 0;
        for (Module module : active) {
            maxWidth = Math.max(maxWidth, font.width(module.getName()));
        }

        int boxW = maxWidth + padding * 2;
        int boxH = active.size() * lineHeight + padding * 2 - 2;

        drawRoundedBox(graphics, x, y, x + boxW, y + boxH, 6, 0x99000000);

        int textY = y + padding;
        for (Module module : active) {
            graphics.text(font, module.getName(), x + padding, textY, 0xFFFFFF, true);
            textY += lineHeight;
        }
    }

    static void drawRoundedBox(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        // center
        graphics.fill(x1 + radius, y1, x2 - radius, y2, color);
        graphics.fill(x1, y1 + radius, x2, y2 - radius, color);

        // approximate rounded corners by vertical strips
        for (int i = 0; i < radius; i++) {
            int dx = radius - i;
            int dy = (int) Math.sqrt(radius * radius - dx * dx);
            graphics.fill(x1 + i, y1 + radius - dy, x1 + i + 1, y2 - radius + dy, color);
            graphics.fill(x2 - i - 1, y1 + radius - dy, x2 - i, y2 - radius + dy, color);
        }
    }
}
