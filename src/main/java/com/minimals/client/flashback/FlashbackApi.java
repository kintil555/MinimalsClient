package com.minimals.client.flashback;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;

/** Direct Flashback calls. Never reference this class unless Flashback is loaded; see FlashbackBridge. */
final class FlashbackApi {

    private FlashbackApi() {
    }

    static boolean isRecording() {
        return Flashback.RECORDER != null;
    }

    static boolean canRecord() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && !Flashback.isInReplay();
    }

    static void start() {
        if (canRecord() && Flashback.RECORDER == null) {
            Flashback.startRecordingReplay();
        }
    }

    static void finish() {
        if (Flashback.RECORDER != null) {
            Flashback.finishRecordingReplay();
        }
    }

    static boolean isInReplay() {
        return Flashback.isInReplay();
    }
}
