package com.minimals.client.dressing;

import com.tom.cpm.client.GuiImpl;
import com.tom.cpm.shared.editor.gui.EditorGui;
import com.tom.cpm.shared.gui.ModelsGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Foundation for the Dressing Room "Customize" tab. For now it simply hands off to the screens
 * of the bundled Customizable Player Models mod (MIT, by tom5454). Every CPM reference in this
 * mod lives in this one class, so the CPM code can later be replaced by our own editor without
 * touching {@link DressingRoomScreen}.
 *
 * <p>CPM's {@link GuiImpl} returns to {@code parent} when it is closed, so passing the Dressing
 * Room brings the player straight back to it.
 */
public final class CustomizePlayerBridge {

    private CustomizePlayerBridge() {
    }

    /** Opens CPM's full model editor (build/edit the player's model, textures, animations). */
    public static void openEditor(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new GuiImpl(EditorGui::new, parent));
    }

    /** Opens CPM's model browser (choose/apply one of the saved models). */
    public static void openModels(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new GuiImpl(ModelsGui::new, parent));
    }
}
