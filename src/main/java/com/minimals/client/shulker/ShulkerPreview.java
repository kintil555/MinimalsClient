package com.minimals.client.shulker;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Condensed view of a shulker box's contents for the tooltip preview.
 *
 * Identical stacks (same item and same data components) are merged into one entry whose count is
 * the sum, so a box full of 27 stacks of cobblestone shows as a single slot instead of 27. Only
 * the first {@link #MAX_SLOTS} distinct entries are kept; when more exist, {@link #hasMore()} is
 * true and the tooltip prints "and more".
 *
 * Entries keep first-seen (container slot) order, so the preview is stable while hovering.
 */
public final class ShulkerPreview {

    /** Maximum number of item slots drawn in the preview. */
    public static final int MAX_SLOTS = 8;

    private final List<ItemStack> entries;
    private final boolean more;

    private ShulkerPreview(List<ItemStack> entries, boolean more) {
        this.entries = entries;
        this.more = more;
    }

    /** Builds the preview from a shulker's CONTAINER component. Never returns null. */
    public static ShulkerPreview of(ItemContainerContents contents) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : (Iterable<ItemStack>) contents.nonEmptyItemCopyStream()::iterator) {
            merge(merged, stack);
        }
        boolean overflow = merged.size() > MAX_SLOTS;
        List<ItemStack> shown = overflow ? new ArrayList<>(merged.subList(0, MAX_SLOTS)) : merged;
        return new ShulkerPreview(List.copyOf(shown), overflow);
    }

    private static void merge(List<ItemStack> merged, ItemStack stack) {
        for (ItemStack existing : merged) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                // Only the local copy's count changes; the shulker's real contents are untouched
                // because nonEmptyItemCopyStream() hands out fresh ItemStack instances.
                existing.setCount(existing.getCount() + stack.getCount());
                return;
            }
        }
        merged.add(stack);
    }

    public List<ItemStack> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** True when more than {@link #MAX_SLOTS} distinct items exist, so "and more" is shown. */
    public boolean hasMore() {
        return more;
    }
}
