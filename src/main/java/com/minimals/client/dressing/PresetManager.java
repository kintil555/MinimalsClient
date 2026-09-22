package com.minimals.client.dressing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.minimals.client.MinimalClientMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A named bundle of "look" choices - which library skin, its model, which unlocked cape, and
 * which local glow mask - saved together so all four can be re-applied in one click instead of
 * separately. Presets only reference a {@link SkinLibrary} entry and a mask key by id/name; they
 * hold no image data themselves.
 */
public final class PresetManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * @param skinId     id of the {@link SkinLibrary.Entry} to apply, or null to leave the skin
     *                   unchanged
     * @param modelApiName "classic" or "slim"
     * @param capeId     cape id to activate, or null for no cape
     * @param maskKey    skinKey the glow mask was saved under (see SkinEmissionMask), or null
     */
    public record Preset(String id, String name, String skinId, String modelApiName, String capeId,
                          String maskKey) {
        public MojangSkinService.Model model() {
            return "slim".equals(modelApiName) ? MojangSkinService.Model.SLIM : MojangSkinService.Model.CLASSIC;
        }
    }

    private record Index(List<Preset> presets) {
    }

    private PresetManager() {
    }

    private static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("minimals").resolve("dressing");
    }

    private static Path indexFile() {
        return directory().resolve("presets.json");
    }

    public static List<Preset> list() {
        Path file = indexFile();
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Index index = GSON.fromJson(reader, Index.class);
            return index == null || index.presets() == null ? new ArrayList<>() : new ArrayList<>(index.presets());
        } catch (IOException | JsonSyntaxException e) {
            MinimalClientMod.LOGGER.warn("Presets: failed to read index", e);
            return new ArrayList<>();
        }
    }

    private static void writeIndex(List<Preset> presets) {
        try {
            Files.createDirectories(directory());
            try (Writer writer = Files.newBufferedWriter(indexFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(new Index(presets), writer);
            }
        } catch (IOException e) {
            MinimalClientMod.LOGGER.warn("Presets: failed to write index", e);
        }
    }

    public static Preset save(String name, String skinId, MojangSkinService.Model model, String capeId,
                               String maskKey) {
        Preset preset = new Preset(UUID.randomUUID().toString(), name, skinId, model.apiName, capeId, maskKey);
        List<Preset> presets = list();
        presets.add(preset);
        writeIndex(presets);
        return preset;
    }

    public static void remove(String id) {
        List<Preset> presets = list();
        presets.removeIf(p -> p.id().equals(id));
        writeIndex(presets);
    }
}
