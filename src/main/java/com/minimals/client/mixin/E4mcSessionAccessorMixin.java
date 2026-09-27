package com.minimals.client.mixin;

import com.minimals.client.worldhost.E4mcDomainHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * e4mc's QuiclimeSession only prints the assigned domain to chat; it has no public
 * getter/callback. The assignment happens inside an anonymous
 * SimpleChannelInboundHandler#channelRead0 nested in start(), right before
 * "LOGGER.info(\"Domain assigned: {}\", domain)" — we anchor there and capture the
 * local `domain` variable, since anonymous inner classes can't be targeted directly.
 *
 * IMPORTANT: this couples us to e4mc 6.2.2's internal layout (anonymous class name +
 * line-level anchor via the LOGGER.info call). Verified against the actual shipped jar
 * (e4mc-fabric-6_2_2-modern.jar) via javap/decompilation — the handler is
 * QuiclimeSession$2$1 (extends SimpleChannelInboundHandler, has both the bridge and the
 * generic channelRead0 overloads; we target the generic one taking ControlMessage).
 * javac's anonymous-class numbering doesn't map 1:1 to source nesting, so if e4mc updates,
 * re-decompile the new jar and re-check this name/target before re-guessing it.
 */
@Mixin(targets = "link.e4mc.QuiclimeSession$2$1")
public abstract class E4mcSessionAccessorMixin {

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Llink/e4mc/QuiclimeSession$ControlMessageCodec$ControlMessage;)V",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V"),
            locals = LocalCapture.CAPTURE_FAILEXCEPTION
    )
    private void minimals$captureDomain(Object ctx, Object msg, CallbackInfo ci, String domain) {
        E4mcDomainHolder.set(domain);
    }
}


