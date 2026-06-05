package dev.limucc.animatedgui.client.anim;

import org.joml.Matrix3x2f;

/**
 * How navigating from one menu to another (Pause → Options, a sub-screen's Back, …) animates. Every style is
 * <em>single-screen</em>: the outgoing menu plays its exit, then — over the same, never-blanking backdrop
 * (panorama / blurred game) — the incoming menu plays its enter. Only ever one screen renders at a time, so
 * there's no ghosting and nothing fragile to crash in a heavily-modded GUI pipeline.
 *
 * <ul>
 *   <li><b>SLIDE</b> — carousel: the old panel slides off one side, the new panel slides in from the other.</li>
 *   <li><b>ZOOM</b> — the old panel shrinks &amp; fades away; the new grows in &amp; fades up.</li>
 *   <li><b>FADE</b> — the old fades out, the new fades in (no movement).</li>
 *   <li><b>SWAP</b> — the old is dropped instantly; only the new fades in (fastest).</li>
 * </ul>
 *
 * <p>The exit transform/opacity ({@link #applyExit}/{@link #exitAlpha}) is driven by the menu-close timeline,
 * the enter ({@link #applyEnter}/{@link #enterAlpha}) by the menu-open timeline — so the existing
 * "Menu open" / "Menu close" duration &amp; easing settings tune the feel. Inputs are already eased, 0→1.
 */
public enum MenuTransition {
    SLIDE("Slide"),
    ZOOM("Zoom"),
    FADE("Fade"),
    SWAP("Swap");

    /** Travel for the slide, as a fraction of screen width — enough to clear a centred menu panel. */
    private static final float SLIDE_TRAVEL = 0.80f;

    private final String label;

    MenuTransition(String label) { this.label = label; }

    public String label() { return label; }

    public MenuTransition next() {
        MenuTransition[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    /** Keep the outgoing menu on screen to animate out? SWAP drops it instantly. */
    public boolean deferOld() { return this != SWAP; }

    // ── exit: the outgoing menu leaving, te runs 0 (fully shown) → 1 (gone) ──────────
    public void applyExit(Matrix3x2f pose, float te, int w, int h) {
        switch (this) {
            case SLIDE -> pose.translate(-te * w * SLIDE_TRAVEL, 0.0f);
            case ZOOM -> scaleAbout(pose, 1.0f - 0.14f * te, w, h);
            default -> { /* FADE / SWAP: opacity only */ }
        }
    }

    public float exitAlpha(float te) {
        return switch (this) {
            case ZOOM, FADE -> clamp(1.0f - te);
            default -> 1.0f; // SLIDE stays opaque; SWAP has no exit
        };
    }

    // ── enter: the incoming menu arriving, tn runs 0 (off/hidden) → 1 (settled) ──────
    public void applyEnter(Matrix3x2f pose, float tn, int w, int h) {
        switch (this) {
            case SLIDE -> pose.translate((1.0f - tn) * w * SLIDE_TRAVEL, 0.0f);
            case ZOOM -> scaleAbout(pose, 1.10f - 0.10f * tn, w, h);
            default -> { /* FADE / SWAP: opacity only */ }
        }
    }

    public float enterAlpha(float tn) {
        return switch (this) {
            case ZOOM, FADE, SWAP -> clamp(tn);
            default -> 1.0f; // SLIDE stays opaque
        };
    }

    private static void scaleAbout(Matrix3x2f pose, float s, int w, int h) {
        float cx = w / 2f, cy = h / 2f;
        pose.translate(cx, cy);
        pose.scale(s, s);
        pose.translate(-cx, -cy);
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
