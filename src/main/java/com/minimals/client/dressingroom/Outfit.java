package com.minimals.client.dressingroom;

import net.minecraft.world.entity.player.PlayerModelType;

import java.util.UUID;

/**
 * A saved skin+cape combination ("Outfit").
 * skinPath / capePath are absolute local file paths (chosen via file picker) OR null to fall back
 * to the player's real Mojang skin/cape for that slot.
 */
public final class Outfit {
    private final UUID id;
    private String name;
    private String skinPath;   // nullable
    private String capePath;   // nullable
    private PlayerModelType modelType;

    public Outfit(String name, String skinPath, String capePath, PlayerModelType modelType) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.skinPath = skinPath;
        this.capePath = capePath;
        this.modelType = modelType == null ? PlayerModelType.WIDE : modelType;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public String skinPath() { return skinPath; }
    public void setSkinPath(String p) { this.skinPath = p; }
    public String capePath() { return capePath; }
    public void setCapePath(String p) { this.capePath = p; }
    public PlayerModelType modelType() { return modelType; }
    public void setModelType(PlayerModelType m) { this.modelType = m; }
}
