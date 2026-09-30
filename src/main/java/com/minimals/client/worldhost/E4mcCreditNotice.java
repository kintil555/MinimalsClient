package com.minimals.client.worldhost;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One-time thank-you notice on the title screen: Minimals' multiplayer is built on the e4mc
 * relay (MIT, by Skye). A marker file in the config dir makes it appear only once.
 */
public final class E4mcCreditNotice {
    private static boolean checked;

    private E4mcCreditNotice() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (checked) return;
            Screen screen = client.screen;
            if (!(screen instanceof TitleScreen)) return;
            checked = true;

            Path marker = FabricLoader.getInstance().getConfigDir().resolve("minimals_e4mc_credit_shown");
            if (Files.exists(marker)) return;
            try {
                Files.createFile(marker);
            } catch (IOException ignored) {
                // Can't persist the flag: still show it this launch.
            }
            client.setScreen(new AlertScreen(
                    () -> client.setScreen(new TitleScreen()),
                    Component.literal("Thanks to e4mc"),
                    Component.literal("Multiplayer in Minimals is powered by the e4mc relay, built by Skye "
                            + "(MIT licence). It is bundled inside this client, so you don't need to install "
                            + "e4mc separately, and it won't clash if you already have it. "
                            + "Huge thanks to the e4mc team for making this client possible. e4mc.link")
            ));
        });
    }
}
