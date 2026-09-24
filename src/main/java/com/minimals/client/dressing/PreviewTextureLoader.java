package com.minimals.client.dressing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads a remote skin/cape PNG and registers it as a live texture so a Card preview (or the
 * 3D player widget) can show it immediately, WITHOUT touching the account - this is purely a
 * client-side "what would this look like" render. Backed by the same
 * {@link SkinTextureDownloader} vanilla uses for other players' skins/capes, so it benefits from
 * the same caching/registration path (once downloaded, re-selecting the same option is instant).
 */
final class PreviewTextureLoader {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static SkinTextureDownloader downloader;

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

    /** Downloads+registers a cape texture and returns just its Identifier, for drawing a small
     *  thumbnail on a card (as opposed to {@link #previewCape}, which wraps it in a full-body
     *  preview Patch for the 3D player widget). Each call gets its own unique id/cache file so
     *  concurrent thumbnail loads (multiple cards lazily loading at once) never share or
     *  overwrite one another's registered texture. */
    static java.util.concurrent.CompletableFuture<Identifier> registerCapeThumbnail(String textureUrl) {
        int n = COUNTER.incrementAndGet();
        Identifier id = Identifier.fromNamespaceAndPath("minimals", "preview/cape_thumb_" + n);
        return downloader().downloadAndRegisterSkin(id, cacheFile("cape_thumb", textureUrl), textureUrl, false)
                .thenApply(ClientAsset.Texture::texturePath);
    }

    /** Registers {@code textureUrl} under a fresh id and resolves to a Patch swapping just the
     *  cape slot, so callers can do {@code base.with(patch)} for an instant preview skin. */
    static CompletableFuture<PlayerSkin.Patch> previewCape(String textureUrl) {
        int n = COUNTER.incrementAndGet();
        Identifier id = Identifier.fromNamespaceAndPath("minimals", "preview/cape_" + n);
        return downloader().downloadAndRegisterSkin(id, cacheFile("cape", textureUrl), textureUrl, false)
                .thenApply(texture -> PlayerSkin.Patch.create(
                        Optional.empty(),
                        // Two-arg ResourceTexture(id, texturePath): texturePath must point at the
                        // exact Identifier already registered in the TextureManager by
                        // downloadAndRegisterSkin. The one-arg ResourceTexture(Identifier)
                        // constructor instead REWRITES the path to "textures/<path>.png" and
                        // looks it up as a resource-pack asset, which doesn't exist for a
                        // dynamically downloaded texture - that mismatch is what produced the
                        // missing-texture (magenta/black) cape.
                        Optional.of(new ClientAsset.ResourceTexture(texture.texturePath(), texture.texturePath())),
                        Optional.empty(),
                        Optional.empty()));
    }

    /** Same as {@link #previewCape} but for the body (skin) slot, also carrying the model type
     *  so slim/classic arms preview correctly. */
    static CompletableFuture<PlayerSkin.Patch> previewSkin(String textureUrl, PlayerModelType model) {
        int n = COUNTER.incrementAndGet();
        Identifier id = Identifier.fromNamespaceAndPath("minimals", "preview/skin_" + n);
        return downloader().downloadAndRegisterSkin(id, cacheFile("skin", textureUrl), textureUrl, false)
                .thenApply(texture -> PlayerSkin.Patch.create(
                        Optional.of(new ClientAsset.ResourceTexture(texture.texturePath(), texture.texturePath())),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(model)));
    }

    /** Downloads+registers a texture under a fresh id and returns it as a ResourceTexture usable in a
     *  PlayerSkin.Patch. The cache file is keyed by URL hash, so a changed skin (new URL) never
     *  reuses a stale file. */
    static CompletableFuture<ClientAsset.ResourceTexture> register(String kind, String textureUrl, boolean skin) {
        int n = COUNTER.incrementAndGet();
        Identifier id = Identifier.fromNamespaceAndPath("minimals", "live/" + kind + "_" + n);
        return downloader().downloadAndRegisterSkin(id, cacheFile("live_" + kind, textureUrl), textureUrl, skin)
                .thenApply(t -> new ClientAsset.ResourceTexture(t.texturePath(), t.texturePath()));
    }
}
