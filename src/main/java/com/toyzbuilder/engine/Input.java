package com.toyzbuilder.engine;

import org.lwjgl.glfw.GLFW;

/**
 * Keyboard + mouse abstraction (Minecraft-style edge + held + scroll).
 * Only polls valid GLFW key codes — never 0..511 blindly (keys 400+ are
 * invalid and spam GLFW_INVALID_ENUM, which freezes low-end PCs).
 */
public final class Input {

    /** GLFW_KEY_LAST is 348; array sized to cover it. */
    private static final int KEY_CAP = 512;

    private final long window;
    private final boolean[] keyDown = new boolean[KEY_CAP];
    private final boolean[] keyWasDown = new boolean[KEY_CAP];
    private final boolean[] mouseDown = new boolean[8];
    private final boolean[] mouseWasDown = new boolean[8];
    private double mouseX, mouseY;
    private double mouseDX, mouseDY;
    private boolean mouseValid;
    private boolean cursorDisabled = true;
    private double scrollAccum;
    private int scrollSteps;

    /**
     * Valid GLFW keys we care about. Printable + common controls + F-keys/arrows.
     * Built once; avoids glfwGetKey on invalid enums.
     */
    private static final int[] POLL_KEYS;
    static {
        // Space(32)..grave(96), then 256..GLFW_KEY_LAST(348)
        int n = 0;
        int[] tmp = new int[256];
        for (int k = 32; k <= 96; k++) tmp[n++] = k;           // space, 0-9, A-Z, symbols
        for (int k = 256; k <= GLFW.GLFW_KEY_LAST; k++) tmp[n++] = k; // world/func/arrow/mods
        POLL_KEYS = new int[n];
        System.arraycopy(tmp, 0, POLL_KEYS, 0, n);
    }

    public Input(long window) {
        this.window = window;
        GLFW.glfwSetScrollCallback(window, (win, xoff, yoff) -> {
            scrollAccum += yoff;
        });
    }

    public void beginFrame() {
        System.arraycopy(keyDown, 0, keyWasDown, 0, keyDown.length);
        // clear then set only polled keys (unpolled stay false)
        java.util.Arrays.fill(keyDown, false);
        for (int k : POLL_KEYS) {
            if (k >= 0 && k < KEY_CAP) {
                keyDown[k] = GLFW.glfwGetKey(window, k) == GLFW.GLFW_PRESS;
            }
        }

        System.arraycopy(mouseDown, 0, mouseWasDown, 0, mouseDown.length);
        for (int b = 0; b < mouseDown.length; b++) {
            mouseDown[b] = GLFW.glfwGetMouseButton(window, b) == GLFW.GLFW_PRESS;
        }

        scrollSteps = 0;
        if (scrollAccum >= 0.5) {
            scrollSteps = (int) scrollAccum;
            scrollAccum -= scrollSteps;
        } else if (scrollAccum <= -0.5) {
            scrollSteps = (int) scrollAccum;
            scrollAccum -= scrollSteps;
        }

        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(window, mx, my);
        if (!mouseValid) {
            mouseX = mx[0];
            mouseY = my[0];
            mouseDX = 0;
            mouseDY = 0;
            mouseValid = true;
        } else {
            mouseDX = mx[0] - mouseX;
            mouseDY = my[0] - mouseY;
            mouseX = mx[0];
            mouseY = my[0];
        }
    }

    public boolean down(int key) {
        return key >= 0 && key < keyDown.length && keyDown[key];
    }

    public boolean pressed(int key) {
        return down(key) && !(key < keyWasDown.length && keyWasDown[key]);
    }

    public boolean released(int key) {
        return !down(key) && (key < keyWasDown.length && keyWasDown[key]);
    }

    public boolean mouseDown(int button) {
        return button >= 0 && button < mouseDown.length && mouseDown[button];
    }

    public boolean mousePressed(int button) {
        return mouseDown(button) && !(button < mouseWasDown.length && mouseWasDown[button]);
    }

    public boolean mouseReleased(int button) {
        return !mouseDown(button) && (button < mouseWasDown.length && mouseWasDown[button]);
    }

    public int scrollSteps() { return scrollSteps; }

    public double mouseX() { return mouseX; }
    public double mouseY() { return mouseY; }
    public double mouseDX() { return mouseDX; }
    public double mouseDY() { return mouseDY; }

    public void resyncMouse() {
        mouseValid = false;
        mouseDX = 0;
        mouseDY = 0;
    }

    public void setCursorDisabled(boolean disabled) {
        if (disabled == cursorDisabled) return;
        cursorDisabled = disabled;
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR,
                disabled ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
        resyncMouse();
    }

    public boolean isCursorDisabled() { return cursorDisabled; }

    public long windowHandle() { return window; }
}
