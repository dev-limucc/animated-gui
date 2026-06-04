package dev.limucc.animatedgui.client.anim;

import org.joml.Matrix3x2f;

/**
 * How a screen enters/leaves. Each style is a pose transform parameterized by a progress {@code p}:
 * {@code p = 1} means fully shown (identity), {@code p → 0} means fully hidden. Opening plays {@code p}
 * from 0→1, closing plays it 1→0, so one transform serves both directions.
 *
 * <p>{@code p} is the already-eased value, so with the BACK/ELASTIC curves SCALE overshoots into a satisfying pop.
 */
public enum ScreenStyle {
    SCALE("Scale") {
        @Override public void apply(Matrix3x2f pose, float p, int w, int h) {
            float cx = w / 2f, cy = h / 2f;
            float s = 0.62f + 0.38f * p;
            pose.translate(cx, cy);
            pose.scale(s, s);
            pose.translate(-cx, -cy);
        }
    },
    SLIDE_UP("Slide up") {
        @Override public void apply(Matrix3x2f pose, float p, int w, int h) {
            pose.translate(0.0f, (1.0f - p) * h * 0.22f);
        }
    },
    SLIDE_DOWN("Slide down") {
        @Override public void apply(Matrix3x2f pose, float p, int w, int h) {
            pose.translate(0.0f, -(1.0f - p) * h * 0.22f);
        }
    },
    SLIDE_LEFT("Slide left") {
        @Override public void apply(Matrix3x2f pose, float p, int w, int h) {
            pose.translate((1.0f - p) * w * 0.22f, 0.0f);
        }
    },
    SLIDE_RIGHT("Slide right") {
        @Override public void apply(Matrix3x2f pose, float p, int w, int h) {
            pose.translate(-(1.0f - p) * w * 0.22f, 0.0f);
        }
    };

    private final String label;

    ScreenStyle(String label) { this.label = label; }

    public String label() { return label; }

    /** Mutate the (already-pushed) pose for this style at progress {@code p}. */
    public abstract void apply(Matrix3x2f pose, float p, int w, int h);

    public ScreenStyle next() {
        ScreenStyle[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
