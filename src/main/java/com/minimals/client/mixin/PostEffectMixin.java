package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.PostEffectModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;

import java.util.HashSet;
import java.util.Set;

/**
 * Post Effect module. GameRenderer.render() processes vanilla's single spectator post effect right
 * after LevelRenderer.doEntityOutline() (verified in the 26.2 jar: getfield postEffectId, ifnull,
 * getfield effectActive, ShaderManager.getPostChain(id, MAIN_TARGETS), PostChain.process(
 * mainRenderTarget, resourcePool)). We run our list the same way, straight after
 * doEntityOutline(), so it lands on the same world frame and before the GUI is drawn.
 *
 * A chain that failed to load is remembered and skipped: ShaderManager.getPostChain returns null
 * for it and logs an error once, but we must not ask every frame or the log would flood.
 */
@Mixin(GameRenderer.class)
public abstract class PostEffectMixin {

    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private RenderTarget mainRenderTarget;
    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    /** Ids that returned no chain (bad id / broken json); cleared when the module is toggled. */
    @Unique
    private final Set<Identifier> minimals$failed = new HashSet<>();
    @Unique
    private boolean minimals$wasEnabled;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
    private void minimals$applyPostEffects(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        boolean enabled = ModuleManager.isEnabled("Post Effect");
        if (enabled != minimals$wasEnabled) {
            minimals$wasEnabled = enabled;
            minimals$failed.clear();
        }
        if (!enabled) {
            return;
        }
        PostEffectModule module = ModuleManager.postEffect();
        for (Identifier id : module.activeEffects()) {
            if (minimals$failed.contains(id)) {
                continue;
            }
            PostChain chain = minecraft.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
            if (chain == null) {
                minimals$failed.add(id);
                continue;
            }
            chain.process(mainRenderTarget, resourcePool);
        }
    }
}
