package com.minimals.client;

import com.minimals.client.module.Module;
import com.minimals.client.module.ModuleManager;
import com.minimals.client.spectate.MenuChoiceScreen;
import com.minimals.client.spectate.SpectateManager;
import com.minimals.client.ui.hud.ArraylistElement;
import com.minimals.client.ui.hud.HudEditorScreen;
import com.minimals.client.ui.hud.HudElement;
import com.minimals.client.ui.hud.HudRegistry;
import com.minimals.client.ui.hud.KeystrokeElement;
import com.minimals.client.ui.hud.SpearMomentumElement;
import com.minimals.client.ui.hud.WailaElement;
import com.minimals.client.waypoint.WaypointCreateScreen;
import com.minimals.client.waypoint.WaypointIcon;
import com.minimals.client.waypoint.WaypointManager;
import com.minimals.client.waypoint.WaypointRenderer;
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
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "category"));

    private static KeyMapping menuKey;
    private static KeyMapping hudToggleKey;
    private static KeyMapping hudEditorKey;
    /** Places a waypoint. Separate from the Waypoints module's own on/off keybind in the ClickGUI. */
    private static KeyMapping addWaypointKey;
    /** Leaves spectate. Own keybind (default Q), so it never shares state with vanilla Drop Item. */
    private static KeyMapping exitSpectateKey;
    public static boolean hudVisible = true;
    /** True while the Sprint module is the one holding the sprint key down. */
    private static boolean sprintHeldByModule;

    /** Modules whose keybind was already down last tick, so a held key toggles only once. */
    private static final Set<Module> HELD_KEYBINDS = new HashSet<>();

    @Override
    public void onInitializeClient() {
        // Registers the menu-open / button-hover SoundEvents (must happen during init).
        com.minimals.client.sound.MinimalsSounds.init();
        com.minimals.client.dressingroom.DressingRoomManager.get().load();

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

        addWaypointKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minimals.add_waypoint",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_B,
                CATEGORY
        ));

        exitSpectateKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minimals.exit_spectate",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_Q,
                CATEGORY
        ));
        SpectateManager.setExitKey(exitSpectateKey);

        HudRegistry.register(new ArraylistElement());
        HudRegistry.register(new KeystrokeElement());
        HudRegistry.register(new WailaElement());
        HudRegistry.register(new SpearMomentumElement());

        // Adds our timeline elements (Post Effect) to Flashback; no-op when Flashback is not installed.
        com.minimals.client.flashback.FlashbackBridge.bootstrap();

        // Restore the last session's settings (no-op on first run: default.txt does not exist yet).
        ConfigManager.load(ConfigManager.DEFAULT_NAME);

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(MOD_ID, "minimals_hud"),
                MinimalClientMod::renderHud
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Sprint module: press the vanilla sprint key while moving forward, no double-tap
            // needed. Going through the key (instead of setSprinting) lets LocalPlayer apply its
            // own rules: sneaking, using an item, riding, shallow water, low food, ...
            // Runs first so the early returns below can never leave the key stuck down.
            tickAutoSprint(client);
            SpectateManager.tick(client);

            if (MenuScreen.isTypingInMenu()) {
                // Drain queued presses so they don't fire the moment the text field loses focus.
                while (menuKey.consumeClick()) { }
                while (hudToggleKey.consumeClick()) { }
                return;
            }
            while (menuKey.consumeClick()) {
                Screen open = client.gui.screen();
                if (open == null) {
                    client.gui.setScreen(new MenuChoiceScreen());
                } else if (open instanceof MenuScreen || open instanceof MenuChoiceScreen
                        || open instanceof com.minimals.client.spectate.SpectateScreen) {
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

            // Keeps the waypoint list matched to the current world/dimension (cheap when unchanged).
            WaypointManager.sync(client);
            while (addWaypointKey.consumeClick()) {
                if (client.gui.screen() == null && client.player != null && client.level != null
                        && ModuleManager.waypoints().isEnabled()) {
                    client.gui.setScreen(new WaypointCreateScreen(null,
                            client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ(),
                            "", WaypointIcon.LOCATE));
                }
            }

            ModuleManager.keystrokes().tick();

        });
    }

    /**
     * Presses the sprint key for the player while Sprint is on and forward is held.
     *
     * Vanilla only changes {@code KeyMapping.isDown} on key events, never by polling, so a key we
     * press stays down until we release it. We therefore release it only when WE pressed it
     * ({@link #sprintHeldByModule}); if the player was already holding their own sprint key we
     * never touch it, so their key keeps working.
     */
    private static void tickAutoSprint(Minecraft client) {
        if (client.player == null) {
            return;
        }
        KeyMapping sprintKey = client.options.keySprint;
        boolean want = ModuleManager.isEnabled("Sprint")
                && !SpectateManager.isSpectating()
                && client.gui.screen() == null
                && client.options.keyUp.isDown();
        if (want) {
            if (!sprintKey.isDown()) {
                sprintKey.setDown(true);
                sprintHeldByModule = true;
            }
        } else if (sprintHeldByModule) {
            sprintKey.setDown(false);
            sprintHeldByModule = false;
        }
        // Opening a screen makes vanilla call KeyMapping.releaseAll(), which already cleared the
        // key. Drop our claim on it then, or a later release would clobber the player's own press.
        if (sprintHeldByModule && !sprintKey.isDown()) {
            sprintHeldByModule = false;
        }
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
        renderSpectateHint(graphics);
        if (!hudVisible) return;
        WaypointRenderer.render(graphics);
        // While the HUD editor is open it draws every element itself (smoothly, following the
        // cursor). Drawing them here too would show a second, laggier copy underneath.
        if (Minecraft.getInstance().gui.screen() instanceof HudEditorScreen) return;
        for (HudElement element : HudRegistry.all()) {
            element.onScreenSize(graphics.guiWidth(), graphics.guiHeight());
            if (element.isActive()) {
                element.renderScaled(graphics, deltaTracker, element.getX(graphics.guiWidth()), element.getY(graphics.guiHeight()));
            }
        }
    }

    /** Small banner while spectating: who, loading state, and how to leave. */
    private static void renderSpectateHint(GuiGraphicsExtractor graphics) {
        if (!SpectateManager.isSpectating()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String name = "player";
        if (mc.level != null) {
            var target = SpectateManager.find(mc.level, SpectateManager.targetId());
            if (target != null) {
                name = target.getName().getString();
            }
        }
        String text = SpectateManager.isLoading()
                ? "Loading chunks for " + name + "..."
                : "Spectating " + name;
        String hint = "Press Q to exit";
        int w = Math.max(com.minimals.client.ui.UiRenderer.textWidth(text),
                com.minimals.client.ui.UiRenderer.textWidth(hint)) + 16;
        int x = (graphics.guiWidth() - w) / 2;
        drawRoundedBox(graphics, x, 8, x + w, 34, 5, 0xB0141417);
        com.minimals.client.ui.UiRenderer.text(graphics, text,
                x + (w - com.minimals.client.ui.UiRenderer.textWidth(text)) / 2, 12, 0xFFE8E8ED);
        com.minimals.client.ui.UiRenderer.text(graphics, hint,
                x + (w - com.minimals.client.ui.UiRenderer.textWidth(hint)) / 2, 23, 0xFF9A9AA5);
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
