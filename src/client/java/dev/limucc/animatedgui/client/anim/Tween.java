package dev.limucc.animatedgui.client.anim;

/**
 * A single retargetable, time-based interpolation. Every animation in the mod is one of these: it holds a
 * {@code current} value that glides from wherever it is toward a {@code target} using the chosen {@link Easing}
 * over a fixed duration, in wall-clock milliseconds (so it's framerate-independent).
 *
 * <p>When the target changes mid-flight, {@link #retarget} re-anchors the curve at the current value, so there's
 * never a jump — the hotbar selector, chat slide, creative scroll and screen transitions all rely on this.
 */
public final class Tween {

    private float start;
    private float end;
    private float current;
    private long startMs;
    private int durationMs = 1;
    private Easing easing = Easing.LINEAR;
    private boolean active;

    public Tween() {}

    public Tween(float initial) { snap(initial); }

    /** Jump straight to {@code value} with no animation (used to adopt external changes). */
    public void snap(float value) {
        this.start = this.end = this.current = value;
        this.active = false;
    }

    public float current() { return current; }

    public boolean isActive() { return active; }

    /** Aim at {@code target}; if it differs from where we're already heading, start a fresh eased segment. */
    public void retarget(float target, long now, int durationMs, Easing easing) {
        if (active && Math.abs(target - end) < 1.0e-4f) return;
        if (!active && Math.abs(target - current) < 1.0e-4f) { this.end = target; return; }
        this.start = current;
        this.end = target;
        this.startMs = now;
        this.durationMs = Math.max(1, durationMs);
        this.easing = easing;
        this.active = true;
    }

    /** Advance to {@code now} and return the current value. */
    public float update(long now) {
        if (!active) return current;
        float t = (now - startMs) / (float) durationMs;
        if (t >= 1.0f) {
            current = end;
            active = false;
        } else if (t > 0.0f) {
            current = start + (end - start) * easing.apply(t);
        }
        return current;
    }
}
