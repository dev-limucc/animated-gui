package dev.limucc.animatedgui.client.anim;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * Remembers, for the few picture-in-picture states (the inventory's player model) submitted while a screen
 * transform is on the stack, the pose + opacity they were submitted with. A PiP renders into its own texture and
 * is blitted later with the state's {@code pose()} — which for these states is the identity — so without this the
 * model ignores the screen's scale/slide. We keep the model's own bounds (so the texture never changes size and
 * is never re-allocated mid-animation) and just blit it through the captured pose instead.
 *
 * <p>A tiny fixed ring keyed by state identity: no allocation per frame, no growth, and a state that is culled and
 * never blitted is simply overwritten 8 submissions later. Render thread only.
 */
public final class PipCapture {

    private PipCapture() {}

    private static final int SIZE = 8;
    private static final Object[] states = new Object[SIZE];
    private static final Matrix3x2f[] poses = new Matrix3x2f[SIZE];
    private static final float[] alphas = new float[SIZE];
    private static int next;

    /** Diagnostics for the gametest: how many PiP states were captured / the opacity of the latest one. */
    public static int puts;
    public static float lastAlpha = 1.0f;

    static {
        for (int i = 0; i < SIZE; i++) poses[i] = new Matrix3x2f();
    }

    /** Record the pose/alpha a PiP state was submitted with. */
    public static void put(Object state, Matrix3x2fc pose, float alpha) {
        int i = next;
        next = (next + 1) % SIZE;
        states[i] = state;
        poses[i].set(pose);
        alphas[i] = alpha;
        puts++;
        lastAlpha = alpha;
    }

    /** Index of {@code state} in the ring, or -1. Identity comparison. */
    public static int find(Object state) {
        for (int i = 0; i < SIZE; i++) {
            if (states[i] == state) return i;
        }
        return -1;
    }

    public static Matrix3x2fc pose(int index) {
        return poses[index];
    }

    public static float alpha(int index) {
        return alphas[index];
    }

    /** ARGB colour that scales a <em>premultiplied-alpha</em> texture by {@code alpha}: every channel carries it. */
    public static int premultipliedTint(float alpha) {
        if (alpha >= 1.0f) return -1;
        int a = Math.round(Math.max(0.0f, alpha) * 255.0f);
        return (a << 24) | (a << 16) | (a << 8) | a;
    }
}
