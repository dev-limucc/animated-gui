package dev.limucc.animatedgui.client.config;

import dev.limucc.animatedgui.client.anim.Easing;
import dev.limucc.animatedgui.client.anim.MenuTransition;
import dev.limucc.animatedgui.client.anim.ScreenStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Every knob the user can turn, serialized to JSON as-is. Grouped to match the settings screen tabs:
 * General (master + hotbar + reduced motion), Chat, Inventory (slots / creative scroll / recipe book /
 * inventory open+close), Game menu (menu open+close) and Screens (per-screen deny list).
 * One master switch freezes everything back to vanilla.
 */
public class AnimConfig {

    public boolean masterEnabled = true;

    /**
     * Accessibility: screens only fade (no scale / slide / overshoot, at most {@link #REDUCED_MAX_MS} ms) and every
     * movement-based feature (hotbar glide, list scroll, chat slide, item glide, creative scroll, recipe-book slide)
     * snaps back to vanilla.
     */
    public boolean reducedMotion = false;

    /**
     * The screen dim (inventories) and the menu blur (pause / options) fade in and out with the screen instead of
     * snapping to full strength the moment it opens. Pure opacity, so it stays on under Reduced motion.
     */
    public boolean backdropFade = true;

    /** Longest any screen animation may run while {@link #reducedMotion} is on. */
    public static final int REDUCED_MAX_MS = 100;

    // ── General / HUD ──────────────────────────────────────────────────────────
    public Feature hotbar = new Feature(true, 150, Easing.EASE_OUT).motion();
    public boolean hotbarTrail = false;
    /** Smooth (eased) scrolling for every vanilla-list screen — options, Mod Menu, Cloth Config, mod menus… */
    public Feature listScroll = new Feature(true, 150, Easing.EASE_OUT).motion();

    // ── Chat ───────────────────────────────────────────────────────────────────
    public Feature chat = new Feature(true, 220, Easing.EASE_OUT).motion();

    // ── Inventory ───────────────────────────────────────────────────────────────
    public Feature items = new Feature(true, 200, Easing.EASE_OUT).motion();          // item glide between slots
    public Feature creativeScroll = new Feature(true, 220, Easing.EASE_OUT).motion();
    public Feature recipeBook = new Feature(true, 220, Easing.EASE_OUT).motion();     // book slide + inventory shift + paging
    /** Cross-fade of the slot highlight between hovered slots (opacity only, so it is not a "motion" feature). */
    public Feature hoverFade = new Feature(true, 90, Easing.EASE_OUT);
    public ScreenFeature inventoryOpen = new ScreenFeature(true, 220, Easing.EASE_OUT, ScreenStyle.SCALE);
    public ScreenFeature inventoryClose = new ScreenFeature(true, 220, Easing.EASE_IN_OUT, ScreenStyle.SLIDE_UP);

    // ── Game menu ───────────────────────────────────────────────────────────────
    public ScreenFeature menuOpen = new ScreenFeature(true, 200, Easing.EASE_OUT, ScreenStyle.FADE);
    public ScreenFeature menuClose = new ScreenFeature(true, 200, Easing.EASE_IN_OUT, ScreenStyle.FADE);
    /** How navigating between two menus animates (Pause→Options, a sub-screen's Back, …). */
    public MenuTransition menuTransition = MenuTransition.SLIDE;

    // ── Screens ─────────────────────────────────────────────────────────────────
    /**
     * Screen classes that must never be animated (fully qualified class name, or just the simple name).
     * Edited from the "Screens" tab of the settings screen.
     */
    public List<String> deniedScreens = new ArrayList<>();
    /** Screen classes seen so far, so the "Screens" tab has something to list. Filled in automatically. */
    public List<String> knownScreens = new ArrayList<>();

    /** The transition actually used for menu→menu navigation (Reduced motion forces the movement-free FADE). */
    public MenuTransition effectiveMenuTransition() {
        return reducedMotion ? MenuTransition.FADE : menuTransition;
    }

    /** enabled + duration + easing — the shared shape of a single animated feature. */
    public static class Feature {
        public boolean enabled = true;
        public int durationMs = 200;
        public Easing easing = Easing.EASE_OUT;

        /** Not saved: true for features that are about movement/glide (Reduced motion switches these off). */
        public transient boolean motion;
        /**
         * Not saved: display name of an installed mod that already does this job (Smooth Swapping, Smooth
         * Scrolling). While set, this feature stays off so the two never fight; the settings screen says so.
         */
        public transient String yieldedTo;

        public Feature() {}

        public Feature(boolean enabled, int durationMs, Easing easing) {
            this.enabled = enabled;
            this.durationMs = durationMs;
            this.easing = easing;
        }

        /** Marks this feature as movement-based (builder style, used by the field initialisers). */
        public Feature motion() {
            this.motion = true;
            return this;
        }

        /** True only when this feature AND the master switch are on, no other mod owns the job, and Reduced motion allows it. */
        public boolean on() {
            if (!enabled || yieldedTo != null) return false;
            AnimConfig c = AnimConfigManager.get();
            return c.masterEnabled && !(motion && c.reducedMotion);
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

        /** The style actually played: Reduced motion → still fade. */
        public ScreenStyle effStyle() {
            return AnimConfigManager.get().reducedMotion ? ScreenStyle.STILL_FADE : style;
        }

        /** The duration actually played: Reduced motion caps it at {@link AnimConfig#REDUCED_MAX_MS}. */
        public int effMs() {
            int d = durationMs;
            return AnimConfigManager.get().reducedMotion ? Math.min(d, REDUCED_MAX_MS) : d;
        }

        /** The easing actually played: Reduced motion replaces the overshoot curves. */
        public Easing effEasing() {
            Easing e = easing;
            return (AnimConfigManager.get().reducedMotion && e.overshoots()) ? Easing.EASE_OUT : e;
        }
    }

    /** Replace any null/invalid fields after a (possibly partial or hand-edited) JSON load. */
    public void normalize() {
        if (hotbar == null) hotbar = new Feature(true, 150, Easing.EASE_OUT);
        if (listScroll == null) listScroll = new Feature(true, 150, Easing.EASE_OUT);
        if (chat == null) chat = new Feature(true, 220, Easing.EASE_OUT);
        if (items == null) items = new Feature(true, 200, Easing.EASE_OUT);
        if (creativeScroll == null) creativeScroll = new Feature(true, 220, Easing.EASE_OUT);
        if (recipeBook == null) recipeBook = new Feature(true, 220, Easing.EASE_OUT);
        if (hoverFade == null) hoverFade = new Feature(true, 90, Easing.EASE_OUT);
        if (inventoryOpen == null) inventoryOpen = new ScreenFeature(true, 220, Easing.EASE_OUT, ScreenStyle.SCALE);
        if (inventoryClose == null) inventoryClose = new ScreenFeature(true, 220, Easing.EASE_IN_OUT, ScreenStyle.SLIDE_UP);
        if (menuOpen == null) menuOpen = new ScreenFeature(true, 200, Easing.EASE_OUT, ScreenStyle.FADE);
        if (menuClose == null) menuClose = new ScreenFeature(true, 200, Easing.EASE_IN_OUT, ScreenStyle.FADE);
        for (Feature f : new Feature[]{hotbar, listScroll, chat, items, creativeScroll, recipeBook, hoverFade,
                inventoryOpen, inventoryClose, menuOpen, menuClose}) {
            if (f.easing == null) f.easing = Easing.EASE_OUT;
            f.durationMs = Math.max(20, Math.min(2000, f.durationMs));
        }
        // transient flags are lost across a Gson round trip, so re-assert which features are movement-based
        for (Feature f : new Feature[]{hotbar, listScroll, chat, items, creativeScroll, recipeBook}) {
            f.motion = true;
        }
        for (ScreenFeature sf : new ScreenFeature[]{inventoryOpen, inventoryClose, menuOpen, menuClose}) {
            if (sf.style == null) sf.style = ScreenStyle.SCALE;
        }
        if (menuTransition == null) menuTransition = MenuTransition.SLIDE;
        if (deniedScreens == null) deniedScreens = new ArrayList<>();
        if (knownScreens == null) knownScreens = new ArrayList<>();
    }
}
