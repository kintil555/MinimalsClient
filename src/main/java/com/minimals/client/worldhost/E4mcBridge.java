package com.minimals.client.worldhost;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Minimals ships e4mc (MIT, by Skye) as a nested mod, so its relay works without a separate
 * download. If the player also installed e4mc themselves, Fabric Loader keeps only the newest
 * copy of the "e4mc" mod id, so the two never clash.
 *
 * e4mc opens its public relay for every LAN world by default. Minimals only wants that when
 * the player picked "Multiplayer", so this flips e4mc's own {@code hostEnabled} option right
 * before the world is published. Done through reflection because e4mc's config types come
 * from a nested library that is not on our compile classpath.
 */
public final class E4mcBridge {
    private E4mcBridge() {}

    public static void setHostEnabled(boolean enabled) {
        if (!FabricLoader.getInstance().isModLoaded("e4mc")) return;
        try {
            Class<?> configClass = Class.forName("link.e4mc.Config");
            Object instance = configClass.getField("INSTANCE").get(null);
            Field field = configClass.getField("hostEnabled");
            Object trackedValue = field.get(instance);
            Class<?> trackedValueClass = Class.forName("folk.sisby.kaleido.lib.quiltconfig.api.values.TrackedValue");
            Method setValue = trackedValueClass.getMethod("setValue", Object.class);
            setValue.invoke(trackedValue, enabled);
        } catch (Throwable t) {
            WorldHostManager.LOGGER.warn("Could not toggle e4mc hostEnabled={}", enabled, t);
        }
    }
}
