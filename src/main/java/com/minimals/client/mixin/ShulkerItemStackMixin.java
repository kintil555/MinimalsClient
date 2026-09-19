package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.ShulkerPreviewModule;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Shulker Preview, item side.
 *
 * 1. getTooltipImage(): vanilla returns empty for shulker boxes (BlockItem does not override
 *    Item.getTooltipImage). For a shulker with contents we return a ShulkerTooltip, which the
 *    GuiGraphicsExtractor mixin turns into the preview.
 * 2. addToTooltip(): vanilla also prints the contents as text lines ("Item x2", "and N more").
 *    With the preview showing them, those lines are redundant, so the CONTAINER lines are
 *    dropped while the module is active. Every other component still prints normally.
 */
@Mixin(ItemStack.class)
public abstract class ShulkerItemStackMixin {

    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
    private void minimals$shulkerTooltipImage(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        ShulkerPreviewModule module = ModuleManager.shulkerPreview();
        if (!module.isEnabled()) {
            return;
        }
        Optional<TooltipComponent> preview = module.tooltipFor((ItemStack) (Object) this);
        if (preview.isPresent()) {
            cir.setReturnValue(preview);
        }
    }

    @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
    private <T extends TooltipProvider> void minimals$hideShulkerText(
            DataComponentType<T> type, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag, CallbackInfo ci) {
        if (type != DataComponents.CONTAINER) {
            return;
        }
        // Only hide the text when the preview is actually being drawn. shows() is the same
        // check tooltipFor() makes; it is repeated here because building the whole preview just
        // to test for presence would be wasted work on every hovered frame.
        if (ModuleManager.shulkerPreview().appliesTo((ItemStack) (Object) this)
                && display.shows(DataComponents.CONTAINER)) {
            ci.cancel();
        }
    }
}
