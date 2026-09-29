package com.minimals.client.mixin;

import com.minimals.client.worldhost.WorldHostManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

/**
 * Adds a "Join" button to each row of the vanilla Friends list. It only appears while that
 * friend has a world open through World Host (they are both on the relay and each other's
 * vanilla friend); pressing it joins through the relay, which then upgrades to e4mc.
 * The row layout is vanilla's: face, name, status, then [Join] [Remove] on the right.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.friends.FriendEntry")
public abstract class FriendEntryMixin {
    @Unique private static final int MINIMALS_JOIN_W = 36;
    @Unique private static final int MINIMALS_BTN_H = 20;
    @Unique private static final int MINIMALS_REMOVE_W = 20;
    @Unique private static final int MINIMALS_GAP = 4;

    @Unique private Button minimals$joinButton;
    @Unique private UUID minimals$friendId;

    @Inject(method = "<init>", at = @At("RETURN"))
    @SuppressWarnings("unchecked")
    private void minimals$addJoinButton(CallbackInfo ci) {
        UUID id = ((FriendsEntryAccessor) this).minimals$getPlayerId();
        this.minimals$friendId = id;
        Button join = Button.builder(Component.literal("Join"), b -> WorldHostManager.connectToFriend(id))
                .size(MINIMALS_JOIN_W, MINIMALS_BTN_H).build();
        join.visible = false;
        join.active = false;
        this.minimals$joinButton = join;
        // children() returns the entry's own mutable child list (click + narration dispatch).
        ((List<GuiEventListener>) (List<?>) ((AbstractContainerWidget) (Object) this).children()).add(join);
    }

    @ModifyArg(method = "extractWidgetRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/StringWidget;setMaxWidth(ILnet/minecraft/client/gui/components/StringWidget$TextOverflow;)Lnet/minecraft/client/gui/components/StringWidget;"),
            index = 0)
    private int minimals$shrinkStatus(int maxWidth) {
        return minimals$canJoin() ? maxWidth - MINIMALS_JOIN_W - MINIMALS_GAP : maxWidth;
    }

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void minimals$renderJoinButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        Button join = this.minimals$joinButton;
        if (join == null) return;
        boolean canJoin = minimals$canJoin();
        join.visible = canJoin;
        join.active = canJoin;
        if (!canJoin) return;
        AbstractWidget self = (AbstractWidget) (Object) this;
        int x = self.getX() + self.getWidth() - MINIMALS_REMOVE_W - MINIMALS_GAP - MINIMALS_JOIN_W;
        int y = self.getY() + (self.getHeight() - MINIMALS_BTN_H) / 2;
        join.setPosition(x, y);
        join.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Unique
    private boolean minimals$canJoin() {
        return this.minimals$friendId != null && WorldHostManager.isFriendOnline(this.minimals$friendId);
    }
}
