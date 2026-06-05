package dev.limucc.animatedgui.client.config;

import dev.limucc.animatedgui.client.anim.Easing;
import dev.limucc.animatedgui.client.anim.ScreenStyle;

/**
 * Every knob the user can turn, serialized to JSON as-is. One master switch, then a small {@link Feature} block
 * per animated thing (enabled / duration in ms / easing curve), with screens additionally carrying a
 * {@link ScreenStyle}. Defaults are tuned to feel snappy but visibly smooth.
 */
public class AnimConfig {

    public boolean masterEnabled = true;

    public Feature chat = new Feature(true, 220, Easing.EASE_OUT);
    public Feature items = new Feature(true, 200, Easing.EASE_OUT);
    public Feature creativeScroll = new Feature(true, 220, Easing.EASE_OUT);
    public Feature hotbar = new Feature(true, 150, Easing.EASE_OUT);
    public ScreenFeature screenOpen = new ScreenFeature(true, 220, Easing.EASE_OUT, ScreenStyle.SCALE);
    public ScreenFeature screenClose = new ScreenFeature(true, 180, Easing.EASE_IN, ScreenStyle.SCALE);

    /** Optional motion-blur trail behind the hotbar selector as it slides (off by default). */
    public boolean hotbarTrail = false;

    /** enabled + duration + easing — the shared shape of a single animated feature. */
    public static class Feature {
        public boolean enabled = true;
        public int durationMs = 200;
        public Easing easing = Easing.EASE_OUT;

        public Feature() {}

        public Feature(boolean enabled, int durationMs, Easing easing) {
            this.enabled = enabled;
            this.durationMs = durationMs;
            this.easing = easing;
        }

        /** True only when this feature AND the master switch are both on. */
        public boolean on() {
            return enabled && AnimConfigManager.get().masterEnabled;
        }
    }

    /** A feature that also has a movement style (used for screen open/close). */
    public static class ScreenFeature extends Feature {
        public ScreenStyle style = ScreenStyle.SCALE;

        public ScreenFeature() {}

        public ScreenFeature(boolean enabled, int durationMs, Easing easing, ScreenStyle style) {
            super(enabled, durationMs, easing);
            this.style = style;
        }
    }

    /** Replace any null/invalid fields after a (possibly partial or hand-edited) JSON load. */
    public void normalize() {
        if (chat == null) chat = new Feature(true, 220, Easing.EASE_OUT);
        if (items == null) items = new Feature(true, 200, Easing.EASE_OUT);
        if (creativeScroll == null) creativeScroll = new Feature(true, 250, Easing.EASE_OUT);
        if (hotbar == null) hotbar = new Feature(true, 150, Easing.EASE_OUT);
        if (screenOpen == null) screenOpen = new ScreenFeature(true, 220, Easing.EASE_OUT, ScreenStyle.SCALE);
        if (screenClose == null) screenClose = new ScreenFeature(true, 180, Easing.EASE_IN, ScreenStyle.SCALE);
        for (Feature f : new Feature[]{chat, items, creativeScroll, hotbar, screenOpen, screenClose}) {
            if (f.easing == null) f.easing = Easing.EASE_OUT;
            f.durationMs = Math.max(20, Math.min(2000, f.durationMs));
        }
        if (screenOpen.style == null) screenOpen.style = ScreenStyle.SCALE;
        if (screenClose.style == null) screenClose.style = ScreenStyle.SCALE;
    }
}
