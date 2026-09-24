package com.minimals.client.dressing;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Undo/redo for the glow mask. One entry = one whole stroke (mouse down to mouse up) or one fill,
 * stored as a <i>sparse diff</i>: only the cells the stroke actually changed, as parallel
 * {@code index / before / after} arrays. A full-mask snapshot per stroke would cost width*height
 * bytes each (16 KB at 128x128, 1 MB at 1024x1024) - a diff for a typical stroke is a few dozen
 * ints, so the history can be deep without any memory or GC pressure.
 *
 * <p>Usage: {@link #begin()}, then {@link #record(int, int, int)} for every cell changed while the
 * mouse is held, then {@link #commit()} on release. Nothing is pushed if the stroke changed nothing.
 */
final class GlowMaskHistory {

    private static final int MAX_ENTRIES = 200;

    private record Diff(int[] index, byte[] before, byte[] after) {
    }

    private final Deque<Diff> undo = new ArrayDeque<>();
    private final Deque<Diff> redo = new ArrayDeque<>();

    // in-progress stroke buffers (grown geometrically, reused between strokes)
    private int[] curIndex = new int[64];
    private byte[] curBefore = new byte[64];
    private byte[] curAfter = new byte[64];
    private int curSize;
    private boolean open;

    void begin() {
        curSize = 0;
        open = true;
    }

    /** Records that cell {@code index} went from {@code before} to {@code after}. */
    void record(int index, int before, int after) {
        if (!open || before == after) {
            return;
        }
        if (curSize == curIndex.length) {
            int n = curSize * 2;
            curIndex = Arrays.copyOf(curIndex, n);
            curBefore = Arrays.copyOf(curBefore, n);
            curAfter = Arrays.copyOf(curAfter, n);
        }
        curIndex[curSize] = index;
        curBefore[curSize] = (byte) before;
        curAfter[curSize] = (byte) after;
        curSize++;
    }

    /** @return true if the stroke changed anything (and was therefore pushed). */
    boolean commit() {
        if (!open) {
            return false;
        }
        open = false;
        if (curSize == 0) {
            return false;
        }
        undo.addLast(new Diff(Arrays.copyOf(curIndex, curSize), Arrays.copyOf(curBefore, curSize),
                Arrays.copyOf(curAfter, curSize)));
        if (undo.size() > MAX_ENTRIES) {
            undo.removeFirst();
        }
        redo.clear();
        curSize = 0;
        return true;
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    boolean undo(SkinEmissionMask mask) {
        Diff d = undo.pollLast();
        if (d == null) {
            return false;
        }
        // Newest change first: if one stroke touched the same cell twice, the LAST-applied
        // "before" is the cell's original value only when walking backwards.
        for (int i = d.index.length - 1; i >= 0; i--) {
            mask.setIndex(d.index[i], d.before[i] & 0xFF);
        }
        redo.addLast(d);
        return true;
    }

    boolean redo(SkinEmissionMask mask) {
        Diff d = redo.pollLast();
        if (d == null) {
            return false;
        }
        for (int i = 0; i < d.index.length; i++) {
            mask.setIndex(d.index[i], d.after[i] & 0xFF);
        }
        undo.addLast(d);
        return true;
    }

    void clear() {
        undo.clear();
        redo.clear();
        curSize = 0;
        open = false;
    }
}
