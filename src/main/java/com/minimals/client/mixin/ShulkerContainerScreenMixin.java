package com.minimals.client.mixin;

import com.minimals.client.shulker.ShulkerTooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shulker Preview, held-item side. While the cursor carries an item, vanilla only shows a slot's
 * tooltip when its image component says showTooltipWithItemInHand() is true. It finds that out by
 * running the image through ClientTooltipComponent.create(...) - via a method reference, which
 * cannot be wrapped - and create() would throw on a ShulkerTooltip.
 *
 * So for a shulker preview this answers the question directly (true: the preview is useful while
 * moving items into the box) and never lets the method reach create().
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ShulkerContainerScreenMixin {

    @Inject(method = "showTooltipWithItemInHand", at = @At("HEAD"), cancellable = true)
    private void minimals$showShulkerWithItemInHand(ItemStack item, CallbackInfoReturnable<Boolean> cir) {
        if (item.getTooltipImage().orElse(null) instanceof ShulkerTooltip) {
            cir.setReturnValue(true);
        }
    }
}
