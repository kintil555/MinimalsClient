package com.minimals.client.shulker;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Data carrier handed to the tooltip pipeline in place of a vanilla {@code BundleTooltip}.
 * {@code ClientTooltipComponent.create} is a closed switch that rejects unknown types, so
 * ShulkerTooltipMixin swaps this for {@link ClientShulkerTooltip} before vanilla sees it.
 */
public record ShulkerTooltip(ShulkerPreview preview) implements TooltipComponent {
}
