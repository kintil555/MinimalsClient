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
        register(new Module("HitBoxes", Module.Category.COMBAT));

        register(new Module("Sprint", Module.Category.MOVEMENT));
        register(new Module("Speed", Module.Category.MOVEMENT));

        register(new Module("Fullbright", Module.Category.VISUALS));
        register(new Module("HUD", Module.Category.VISUALS, true));
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

    public static boolean isEnabled(String name) {
        for (Module m : MODULES) {
            if (m.getName().equalsIgnoreCase(name)) {
                return m.isEnabled();
            }
        }
        return false;
    }
}
