package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayListScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a small "R" (replays) button to the right of the Singleplayer button, the same slot vanilla
 * uses for its dev-only "TW" button (which is only present when running from an IDE).
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minimals$addReplayButton(CallbackInfo ci) {
        // Singleplayer sits at (width/2 - 100, height/4 + 48), 200x20; the button goes just right of it.
        int y = this.height / 4 + 48;
        Screen self = this;
        addRenderableWidget(Button.builder(Component.literal("R"),
                        btn -> this.minecraft.gui.setScreen(new ReplayListScreen(self)))
                .bounds(this.width / 2 + 104, y, 20, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Replays")))
                .build());
    }
}
