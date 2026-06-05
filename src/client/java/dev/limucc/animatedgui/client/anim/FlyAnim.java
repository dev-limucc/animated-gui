package dev.limucc.animatedgui.client.anim;

/**
 * A single "item is gliding from its old slot to this one" animation. Created when the container diff spots a
 * stack that left one slot and arrived in another in the same frame; consumed by the slot renderer, which
 * offsets the item from {@code (fromX,fromY)} back toward the destination as the curve plays out.
 */
public final class FlyAnim {

    public final int fromX;
    public final int fromY;
    public final long startMs;
    public final int durationMs;
    public final Easing easing;
    /** True when the item merged into an existing stack — animate only a ghost copy, leaving the stack in place. */
    public final boolean merge;

    public FlyAnim(int fromX, int fromY, long startMs, int durationMs, Easing easing, boolean merge) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.startMs = startMs;
        this.durationMs = Math.max(1, durationMs);
        this.easing = easing;
        this.merge = merge;
    }

    /** Fraction of the source→dest offset still to apply: 1 at the start, 0 once it has landed. */
    public float remaining(long now) {
        float t = (now - startMs) / (float) durationMs;
        if (t >= 1.0f) return 0.0f;
        if (t < 0.0f) t = 0.0f;
        return 1.0f - easing.apply(t);
    }
}
