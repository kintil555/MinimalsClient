package com.minimals.client.flashback;

import com.minimals.client.flashback.postfx.PostEffectKeyframeType;
import com.moulberry.flashback.keyframe.KeyframeRegistry;

/**
 * Registers MinimalsClient's timeline elements with Flashback. Only ever referenced from
 * {@link FlashbackBridge#bootstrap()} after Flashback was confirmed loaded, so this class (and its
 * com.moulberry.flashback imports) is never linked when Flashback is missing.
 */
final class FlashbackBootstrap {

    private FlashbackBootstrap() {
    }

    static void run() {
        // Idempotent per class: KeyframeRegistry ignores a second register of the same type.
        KeyframeRegistry.register(PostEffectKeyframeType.INSTANCE);
    }
}
