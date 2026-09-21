package com.minimals.client.flashback.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the Flashback mixins only when a compatible Flashback is installed. Without this, a player running
 * MinimalsClient alone would crash on the first mixin that targets com.moulberry.flashback.*.
 */
public final class FlashbackMixinPlugin implements IMixinConfigPlugin {

    private boolean flashbackPresent;

    @Override
    public void onLoad(String mixinPackage) {
        flashbackPresent = com.minimals.client.flashback.FlashbackBridge.isLoaded();
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


    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
