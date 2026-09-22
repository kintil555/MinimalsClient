package com.minimals.client.dressingroom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Central state for the Advanced Dressing Room.
 *
 * Live skin/cape switching works by registering a client-local {@link DynamicTexture} under a
 * synthetic {@link Identifier} and wrapping it in a {@link ClientAsset.ResourceTexture}, then
 * exposing the resulting {@link PlayerSkin} through {@link #activeSkinSupplier()}. A mixin
 * ({@link com.minimals.client.dressingroom.mixin.PlayerInfoSkinMixin}) redirects the local
 * player's {@code PlayerInfo.getSkin()} to that supplier whenever an outfit is equipped, so the
 * change is visible instantly with no reconnect.
 */
public final class DressingRoomManager {
    private static final DressingRoomManager INSTANCE = new DressingRoomManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Outfit> outfits = new ArrayList<>();
    private Outfit equipped; // null = use real Mojang skin
    private PlayerSkin cachedActiveSkin;

    private DressingRoomManager() {}

    public static DressingRoomManager get() { return INSTANCE; }

    // ---------------------------------------------------------------- outfits

    public List<Outfit> outfits() { return outfits; }

    public Outfit createOutfit(String name, String skinPath, String capePath, PlayerModelType model) {
        Outfit o = new Outfit(name, skinPath, capePath, model);
        outfits.add(o);
        save();
        return o;
    }

    public void deleteOutfit(UUID id) {
        outfits.removeIf(o -> o.id().equals(id));
        if (equipped != null && equipped.id().equals(id)) {
            unequip();
        }
        save();
    }

    public Optional<Outfit> equipped() { return Optional.ofNullable(equipped); }

    public void equip(Outfit outfit) {
        this.equipped = outfit;
        rebuildActiveSkin(outfit);
    }

    public void unequip() {
        this.equipped = null;
        this.cachedActiveSkin = null;
    }

    public boolean isEquipped(Outfit outfit) {
        return equipped != null && equipped.id().equals(outfit.id());
    }

    // ---------------------------------------------------------------- live skin resolution

    /**
     * Returns the skin that should be shown for the local player right now: either the active
     * outfit's composed skin, or empty if no outfit is equipped (real skin should be used).
     */
    public Optional<PlayerSkin> activeSkin() {
        return Optional.ofNullable(cachedActiveSkin);
    }

    private void rebuildActiveSkin(Outfit outfit) {
        Minecraft mc = Minecraft.getInstance();
        PlayerSkin real = mc.getPlayer() != null
            ? mc.getConnection().getPlayerInfo(mc.getPlayer().getUUID()).getSkin()
            : null;

        ClientAsset.Texture body = outfit.skinPath() != null
            ? loadDynamic("dressingroom_skin_" + outfit.id(), outfit.skinPath())
            : (real != null ? real.body() : null);

        ClientAsset.Texture cape = outfit.capePath() != null
            ? loadDynamic("dressingroom_cape_" + outfit.id(), outfit.capePath())
            : (real != null ? real.cape() : null);

        PlayerModelType model = outfit.modelType() != null ? outfit.modelType()
            : (real != null ? real.model() : PlayerModelType.WIDE);

        if (body == null) {
            // No real skin resolved yet (e.g. called too early) — bail, keep old cache.
            return;
        }

        this.cachedActiveSkin = new PlayerSkin(
            body,
            cape,
            real != null ? real.elytra() : null,
            model,
            false // locally composed textures are never "secure"
        );
    }

    private final java.util.Map<String, ClientAsset.ResourceTexture> textureCache = new java.util.HashMap<>();

    /** Loads a local PNG file as a dynamic texture (once) and wraps it as a ClientAsset.Texture. */
    private ClientAsset.Texture loadDynamic(String key, String filePath) {
        ClientAsset.ResourceTexture cached = textureCache.get(key);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();
        Identifier id = Identifier.fromNamespaceAndPath("minimals", "dressingroom/" + key);
        try (NativeImage image = NativeImage.read(Files.newInputStream(Path.of(filePath)))) {
            DynamicTexture texture = new DynamicTexture(key, image);
            mc.getTextureManager().register(id, texture);
        } catch (IOException e) {
            return null;
        }
        ClientAsset.ResourceTexture wrapped = new ClientAsset.ResourceTexture(id, id);
        textureCache.put(key, wrapped);
        return wrapped;
    }

    /** Call when an outfit's skin/cape file path changes, to force a texture reload. */
    public void invalidateTextureCache(Outfit outfit) {
        textureCache.remove("dressingroom_skin_" + outfit.id());
        textureCache.remove("dressingroom_cape_" + outfit.id());
        if (equipped != null && equipped.id().equals(outfit.id())) rebuildActiveSkin(outfit);
    }

    /** Call whenever the equipped outfit or the underlying real skin changes (e.g. respawn/login). */
    public void refreshIfEquipped() {
        if (equipped != null) rebuildActiveSkin(equipped);
    }

    /**
     * Composes (but does not equip) the skin an outfit would produce, for card-grid live
     * previews of outfits that are not currently worn. Textures are cached by outfit id via the
     * texture manager, so repeated calls each frame are cheap (no re-decode).
     */
    public Optional<PlayerSkin> composePreview(Outfit outfit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getPlayer() == null || mc.getConnection() == null) return Optional.empty();
        PlayerSkin real = mc.getConnection().getPlayerInfo(mc.getPlayer().getUUID()).getSkin();
        if (real == null) return Optional.empty();

        ClientAsset.Texture body = outfit.skinPath() != null
            ? loadDynamic("dressingroom_skin_" + outfit.id(), outfit.skinPath())
            : real.body();
        ClientAsset.Texture cape = outfit.capePath() != null
            ? loadDynamic("dressingroom_cape_" + outfit.id(), outfit.capePath())
            : real.cape();
        PlayerModelType model = outfit.modelType() != null ? outfit.modelType() : real.model();

        if (body == null) return Optional.empty();
        return Optional.of(new PlayerSkin(body, cape, real.elytra(), model, false));
    }

    public Supplier<PlayerSkin> activeSkinSupplier() {
        return () -> activeSkin().orElse(null);
    }

    /** Supplier for the sidebar self-preview: shows the equipped outfit, or the real skin. */
    public Supplier<PlayerSkin> activeSkinSupplierOrReal() {
        return () -> activeSkin().orElseGet(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getPlayer() == null || mc.getConnection() == null) return null;
            return mc.getConnection().getPlayerInfo(mc.getPlayer().getUUID()).getSkin();
        });
    }

    // ---------------------------------------------------------------- persistence

    private static Path storeFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("minimals").resolve("dressingroom_outfits.json");
    }

    public void save() {
        try {
            Path file = storeFile();
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(outfits, w);
            }
        } catch (IOException ignored) {
            // Non-fatal: outfits simply won't persist this session.
        }
    }

    public void load() {
        Path file = storeFile();
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Outfit[] loaded = GSON.fromJson(r, Outfit[].class);
            outfits.clear();
            if (loaded != null) outfits.addAll(List.of(loaded));
        } catch (IOException ignored) {
        }
    }
}
