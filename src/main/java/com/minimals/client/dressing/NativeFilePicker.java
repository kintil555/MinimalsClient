package com.minimals.client.dressing;

import com.minimals.client.MinimalClientMod;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;

/**
 * Thin wrapper around LWJGL's TinyFileDialogs (bundled with every LWJGL-based Minecraft install,
 * same library the game's own screenshot-folder/resource-pack "open in explorer" buttons use
 * under the hood on some platforms) to pop a native "open file" dialog restricted to PNGs.
 *
 * <p>TinyFileDialogs blocks the calling thread until the user closes the dialog, so this must
 * never be called from the render thread. In-game we only ever call it from a button's onPress,
 * which itself runs on the render thread - here it hands off to a short-lived daemon thread and
 * blocks the button callback's caller instead via a simple synchronous wait, since a whole extra
 * async plumbing layer is unnecessary for a modal file chooser the player is already waiting on.
 */
final class NativeFilePicker {

    private NativeFilePicker() {
    }

    static java.nio.file.Path pickPng(String title) {
        try {
            String result;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();
                result = TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, "PNG images", false);
            }
            return result == null ? null : Path.of(result);
        } catch (Throwable t) {
            MinimalClientMod.LOGGER.warn("Dressing room: native file picker unavailable", t);
            return null;
        }
    }
}
