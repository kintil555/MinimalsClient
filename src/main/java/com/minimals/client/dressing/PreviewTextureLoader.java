package com.minimals.client.dressing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Downloads a remote skin/cape PNG and registers it as a live texture so a Card preview (or the
 * 3D player widget) can show it immediately, WITHOUT touching the account - this is purely a
 * client-side "what would this look like" render. Backed by the same
 * {@link SkinTextureDownloader} vanilla uses for other players' skins/capes, so it benefits from
 * the same caching/registration path (once downloaded, re-selecting the same option is instant).
 */
public final class PreviewTextureLoader {

    /** Disk cache limits. Enforced once per launch by {@link #trimDiskCache()}. */
    private static final int MAX_CACHE_FILES = 150;
    private static final long MAX_CACHE_BYTES = 32L * 1024 * 1024;

    private static SkinTextureDownloader downloader;

    /** One in-flight/finished download per (kind, url). Identical requests share the same future,
     *  so a cape shown in the list, the preview and the thumbnail is downloaded and registered
     *  once instead of once per call. Ids are derived from the URL hash, so they are stable. */
    private static final Map<String, CompletableFuture<ClientAsset.Texture>> TEXTURES = new ConcurrentHashMap<>();
    /** Ids registered for throw-away previews (not the live in-game skin), released on screen close. */
    private static final Map<String, Identifier> PREVIEW_IDS = new ConcurrentHashMap<>();

    private PreviewTextureLoader() {
    }

    private static SkinTextureDownloader downloader() {
        if (downloader == null) {
            Minecraft mc = Minecraft.getInstance();
            downloader = new SkinTextureDownloader(mc.getProxy(), mc.getTextureManager(), mc::execute);
        }
        return downloader;
    }

    /** Per-texture scratch cache file: downloadAndRegisterSkin requires a real path (it checks
     *  Files.isRegularFile on it) and reuses it as an on-disk cache on repeat calls. Keyed by a
     *  hash of the actual texture URL (not a runtime counter) so the cache file always matches
     *  the content it holds - a counter-based name collides with leftover files from a previous
     *  game session (counter resets to 1 on restart), silently loading a stale, wrong texture
     *  from disk instead of downloading the requested one. */
    private static Path cacheFile(String suffix, String textureUrl) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("minimals").resolve("preview-cache")
                .resolve(suffix + "_" + sha1Hex(textureUrl) + ".png");
    }

    private static String sha1Hex(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Returns the texture for {@code (kind, url)}, downloading it at most once. A failed download
     *  is dropped from the map so the next request retries instead of caching the failure. */
    private static CompletableFuture<ClientAsset.Texture> texture(String namespacePrefix, String kind,
                                                                  String textureUrl, boolean skin,
                                                                  boolean preview) {
        String key = namespacePrefix + kind + "|" + textureUrl;
        return TEXTURES.computeIfAbsent(key, k -> {
            Identifier id = Identifier.fromNamespaceAndPath("minimals",
                    namespacePrefix + kind + "_" + sha1Hex(textureUrl));
            if (preview) {
                PREVIEW_IDS.put(k, id);
            }
            CompletableFuture<ClientAsset.Texture> f =
                    downloader().downloadAndRegisterSkin(id, cacheFile(kind, textureUrl), textureUrl, skin);
            f.whenComplete((t, err) -> {
                if (err != null) {
                    TEXTURES.remove(k);
                    PREVIEW_IDS.remove(k);
                }
            });
            return f;
        });
    }

    /** Downloads+registers a cape texture and returns just its Identifier, for drawing a small
     *  thumbnail on a card. Shared per URL: many cards asking for the same cape reuse one texture. */
    static CompletableFuture<Identifier> registerCapeThumbnail(String textureUrl) {
        return texture("preview/", "cape_thumb", textureUrl, false, true)
                .thenApply(ClientAsset.Texture::texturePath);
    }

    /** Resolves to a Patch swapping just the cape slot, so callers can do {@code base.with(patch)}. */
    static CompletableFuture<PlayerSkin.Patch> previewCape(String textureUrl) {
        return texture("preview/", "cape", textureUrl, false, true)
                .thenApply(t -> PlayerSkin.Patch.create(
                        Optional.empty(),
                        // Two-arg ResourceTexture(id, texturePath): texturePath must point at the
                        // exact Identifier already registered in the TextureManager. The one-arg
                        // constructor rewrites it to "textures/<path>.png" (a resource-pack asset
                        // lookup), which is what produced the magenta/black cape before.
                        Optional.of(new ClientAsset.ResourceTexture(t.texturePath(), t.texturePath())),
                        Optional.empty(),
                        Optional.empty()));
    }

    /** Same as {@link #previewCape} but for the body (skin) slot, also carrying the model type. */
    static CompletableFuture<PlayerSkin.Patch> previewSkin(String textureUrl, PlayerModelType model) {
        return texture("preview/", "skin", textureUrl, false, true)
                .thenApply(t -> PlayerSkin.Patch.create(
                        Optional.of(new ClientAsset.ResourceTexture(t.texturePath(), t.texturePath())),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(model)));
    }

    /** Texture for the local player's actual in-game skin/cape. Kept for the whole session (it is
     *  what LocalSkinOverride renders), so it is NOT part of the preview set released on close. */
    static CompletableFuture<ClientAsset.ResourceTexture> register(String kind, String textureUrl, boolean skin) {
        return texture("live/", "live_" + kind, textureUrl, skin, false)
                .thenApply(t -> new ClientAsset.ResourceTexture(t.texturePath(), t.texturePath()));
    }

    /** Frees every preview texture (thumbnails + try-on previews) from the TextureManager and
     *  forgets them, so the dressing room does not leave GPU textures behind after it closes.
     *  The files stay on disk, so reopening is fast. Must run on the render thread. */
    static void releasePreviews() {
        Minecraft mc = Minecraft.getInstance();
        for (Map.Entry<String, Identifier> e : new ArrayList<>(PREVIEW_IDS.entrySet())) {
            mc.getTextureManager().release(e.getValue());
            TEXTURES.remove(e.getKey());
            PREVIEW_IDS.remove(e.getKey());
        }
    }

    /**
     * Keeps the on-disk cache bounded and removes legacy junk. Call once at startup, off the
     * render thread. Deletes (1) files from the old counter-based naming that nothing reads any
     * more (cape_thumb_546.png, cape_548.png, skin_3.png ...), then (2) the least recently used
     * files until both MAX_CACHE_FILES and MAX_CACHE_BYTES hold.
     */
    public static void trimDiskCache() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("minimals").resolve("preview-cache");
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> keep = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String name = f.getFileName().toString();
                // Current names end in a 40-char sha1 hex; anything else is a legacy counter-named file.
                if (!name.matches(".*_[0-9a-f]{40}\\.png")) {
                    Files.deleteIfExists(f);
                } else {
                    keep.add(f);
                }
            }
        } catch (IOException e) {
            return;
        }
        keep.sort(Comparator.comparingLong((Path f) -> {
            try {
                return Files.getLastModifiedTime(f).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }).reversed()); // newest first
        long total = 0;
        int count = 0;
        for (Path f : keep) {
            long size;
            try {
                size = Files.size(f);
            } catch (IOException e) {
                continue;
            }
            count++;
            total += size;
            if (count > MAX_CACHE_FILES || total > MAX_CACHE_BYTES) {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                    // Best effort: a locked file is retried on the next launch.
                }
            }
        }
    }
}
