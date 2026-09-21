package com.minimals.client.flashback.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

import java.util.List;
import java.util.Set;

/**
 * Applies the Flashback mixins only when Flashback is installed. Without this, a player running
 * MinimalsClient alone would crash on the first mixin that targets com.moulberry.flashback.*.
 */
public final class FlashbackMixinPlugin implements IMixinConfigPlugin {

    private boolean flashbackPresent;

    @Override
    public void onLoad(String mixinPackage) {
        flashbackPresent = FabricLoader.getInstance().isModLoaded("flashback");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return flashbackPresent;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

}
