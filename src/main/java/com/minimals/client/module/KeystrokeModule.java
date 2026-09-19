package com.minimals.client.module;

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

    public int getLeftCps() {
        return leftCps;
    }

    public int getRightCps() {
        return rightCps;
    }
}
