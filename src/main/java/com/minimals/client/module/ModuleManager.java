package com.minimals.client.module;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {

    private static final List<Module> MODULES = new ArrayList<>();
    private static final Map<Module.Category, List<Module>> BY_CATEGORY = new EnumMap<>(Module.Category.class);

    static {
        register(new Module("AutoSwap", Module.Category.COMBAT));
        register(new HitBoxesModule());
        register(new CustomHitSoundModule());

        register(new Module("Sprint", Module.Category.MOVEMENT));
        register(new Module("Nearby Entities", Module.Category.MOVEMENT));

        register(new LowOnFireModule());
        register(new HurtColorModule());
        register(new Module("Fullbright", Module.Category.VISUALS));
        register(new Module("HUD", Module.Category.VISUALS, true));
        register(new KeystrokeModule());
        register(new BlockOnEntitiesModule());
        register(new SnapPerspectiveModule());
        register(new ShulkerPreviewModule());

        register(new OptimizationModule());
        register(new CrystalOptimizerModule());
    }

    private ModuleManager() {
    }

    private static void register(Module module) {
        MODULES.add(module);
        BY_CATEGORY.computeIfAbsent(module.getCategory(), c -> new ArrayList<>()).add(module);
    }

    public static List<Module> getModules(Module.Category category) {
        return BY_CATEGORY.getOrDefault(category, List.of());
    }

    public static List<Module> getAllModules() {
        return MODULES;
    }

    public static BlockOnEntitiesModule blockOnEntities() {
        for (Module m : MODULES) {
            if (m instanceof BlockOnEntitiesModule module) {
                return module;
            }
        }
        throw new IllegalStateException("BlockOnEntitiesModule is not registered");
    }

    public static OptimizationModule optimization() {
        for (Module m : MODULES) {
            if (m instanceof OptimizationModule module) {
                return module;
            }
        }
        throw new IllegalStateException("OptimizationModule is not registered");
    }

    public static LowOnFireModule lowOnFire() {
        for (Module m : MODULES) {
            if (m instanceof LowOnFireModule module) {
                return module;
            }
        }
        throw new IllegalStateException("LowOnFireModule is not registered");
    }

    public static HurtColorModule hurtColor() {
        for (Module m : MODULES) {
            if (m instanceof HurtColorModule module) {
                return module;
            }
        }
        throw new IllegalStateException("HurtColorModule is not registered");
    }

    public static CrystalOptimizerModule crystalOptimizer() {
        for (Module m : MODULES) {
            if (m instanceof CrystalOptimizerModule module) {
                return module;
            }
        }
        throw new IllegalStateException("CrystalOptimizerModule is not registered");
    }

    public static CustomHitSoundModule customHitSound() {
        for (Module m : MODULES) {
            if (m instanceof CustomHitSoundModule module) {
                return module;
            }
        }
        throw new IllegalStateException("CustomHitSoundModule is not registered");
    }

    public static ShulkerPreviewModule shulkerPreview() {
        for (Module m : MODULES) {
            if (m instanceof ShulkerPreviewModule module) {
                return module;
            }
        }
        throw new IllegalStateException("ShulkerPreviewModule is not registered");
    }

    public static KeystrokeModule keystrokes() {
        for (Module m : MODULES) {
            if (m instanceof KeystrokeModule module) {
                return module;
            }
        }
        throw new IllegalStateException("KeystrokeModule is not registered");
    }

    public static boolean isEnabled(String name) {
        for (Module m : MODULES) {
            if (m.getName().equalsIgnoreCase(name)) {
                return m.isEnabled();
            }
        }
        return false;
    }
}
