package com.minimals.client.mixin;

import com.minimals.client.sound.MinimalsSounds;
import com.minimals.client.sound.SilentHover;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plays the hover sound when the cursor <em>enters</em> one of this mod's widgets.
 *
 * <p>{@code AbstractWidget.extractRenderState} is final and is where vanilla recomputes
 * {@code isHovered} every frame (it already accounts for the active scissor, so a row that is
 * half scrolled out of the menu viewport is not "hovered" over its clipped part). We inject at
 * its end and compare with the previous frame: false -> true is one hover event.
 *
 * <p>Scoped to widgets from {@code com.minimals.client} so vanilla screens and other mods keep
 * their own behaviour, and widgets implementing {@link SilentHover} (labels) stay quiet.
 */
@Mixin(AbstractWidget.class)
public abstract class WidgetHoverSoundMixin {

    @Shadow protected boolean isHovered;
    @Shadow public boolean active;
    @Shadow public boolean visible;

    @Unique
    private boolean minimals$wasHovered;

    /**
     * Vanilla returns at the very top for a hidden widget, so TAIL never runs for it. Clear the
     * flag here so a row scrolled away and back in counts as a fresh hover.
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void minimals$resetWhenHidden(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                          float delta, CallbackInfo ci) {
        if (!this.visible) {
            this.minimals$wasHovered = false;
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void minimals$hoverSound(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     float delta, CallbackInfo ci) {
        boolean now = this.isHovered && this.active && this.visible;
        if (now && !this.minimals$wasHovered && minimals$isOurs()) {
            MinimalsSounds.playHover();
        }
        this.minimals$wasHovered = now;
    }

    @Unique
    private boolean minimals$isOurs() {
        Object self = this;
        return !(self instanceof SilentHover)
                && self.getClass().getName().startsWith("com.minimals.client.");
    }
}
