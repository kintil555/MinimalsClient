package com.minimals.client.dressing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.file.Path;
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
     *  Files.isRegularFile on it) and reuses it as an on-disk cache on repeat calls. Takes the
     *  same counter value used for the Identifier so id and cache file always pair up 1:1, even
     *  when multiple downloads are in flight at once. */
    private static Path cacheFile(String suffix, int n) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("minimals").resolve("preview-cache")
                .resolve(suffix + "_" + n + ".png");
    }

    /** Downloads+registers a cape texture and returns just its Identifier, for drawing a small
     *  thumbnail on a card (as opposed to {@link #previewCape}, which wraps it in a full-body
     *  preview Patch for the 3D player widget). Each call gets its own unique id/cache file so
     *  concurrent thumbnail loads (multiple cards lazily loading at once) never share or
     *  overwrite one another's registered texture. */
    static java.util.concurrent.CompletableFuture<Identifier> registerCapeThumbnail(String textureUrl) {
        int n = COUNTER.incrementAndGet();
        Identifier id = Identifier.fromNamespaceAndPath("minimals", "preview/cape_thumb_" + n);
        return downloader().downloadAndRegisterSkin(id, cacheFile("cape_thumb", n), textureUrl, false)
                .thenApply(ClientAsset.Texture::texturePath);
    }

    /** Registers {@code textureUrl} under a fresh id and resolves to a Patch swapping just the
     *  cape slot, so callers can do {@code base.with(patch)} for an instant preview skin. */
    static CompletableFuture<PlayerSkin.Patch> previewCape(String textureUrl) {
        int n = COUNTER.incrementAndGet();
        Identifier id = Identifier.fromNamespaceAndPath("minimals", "preview/cape_" + n);
        return downloader().downloadAndRegisterSkin(id, cacheFile("cape", n), textureUrl, false)
                .thenApply(texture -> PlayerSkin.Patch.create(
                        Optional.empty(),
                        Optional.of(new ClientAsset.ResourceTexture(texture.texturePath())),
                        Optional.empty(),
                        Optional.empty()));
    }

    /** Same as {@link #previewCape} but for the body (skin) slot, also carrying the model type
     *  so slim/classic arms preview correctly. */
    static CompletableFuture<PlayerSkin.Patch> previewSkin(String textureUrl, PlayerModelType model) {
        int n = COUNTER.incrementAndGet();
        Identifier id = Identifier.fromNamespaceAndPath("minimals", "preview/skin_" + n);
        return downloader().downloadAndRegisterSkin(id, cacheFile("skin", n), textureUrl, false)
                .thenApply(texture -> PlayerSkin.Patch.create(
                        Optional.of(new ClientAsset.ResourceTexture(texture.texturePath())),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(model)));
    }
}
