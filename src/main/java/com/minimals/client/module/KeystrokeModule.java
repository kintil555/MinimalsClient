package com.minimals.client.module;

import com.minimals.client.module.setting.BoolSetting;
import com.minimals.client.module.setting.ColorSetting;
import com.minimals.client.module.setting.IntSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks WASD + left/right mouse button state and click-per-second counters for the Keystroke
 * HUD element. Ticked from the client tick loop; CPS is a rolling 1-second click count.
 */
public class KeystrokeModule extends Module {

    private final Deque<Long> leftClickTimes = new ArrayDeque<>();
    private final Deque<Long> rightClickTimes = new ArrayDeque<>();
    private boolean prevLeftDown;
    private boolean prevRightDown;

    /** Gap between keys in GUI pixels. */
    public final IntSetting spacing = addSetting(new IntSetting("Spacing", 2, 0, 12, 1, "px"));
    public final ColorSetting idleColor = addSetting(new ColorSetting("Idle Color", 0x808080));
    public final IntSetting idleOpacity = addSetting(new IntSetting("Idle Opacity", 50, 0, 100, 5, "%"));
    public final ColorSetting pressedColor = addSetting(new ColorSetting("Pressed Color", 0xA0A0A0));
    public final IntSetting pressedOpacity = addSetting(new IntSetting("Pressed Opacity", 75, 0, 100, 5, "%"));
    public final ColorSetting textColor = addSetting(new ColorSetting("Text Color", 0xFFFFFF));
    public final BoolSetting showSpace = addSetting(new BoolSetting("Show Space", true));
    public final BoolSetting showShift = addSetting(new BoolSetting("Show Shift", true));
    public final BoolSetting showMouse = addSetting(new BoolSetting("Show Mouse (CPS)", true));

    private volatile int leftCps;
    private volatile int rightCps;

    public KeystrokeModule() {
        super("Keystrokes", Category.VISUALS);
    }

    /** Called once per client tick. */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler == null) {
            return;
        }
        long now = System.currentTimeMillis();

        boolean leftDown = mc.mouseHandler.isLeftPressed();
        if (leftDown && !prevLeftDown) {
            leftClickTimes.addLast(now);
        }
        prevLeftDown = leftDown;

        boolean rightDown = mc.mouseHandler.isRightPressed();
        if (rightDown && !prevRightDown) {
            rightClickTimes.addLast(now);
        }
        prevRightDown = rightDown;

        leftCps = pruneAndCount(leftClickTimes, now);
        rightCps = pruneAndCount(rightClickTimes, now);
    }

    private static int pruneAndCount(Deque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() > 1000L) {
            times.pollFirst();
        }
        return times.size();
    }

    /** ARGB fill for a key: chosen colour with its own opacity, for the idle or pressed state. */
    public int fillColor(boolean pressed) {
        ColorSetting c = pressed ? pressedColor : idleColor;
        int alpha = Math.round((pressed ? pressedOpacity : idleOpacity).get() * 255f / 100f);
        return (alpha << 24) | (c.get() & 0xFFFFFF);
    }

    public int getLeftCps() {
        return leftCps;
    }

    public int getRightCps() {
        return rightCps;
    }
}
