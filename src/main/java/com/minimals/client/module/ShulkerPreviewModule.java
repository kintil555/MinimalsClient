package com.minimals.client.module;

import com.minimals.client.shulker.ShulkerPreview;
import com.minimals.client.shulker.ShulkerTooltip;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Optional;

/**
 * Shulker Preview: hovering a shulker box in an inventory shows a compact preview of its contents
 * under the name (max 8 slots, identical items merged, "and more" on overflow).
 *
 * The behaviour lives in two mixins (ShulkerTooltipMixin, ShulkerTextMixin); this class holds the
 * on/off state and the shared decision of which stacks get a preview.
 */
public class ShulkerPreviewModule extends Module {

    public ShulkerPreviewModule() {
        super("Shulker Preview", Category.VISUALS);
    }

    /** True for shulker boxes (any colour) that carry a CONTAINER component. */
    public boolean appliesTo(ItemStack stack) {
        return isEnabled()
                && stack.is(ItemTags.SHULKER_BOXES)
                && stack.has(DataComponents.CONTAINER);
    }

    /**
     * Tooltip data for a shulker, or empty when this module should stay out of the way (module
     * off, not a shulker, or the item's tooltip is hidden via TooltipDisplay - same rule vanilla
     * uses for bundles).
     */
    public Optional<TooltipComponent> tooltipFor(ItemStack stack) {
        if (!appliesTo(stack)) {
            return Optional.empty();
        }
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (!display.shows(DataComponents.CONTAINER)) {
            return Optional.empty();
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return Optional.empty();
        }
        return Optional.of(new ShulkerTooltip(ShulkerPreview.of(contents)));
    }
}
