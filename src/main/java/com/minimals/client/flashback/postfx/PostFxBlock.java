package com.minimals.client.flashback.postfx;

import net.minecraft.core.BlockPos;

/**
 * One block the effect is anchored to. radius is in blocks: the effect fades from full strength at
 * the block centre to nothing at radius, measured in world space and then projected to the screen.
 */
public record PostFxBlock(int x, int y, int z, float radius) {

    public static final float MIN_RADIUS = 0.5f;
    public static final float MAX_RADIUS = 64.0f;
    public static final float DEFAULT_RADIUS = 4.0f;

    public PostFxBlock {
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    public static PostFxBlock of(BlockPos pos, float radius) {
        return new PostFxBlock(pos.getX(), pos.getY(), pos.getZ(), radius);
    }

    public PostFxBlock withRadius(float newRadius) {
        return new PostFxBlock(x, y, z, newRadius);
    }

    public boolean samePos(PostFxBlock other) {
        return x == other.x && y == other.y && z == other.z;
    }
}
