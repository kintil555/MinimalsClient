package com.minimals.client.replay;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Where replays live: {@code <game dir>/config/minimals/replays/}. */
public final class ReplayStorage {

    public static final String EXTENSION = ".mreplay";

    private ReplayStorage() {
    }

    public static Path dir() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("minimals").resolve("replays");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            // Surfaces later as a write failure with a proper message.
        }
        return dir;
    }

    /** Scratch file for the always-on buffer of the current session. */
    public static Path bufferFile() {
        return dir().resolve(".buffer.tmp");
    }
}
