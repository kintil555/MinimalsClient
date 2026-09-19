package com.minimals.client;

import com.minimals.client.module.Module;
import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.setting.Setting;
import com.minimals.client.ui.hud.HudElement;
import com.minimals.client.ui.hud.HudRegistry;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Saves and loads named configs as plain-text files in {@code <game dir>/config/minimals/} (FabricLoader config dir).
 * A config is just one file (e.g. {@code default.txt}), so sharing it means sending that file.
 *
 * Format: one {@code key=value} per line; lines starting with {@code #} are comments.
 * <pre>
 * setting.Opacity=85
 * module.Fullbright.enabled=true
 * module.Fullbright.key=72
 * module.HurtColor.Color=FF8800
 * hud.arraylist.x=0.0100
 * </pre>
 * Unknown keys and malformed values are skipped, so configs from another version, or edited
 * by hand, load as far as they can instead of failing.
 */
public final class ConfigManager {

    public static final String DEFAULT_NAME = "default";
    private static final String EXTENSION = ".txt";

    private static String currentName = DEFAULT_NAME;

    private ConfigManager() {
    }

    public static Path directory() {
        // FabricLoader knows the config dir without needing a Minecraft instance, so this is safe
        // to call from onInitializeClient (Minecraft.getInstance() may not be ready that early).
        return FabricLoader.getInstance().getConfigDir().resolve("minimals");
    }

    public static String currentName() {
        return currentName;
    }

    /** Names (without extension) of every config file in the folder, sorted. */
    public static List<String> list() {
        Path dir = directory();
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return names;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(EXTENSION))
                    .map(n -> n.substring(0, n.length() - EXTENSION.length()))
                    .sorted()
                    .forEach(names::add);
        } catch (IOException e) {
            System.err.println("[Minimals] Could not list configs: " + e);
        }
        return names;
    }

    /**
     * Restricts a user-typed name to safe filename characters so it can never escape the
     * config folder (no slashes, dots or spaces). Falls back to "default" when nothing is left.
     */
    public static String sanitize(String name) {
        String cleaned = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return cleaned.isEmpty() ? DEFAULT_NAME : cleaned;
    }

    /**
     * Writes default.txt (what is auto-loaded on the next start) without changing which config
     * the player last loaded or saved by name, so a named config is only touched by Save.
     */
    public static void autoSave() {
        String previous = currentName;
        save(DEFAULT_NAME);
        currentName = previous;
    }

    public static boolean save(String name) {
        String safe = sanitize(name);
        List<String> lines = new ArrayList<>();
        lines.add("# Minimals config - edit by hand or share this file.");

        for (Setting<?> setting : ClientSettings.ALL) {
            lines.add("setting." + key(setting) + "=" + setting.serialize());
        }

        for (Module module : ModuleManager.getAllModules()) {
            String prefix = "module." + key(module.getName()) + ".";
            lines.add(prefix + "enabled=" + module.isEnabled());
            lines.add(prefix + "key=" + module.getKeyBind());
            for (Setting<?> setting : module.getSettings()) {
                lines.add(prefix + key(setting) + "=" + setting.serialize());
            }
        }

        for (HudElement element : HudRegistry.all()) {
            lines.add("hud." + element.getId() + ".x=" + element.getXFrac());
            lines.add("hud." + element.getId() + ".y=" + element.getYFrac());
        }

        try {
            Files.createDirectories(directory());
            Files.write(directory().resolve(safe + EXTENSION), lines, StandardCharsets.UTF_8);
            currentName = safe;
            return true;
        } catch (IOException e) {
            System.err.println("[Minimals] Could not save config '" + safe + "': " + e);
            return false;
        }
    }

    /** Loads a config by name. Returns false when the file does not exist or cannot be read. */
    public static boolean load(String name) {
        String safe = sanitize(name);
        Path file = directory().resolve(safe + EXTENSION);
        if (!Files.isRegularFile(file)) {
            return false;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Minimals] Could not read config '" + safe + "': " + e);
            return false;
        }

        for (String raw : lines) {
            String line = raw.strip();
            int eq = line.indexOf('=');
            if (line.isEmpty() || line.startsWith("#") || eq <= 0) {
                continue;
            }
            apply(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
        }
        currentName = safe;
        return true;
    }

    private static void apply(String key, String value) {
        if (key.startsWith("setting.")) {
            String id = key.substring("setting.".length());
            for (Setting<?> setting : ClientSettings.ALL) {
                if (key(setting).equals(id)) {
                    setting.deserialize(value);
                    return;
                }
            }
        } else if (key.startsWith("module.")) {
            applyModule(key.substring("module.".length()), value);
        } else if (key.startsWith("hud.")) {
            applyHud(key.substring("hud.".length()), value);
        }
    }

    private static void applyModule(String rest, String value) {
        for (Module module : ModuleManager.getAllModules()) {
            String prefix = key(module.getName()) + ".";
            if (!rest.startsWith(prefix)) {
                continue;
            }
            String field = rest.substring(prefix.length());
            switch (field) {
                case "enabled" -> module.setEnabled(Boolean.parseBoolean(value));
                case "key" -> {
                    try {
                        module.setKeyBind(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        // keep the current bind
                    }
                }
                default -> {
                    for (Setting<?> setting : module.getSettings()) {
                        if (key(setting).equals(field)) {
                            setting.deserialize(value);
                            return;
                        }
                    }
                }
            }
            return;
        }
    }

    private static void applyHud(String rest, String value) {
        int dot = rest.lastIndexOf('.');
        if (dot <= 0) {
            return;
        }
        String id = rest.substring(0, dot);
        String axis = rest.substring(dot + 1);
        for (HudElement element : HudRegistry.all()) {
            if (!element.getId().equals(id)) {
                continue;
            }
            try {
                float v = Float.parseFloat(value);
                if (axis.equals("x")) {
                    element.setXFrac(v);
                } else if (axis.equals("y")) {
                    element.setYFrac(v);
                }
            } catch (NumberFormatException ignored) {
                // keep the current position
            }
            return;
        }
    }

    /** Config key for a name: lower case, spaces removed ("Render Distance" -> "renderdistance"). */
    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static String key(Setting<?> setting) {
        return key(setting.getName());
    }
}
