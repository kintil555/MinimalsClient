package com.minimals.client.flashback.postfx;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Projects a block centre to screen pixels (origin top-left) and turns a world-space radius into a
 * pixel radius at that depth. Uses Camera.getViewRotationProjectionMatrix, which maps positions
 * relative to the camera into clip space.
 */
final class PostFxProjector {

    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();

    private PostFxProjector() {
    }

    /**
     * @return {px, py, pixelRadius}, or null when the block is behind the camera / far off screen
     */
    static float[] project(Camera camera, PostFxBlock block, int width, int height) {
        Vec3 cam = camera.position();
        float dx = (float) (block.x() + 0.5 - cam.x);
        float dy = (float) (block.y() + 0.5 - cam.y);
        float dz = (float) (block.z() + 0.5 - cam.z);

        camera.getViewRotationProjectionMatrix(MATRIX);
        TMP.set(dx, dy, dz, 1.0f);
        MATRIX.transform(TMP);
        if (TMP.w <= 0.0f) {
            return null; // behind the camera
        }
        float ndcX = TMP.x / TMP.w;
        float ndcY = TMP.y / TMP.w;
        float px = (ndcX * 0.5f + 0.5f) * width;
        float py = (1.0f - (ndcY * 0.5f + 0.5f)) * height;

        // Radius: project a point one radius to the side of the centre in view space. Using the
        // clip-space w (= view depth for a perspective matrix) gives pixels per world unit.
        float depth = TMP.w;
        float pixelsPerUnit = MATRIX.m11() * 0.5f * height / depth;
        float pixelRadius = block.radius() * Math.abs(pixelsPerUnit);
        if (px < -pixelRadius || px > width + pixelRadius || py < -pixelRadius || py > height + pixelRadius) {
            return null;
        }
        return new float[]{px, py, pixelRadius};
    }
}
