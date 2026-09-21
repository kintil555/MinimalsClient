package com.minimals.client.flashback.mixin;

import com.minimals.client.flashback.postfx.BlockPickMode;
import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets Ctrl+H + left click pick a block instead of Flashback grabbing the mouse for camera
 * movement. handleBasicInputs() runs only while the viewport is hovered and no popup is open.
 */
@Mixin(value = ReplayUI.class, remap = false)
public abstract class PostEffectPickMixin {

    @Inject(method = "handleBasicInputs", at = @At("HEAD"), cancellable = true, remap = false)
    private static void minimals$pickBlock(CallbackInfo ci) {
        if (BlockPickMode.isArmed() && ImGui.isMouseClicked(GLFW.GLFW_MOUSE_BUTTON_LEFT)
                && BlockPickMode.tryPickOnLeftClick()) {
            ci.cancel();
        }
    }
}
