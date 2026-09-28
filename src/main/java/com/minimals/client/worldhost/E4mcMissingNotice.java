package com.minimals.client.worldhost;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One-time notice, shown on the title screen, when e4mc is not installed. World Host P2P
 * needs e4mc; without it the feature is unavailable. Uses vanilla AlertScreen (not our
 * custom UI) and a marker file in the config dir so it only ever appears once.
 */
public final class E4mcMissingNotice {
    private static final String E4MC_MOD_ID = "e4mc";
    private static boolean checked;

    private E4mcMissingNotice() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (checked) return;
            Screen screen = client.gui.screen();
            // Wait until the title screen is actually up, then decide once per launch.
            if (!(screen instanceof TitleScreen)) return;
            checked = true;

            if (FabricLoader.getInstance().isModLoaded(E4MC_MOD_ID)) return;
            Path marker = FabricLoader.getInstance().getConfigDir().resolve("minimals_e4mc_notice_shown");
            if (Files.exists(marker)) return;

            try {
                Files.createFile(marker);
            } catch (IOException ignored) {
                // Can't persist the flag: still show it this launch rather than hide the info.
            }
            client.gui.setScreen(new AlertScreen(
                    () -> client.gui.setScreen(new TitleScreen()),
                    Component.literal("e4mc required"),
                    Component.literal("World Host in this mod requires the e4mc mod. "
                            + "Without it, you cannot use that feature.")
            ));
        });
    }
}
