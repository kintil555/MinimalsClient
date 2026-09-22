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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A local library of skins the player has uploaded or fetched: each entry is the skin's own PNG
 * (copied in once) plus a small metadata record, so it can be re-applied later without picking
 * the file again or re-fetching the same username. Entirely local - this never talks to Mojang,
 * it just remembers PNGs {@link MojangSkinService} has already handled once.
 */
public final class SkinLibrary {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One saved skin: a display name, the model it was saved with, and where its PNG lives. */
    public record Entry(String id, String name, String modelApiName, String pngFileName) {
        public MojangSkinService.Model model() {
            return "slim".equals(modelApiName) ? MojangSkinService.Model.SLIM : MojangSkinService.Model.CLASSIC;
        }
    }

    private record Index(List<Entry> entries) {
    }

    private SkinLibrary() {
    }

    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("minimals").resolve("dressing").resolve("skins");
    }

    private static Path indexFile() {
        return directory().resolve("index.json");
    }

    public static List<Entry> list() {
        Path file = indexFile();
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Index index = GSON.fromJson(reader, Index.class);
            return index == null || index.entries() == null ? new ArrayList<>() : new ArrayList<>(index.entries());
        } catch (IOException | JsonSyntaxException e) {
            MinimalClientMod.LOGGER.warn("Skin library: failed to read index", e);
            return new ArrayList<>();
        }
    }

    private static void writeIndex(List<Entry> entries) {
        try {
            Path dir = directory();
            Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(indexFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(new Index(entries), writer);
            }
        } catch (IOException e) {
            MinimalClientMod.LOGGER.warn("Skin library: failed to write index", e);
        }
    }

    /**
     * Copies {@code pngFile} into the library under a new id and adds it to the index. Returns
     * the new entry, or null if the copy failed.
     */
    public static Entry saveFromFile(Path pngFile, String name, MojangSkinService.Model model) {
        try {
            Path dir = directory();
            Files.createDirectories(dir);
            String id = UUID.randomUUID().toString();
            String fileName = id + ".png";
            Files.copy(pngFile, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            Entry entry = new Entry(id, name, model.apiName, fileName);
            List<Entry> entries = list();
            entries.add(entry);
            writeIndex(entries);
            return entry;
        } catch (IOException e) {
            MinimalClientMod.LOGGER.warn("Skin library: failed to save skin", e);
            return null;
        }
    }

    /** Downloads a fetched skin's texture and saves it into the library, same as saveFromFile. */
    public static java.util.concurrent.CompletableFuture<Entry> saveFromUrl(String textureUrl, String name,
                                                                              MojangSkinService.Model model) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                var client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(10)).build();
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(textureUrl))
                        .timeout(java.time.Duration.ofSeconds(15)).GET().build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() / 100 != 2) {
                    return null;
                }
                Path dir = directory();
                Files.createDirectories(dir);
                String id = UUID.randomUUID().toString();
                String fileName = id + ".png";
                Files.write(dir.resolve(fileName), response.body());
                Entry entry = new Entry(id, name, model.apiName, fileName);
                List<Entry> entries = list();
                entries.add(entry);
                writeIndex(entries);
                return entry;
            } catch (Exception e) {
                MinimalClientMod.LOGGER.warn("Skin library: failed to save fetched skin", e);
                return null;
            }
        });
    }

    public static Path pngPath(Entry entry) {
        return directory().resolve(entry.pngFileName());
    }

    public static void remove(String id) {
        List<Entry> entries = list();
        entries.removeIf(e -> {
            if (e.id().equals(id)) {
                try {
                    Files.deleteIfExists(pngPath(e));
                } catch (IOException ex) {
                    MinimalClientMod.LOGGER.warn("Skin library: failed to delete skin file", ex);
                }
                return true;
            }
            return false;
        });
        writeIndex(entries);
    }

    public static Entry byId(String id) {
        return list().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }
}
