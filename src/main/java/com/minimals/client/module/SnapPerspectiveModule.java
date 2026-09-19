package com.minimals.client.module;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Snap Perspective: while the bound key is held the camera switches to the third-person
 * front view; releasing it restores whatever perspective was active before.
 */
public class SnapPerspectiveModule extends Module {

    private CameraType previous;

    public SnapPerspectiveModule() {
        super("Snap Perspective", Category.VISUALS);
    }

    @Override
    public boolean isHoldKeybind() {
        return true;
    }

    @Override
    protected void onToggle(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (enabled) {
            if (previous == null) {
                previous = mc.options.getCameraType();
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        } else if (previous != null) {
            mc.options.setCameraType(previous);
            previous = null;
        }
    }
}
