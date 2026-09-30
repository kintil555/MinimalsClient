package com.minimals.client.mixin;

import com.minimals.client.worldhost.E4mcDomainHolder;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * e4mc has no public getter/callback for the domain its relay assigned. The only place it
 * hands the domain to a public static method is the "Domain assigned" chat message, which
 * builds a click-to-copy event with {@code Mirror.copyToClipboard(domain)} (its only caller,
 * verified in e4mc 6.2.2's source). We hook that call instead of the anonymous network
 * handler: its parameter types are private nested classes that a mixin can't name.
 * require = 0: if a different e4mc version changes this, the join just uses the relay fallback.
 */
@Mixin(targets = "link.e4mc.Mirror")
public abstract class E4mcSessionAccessorMixin {

    @Inject(method = "copyToClipboard(Ljava/lang/String;)Lnet/minecraft/network/chat/ClickEvent;",
            at = @At("HEAD"), require = 0)
    private static void minimals$captureDomain(String value, CallbackInfoReturnable<ClickEvent> cir) {
        E4mcDomainHolder.set(value);
    }
}
