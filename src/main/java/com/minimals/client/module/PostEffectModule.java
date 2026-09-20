package com.minimals.client.module;

import com.minimals.client.module.setting.EnumSetting;
import com.minimals.client.module.setting.StringSetting;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Post Effect: applies fullscreen post-processing shaders (assets/&lt;ns&gt;/post_effect/*.json) on
 * top of the finished frame. This is the 26.3 "/posteffect" idea done on the client, on 26.2.
 *
 * 26.3 replaced vanilla's single hardcoded spectator slot with a list of effects the player can
 * add/remove. 26.2 still only has that one slot (GameRenderer.postEffectId), so this module keeps
 * its own list and PostEffectMixin runs it after the vanilla one, using the same public calls
 * vanilla does: ShaderManager.getPostChain(id, MAIN_TARGETS) + PostChain.process(...).
 *
 * Two independent slots so effects can be stacked (order: Effect 1, then Effect 2, then Custom).
 * Effect 1/2 pick from the built-in effects that always exist; Custom takes any resource-pack id
 * ("namespace:name", the file post_effect/name.json in that namespace).
 */
public class PostEffectModule extends Module {

    /** Built-in effects shipped in assets/minecraft/post_effect (present in 26.2 and 26.3). */
    public enum Preset implements EnumSetting.Labeled {
        NONE("None", null),
        BLUR("Blur", "minecraft:blur"),
        INVERT("Invert", "minecraft:invert"),
        CREEPER("Creeper", "minecraft:creeper"),
        SPIDER("Spider", "minecraft:spider");

        private final String label;
        private final Identifier id;

        Preset(String label, String id) {
            this.label = label;
            this.id = id == null ? null : Identifier.parse(id);
        }

        @Override
        public String label() {
            return label;
        }

        /** Null for NONE. */
        public Identifier id() {
            return id;
        }
    }

    public final EnumSetting<Preset> effect1 =
            addSetting(new EnumSetting<>("Effect 1", Preset.NONE, Preset.values()));
    public final EnumSetting<Preset> effect2 =
            addSetting(new EnumSetting<>("Effect 2", Preset.NONE, Preset.values()));
    /** Any resource-pack post effect, e.g. "mypack:vhs". Empty = unused. */
    public final StringSetting custom = addSetting(new StringSetting("Custom ID", "", 64));

    public PostEffectModule() {
        super("Post Effect", Category.VISUALS);
    }

    /**
     * Effects to run this frame, in order. Invalid custom ids are skipped (never thrown), and a
     * duplicate is dropped so the same chain is not processed twice in one frame.
     */
    public List<Identifier> activeEffects() {
        List<Identifier> out = new ArrayList<>(3);
        addUnique(out, effect1.get().id());
        addUnique(out, effect2.get().id());
        String raw = custom.get().trim();
        if (!raw.isEmpty()) {
            Identifier parsed = Identifier.tryParse(raw);
            if (parsed != null) {
                addUnique(out, parsed);
            }
        }
        return out;
    }

    private static void addUnique(List<Identifier> list, Identifier id) {
        if (id != null && !list.contains(id)) {
            list.add(id);
        }
    }
}
