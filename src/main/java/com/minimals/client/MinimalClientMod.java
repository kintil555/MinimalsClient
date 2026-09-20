package com.minimals.client;

import com.minimals.client.module.Module;
import com.minimals.client.module.ModuleManager;
import com.minimals.client.ui.hud.ArraylistElement;
import com.minimals.client.ui.hud.HudEditorScreen;
import com.minimals.client.ui.hud.HudElement;
import com.minimals.client.ui.hud.HudRegistry;
import com.minimals.client.ui.hud.KeystrokeElement;
import com.minimals.client.ui.hud.SpearMomentumElement;
import com.minimals.client.ui.hud.WailaElement;
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
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.HashSet;
import java.util.Set;

public class MinimalClientMod implements ClientModInitializer {

    public static final String MOD_ID = "minimals";

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "category"));

    private static KeyMapping menuKey;
    private static KeyMapping hudToggleKey;
    private static KeyMapping hudEditorKey;
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

        hudEditorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minimals.hud_editor",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_J,
                CATEGORY
        ));

        HudRegistry.register(new ArraylistElement());
        HudRegistry.register(new KeystrokeElement());
        HudRegistry.register(new WailaElement());
        HudRegistry.register(new SpearMomentumElement());

        // Restore the last session's settings (no-op on first run: default.txt does not exist yet).
        ConfigManager.load(ConfigManager.DEFAULT_NAME);

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
            while (hudEditorKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new HudEditorScreen());
                } else if (client.gui.screen() instanceof HudEditorScreen) {
                    client.gui.setScreen((Screen) null);
                }
            }

            pollModuleKeybinds(client);

            ModuleManager.keystrokes().tick();

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
        Screen screen = client.gui.screen();
        for (Module module : ModuleManager.getAllModules()) {
            if (module.isHoldKeybind()) {
                syncHoldModule(client, module, screen);
            }
        }
        if (screen != null) {
            HELD_KEYBINDS.clear();
            return;
        }
        for (Module module : ModuleManager.getAllModules()) {
            if (!module.hasKeyBind() || module.isHoldKeybind()) {
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

    /**
     * Hold-type modules are on exactly while their key is down. With no screen open the key
     * state decides; with the Minimals menu open the state is left alone so the row can be
     * previewed by clicking; any other screen (chat, inventory...) releases it so typing the
     * bound letter cannot trigger it.
     */
    private static void syncHoldModule(Minecraft client, Module module, Screen screen) {
        if (screen instanceof MenuScreen) {
            return;
        }
        boolean want = screen == null && module.hasKeyBind()
                && InputConstants.isKeyDown(client.getWindow(), module.getKeyBind());
        if (module.isEnabled() != want) {
            module.setEnabled(want);
        }
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!hudVisible) return;
        // While the HUD editor is open it draws every element itself (smoothly, following the
        // cursor). Drawing them here too would show a second, laggier copy underneath.
        if (Minecraft.getInstance().gui.screen() instanceof HudEditorScreen) return;
        for (HudElement element : HudRegistry.all()) {
            element.onScreenSize(graphics.guiWidth(), graphics.guiHeight());
            if (element.isActive()) {
                element.render(graphics, deltaTracker, element.getX(graphics.guiWidth()), element.getY(graphics.guiHeight()));
            }
        }
    }

    public static void drawRoundedBox(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
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
