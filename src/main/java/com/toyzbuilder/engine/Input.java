package com.toyzbuilder.engine;

import org.lwjgl.glfw.GLFW;

/**
 * Keyboard + mouse abstraction (Minecraft-style edge + held + scroll).
 */
public final class Input {

    private final long window;
    private final boolean[] keyDown = new boolean[512];
    private final boolean[] keyWasDown = new boolean[512];
    private final boolean[] mouseDown = new boolean[8];
    private final boolean[] mouseWasDown = new boolean[8];
    private double mouseX, mouseY;
    private double mouseDX, mouseDY;
    private boolean mouseValid;
    private boolean cursorDisabled = true;
    private double scrollAccum;
    private int scrollSteps; // discrete notches this frame

    public Input(long window) {
        this.window = window;
        GLFW.glfwSetScrollCallback(window, (win, xoff, yoff) -> {
            scrollAccum += yoff;
        });
    }

    public void beginFrame() {
        System.arraycopy(keyDown, 0, keyWasDown, 0, keyDown.length);
        for (int k = 0; k < keyDown.length; k++) {
            keyDown[k] = GLFW.glfwGetKey(window, k) == GLFW.GLFW_PRESS;
        }
        System.arraycopy(mouseDown, 0, mouseWasDown, 0, mouseDown.length);
        for (int b = 0; b < mouseDown.length; b++) {
            mouseDown[b] = GLFW.glfwGetMouseButton(window, b) == GLFW.GLFW_PRESS;
        }

        // convert accumulated scroll to discrete steps (±1 per notch)
        scrollSteps = 0;
        if (scrollAccum >= 0.5) {
            scrollSteps = (int) scrollAccum;
            scrollAccum -= scrollSteps;
        } else if (scrollAccum <= -0.5) {
            scrollSteps = (int) scrollAccum; // negative
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

    /** Discrete scroll notches this frame (positive = up / away). */
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
