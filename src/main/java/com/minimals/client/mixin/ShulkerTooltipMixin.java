package com.minimals.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.minimals.client.shulker.ClientShulkerTooltip;
import com.minimals.client.shulker.ShulkerTooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shulker Preview, rendering side. ClientTooltipComponent.create(TooltipComponent) is a closed
 * switch that throws IllegalArgumentException("Unknown TooltipComponent") for anything except
 * BundleTooltip and ActivePlayersTooltip, so a ShulkerTooltip has to be converted before it runs.
 *
 * ClientTooltipComponent is an interface, and Mixin cannot reliably inject into interface static
 * methods (SpongePowered/Mixin#318, #497), so create() itself is left alone and its callers are
 * wrapped instead. A scan of every class in the 26.2 jar shows exactly two external callers:
 *  - GuiGraphicsExtractor: the lambdas inside the two setTooltipForNextFrame overloads that take
 *    an Optional image (lambda$setTooltipForNextFrame$0 and $1) - handled here
 *  - AbstractContainerScreen.showTooltipWithItemInHand - handled in ShulkerContainerScreenMixin
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ShulkerTooltipMixin {

    @WrapOperation(method = {"lambda$setTooltipForNextFrame$0", "lambda$setTooltipForNextFrame$1"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;"))
    private static ClientTooltipComponent minimals$createShulkerTooltip(TooltipComponent component,
                                                                       Operation<ClientTooltipComponent> original) {
        if (component instanceof ShulkerTooltip shulker) {
            return new ClientShulkerTooltip(shulker.preview());
        }
        return original.call(component);
    }
}
