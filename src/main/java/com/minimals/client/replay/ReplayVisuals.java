package com.minimals.client.replay;

/**
 * The checkboxes of the replay editor's "Visuals" panel, grouped into sections.
 *
 * Only the structure exists so far: the values are stored so the panel keeps its state while a
 * replay is open, but nothing reads them yet, with one exception: {@link Toggle#OVERRIDE_FOV} is
 * the existing FOV override, so it reads and writes {@link ReplayView} directly.
 */
public final class ReplayVisuals {

    public enum Section {
        GUI("GUI"),
        WORLD("World"),
        OVERRIDES("Overrides"),
        OTHER("Other");

        private final String label;

        Section(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Toggle {
        CHAT(Section.GUI, "Chat", false),
        BOSS_BAR(Section.GUI, "Boss Bar", false),
        TITLE_TEXT(Section.GUI, "Title Text", false),
        SCOREBOARD(Section.GUI, "Scoreboard", false),
        ACTION_BAR(Section.GUI, "Action Bar", false),

        RENDER_BLOCKS(Section.WORLD, "Render Blocks", true),
        RENDER_ENTITIES(Section.WORLD, "Render Entities", true),
        RENDER_PLAYERS(Section.WORLD, "Render Players", true),
        RENDER_PARTICLES(Section.WORLD, "Render Particles", true),
        RENDER_SKY(Section.WORLD, "Render Sky", true),
        RENDER_NAMETAGS(Section.WORLD, "Render Nametags", true),

        OVERRIDE_FOG(Section.OVERRIDES, "Override Fog", false),
        OVERRIDE_FOV(Section.OVERRIDES, "Override FOV", false),
        OVERRIDE_TIME(Section.OVERRIDES, "Override Time", false),
        CAMERA_SHAKE(Section.OVERRIDES, "Camera Shake", false),
        CAMERA_ROLL(Section.OVERRIDES, "Camera Roll", false),

        RULE_OF_THIRDS(Section.OTHER, "Rule of Thirds Guide", false),
        CENTER_GUIDE(Section.OTHER, "Center Guide", false),
        CAMERA_PATH(Section.OTHER, "Camera Path", true);

        private final Section section;
        private final String label;
        private final boolean defaultValue;

        Toggle(Section section, String label, boolean defaultValue) {
            this.section = section;
            this.label = label;
            this.defaultValue = defaultValue;
        }

        public Section section() {
            return section;
        }

        public String label() {
            return label;
        }

        public boolean defaultValue() {
            return defaultValue;
        }
    }

    private static final boolean[] VALUES = new boolean[Toggle.values().length];

    static {
        reset();
    }

    private ReplayVisuals() {
    }

    public static boolean get(Toggle toggle) {
        if (toggle == Toggle.OVERRIDE_FOV) {
            return ReplayView.isFovOverride();
        }
        return VALUES[toggle.ordinal()];
    }

    public static void set(Toggle toggle, boolean on) {
        if (toggle == Toggle.OVERRIDE_FOV) {
            ReplayView.setFovOverride(on);
            return;
        }
        VALUES[toggle.ordinal()] = on;
    }

    public static void flip(Toggle toggle) {
        set(toggle, !get(toggle));
    }

    /** Back to the defaults. The FOV override is owned by {@link ReplayView#reset()}. */
    public static void reset() {
        for (Toggle t : Toggle.values()) {
            VALUES[t.ordinal()] = t.defaultValue();
        }
    }
}
