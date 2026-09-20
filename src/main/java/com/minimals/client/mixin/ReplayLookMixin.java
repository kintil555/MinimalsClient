package com.minimals.client.mixin;

/*
 * Intentionally empty. The replay free camera now turns through vanilla MouseHandler.turnPlayer
 * (the mouse is grabbed with MouseHandler.grabMouse), so no custom look hook is needed. Kept as a
 * file only so the mixin list stays stable; it is not registered.
 */
final class ReplayLookMixin {
    private ReplayLookMixin() {
    }
}
