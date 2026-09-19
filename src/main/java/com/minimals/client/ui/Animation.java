package com.minimals.client.ui;

import com.minimals.client.ClientSettings;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Time-based 0..1 progress that eases toward a target. When the Animations setting is off
 * it snaps straight to the target, so callers never need their own branch.
 */
public class Animation {

    private final long durationMs;
    private float from;
    private float to;
    private long startedAt;

    public Animation(float initial, long durationMs) {
        this.from = initial;
        this.to = initial;
        this.durationMs = durationMs;
    }

    /** Current eased value in 0..1. */
    public float get() {
        if (!ClientSettings.ANIMATIONS.get()) {
            return to;
        }
        float t = Mth.clamp((Util.getMillis() - startedAt) / (float) durationMs, 0f, 1f);
        float eased = (float) Mth.smoothstep(t);
        return Mth.lerp(eased, from, to);
    }

    /** Starts moving toward {@code target} from wherever the value is right now. */
    public void setTarget(float target) {
        if (target == to) {
            return;
        }
        this.from = get();
        this.to = target;
        this.startedAt = Util.getMillis();
    }

    public boolean isFinished() {
        return !ClientSettings.ANIMATIONS.get()
                || Util.getMillis() - startedAt >= durationMs;
    }
}
