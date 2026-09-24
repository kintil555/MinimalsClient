package com.minimals.client.dressing;

import com.minimals.client.MinimalClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Live skin/cape override for the local player. The game's own skin only updates from the signed
 * profile property received at login/join, so after an upload it would stay stale until reconnect.
 * After a successful change we read the account's real current textures from Mojang, register them
 * and hand the result to PlayerInfoMixin, which applies it to the local player at once.
 */
public final class LocalSkinOverride {

    private static volatile PlayerSkin.Patch patch;

    private LocalSkinOverride() {
    }

    public static PlayerSkin.Patch get() {
        return patch;
    }

    /** Fetches the account's current skin+cape and applies them in-game immediately. */
    public static CompletableFuture<Void> refreshFromAccount() {
        return MojangSkinService.fetchOwnAppearance().thenCompose(app -> {
            if (app == null) {
                return CompletableFuture.completedFuture(null);
            }
            PlayerModelType model = app.model() == MojangSkinService.Model.SLIM
                    ? PlayerModelType.SLIM : PlayerModelType.WIDE;
            CompletableFuture<Optional<ClientAsset.ResourceTexture>> skin = app.skinUrl() == null
                    ? CompletableFuture.completedFuture(Optional.empty())
                    : PreviewTextureLoader.register("skin", app.skinUrl(), true).thenApply(Optional::of);
            CompletableFuture<Optional<ClientAsset.ResourceTexture>> cape = app.capeUrl() == null
                    ? CompletableFuture.completedFuture(Optional.empty())
                    : PreviewTextureLoader.register("cape", app.capeUrl(), false).thenApply(Optional::of);
            return skin.thenCombine(cape, (s, c) -> {
                // A Patch cannot express "no cape", so apply() nulls it when the account has none.
                capeRemoved = c.isEmpty();
                patch = PlayerSkin.Patch.create(s, c, Optional.empty(), Optional.of(model));
                return (Void) null;
            });
        }).exceptionally(e -> {
            MinimalClientMod.LOGGER.warn("Live skin refresh failed", e);
            return null;
        });
    }

    private static volatile boolean capeRemoved;

    /** Applies the override on top of the game's own skin for the local player. */
    public static PlayerSkin apply(PlayerSkin base) {
        PlayerSkin.Patch p = patch;
        if (p == null) {
            return base;
        }
        PlayerSkin out = base.with(p);
        if (capeRemoved) {
            out = new PlayerSkin(out.body(), null, out.elytra(), out.model(), out.secure());
        }
        return out;
    }

    public static boolean isLocal(java.util.UUID id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(id);
    }
}
