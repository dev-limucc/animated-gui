package dev.limucc.animatedgui.client.config;

import dev.limucc.animatedgui.client.anim.Easing;
import dev.limucc.animatedgui.client.anim.MenuTransition;
import dev.limucc.animatedgui.client.anim.ScreenStyle;

/**
 * Every knob the user can turn, serialized to JSON as-is. Grouped to match the settings screen tabs:
 * General (master + hotbar), Chat, Inventory (slots / creative scroll / recipe book / inventory open+close)
 * and Game menu (menu open+close). One master switch freezes everything back to vanilla.
 */
public class AnimConfig {

    public boolean masterEnabled = true;

    // ── General / HUD ──────────────────────────────────────────────────────────
    public Feature hotbar = new Feature(true, 150, Easing.EASE_OUT);
    public boolean hotbarTrail = false;
    /** Smooth (eased) scrolling for every vanilla-list screen — options, Mod Menu, Cloth Config, mod menus… */
    public Feature listScroll = new Feature(true, 150, Easing.EASE_OUT);

    // ── Chat ───────────────────────────────────────────────────────────────────
    public Feature chat = new Feature(true, 220, Easing.EASE_OUT);

    // ── Inventory ───────────────────────────────────────────────────────────────
    public Feature items = new Feature(true, 200, Easing.EASE_OUT);          // item glide between slots
    public Feature creativeScroll = new Feature(true, 220, Easing.EASE_OUT);
    public Feature recipeBook = new Feature(true, 220, Easing.EASE_OUT);     // book slide + inventory shift + paging
    public ScreenFeature inventoryOpen = new ScreenFeature(true, 220, Easing.EASE_OUT, ScreenStyle.SCALE);
    public ScreenFeature inventoryClose = new ScreenFeature(true, 220, Easing.EASE_IN_OUT, ScreenStyle.SLIDE_UP);

    // ── Game menu ───────────────────────────────────────────────────────────────
    public ScreenFeature menuOpen = new ScreenFeature(true, 200, Easing.EASE_OUT, ScreenStyle.FADE);
    public ScreenFeature menuClose = new ScreenFeature(true, 200, Easing.EASE_IN_OUT, ScreenStyle.FADE);
    /** How navigating between two menus animates (Pause→Options, a sub-screen's Back, …). */
    public MenuTransition menuTransition = MenuTransition.SLIDE;

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
        if (hotbar == null) hotbar = new Feature(true, 150, Easing.EASE_OUT);
        if (listScroll == null) listScroll = new Feature(true, 150, Easing.EASE_OUT);
        if (chat == null) chat = new Feature(true, 220, Easing.EASE_OUT);
        if (items == null) items = new Feature(true, 200, Easing.EASE_OUT);
        if (creativeScroll == null) creativeScroll = new Feature(true, 220, Easing.EASE_OUT);
        if (recipeBook == null) recipeBook = new Feature(true, 220, Easing.EASE_OUT);
        if (inventoryOpen == null) inventoryOpen = new ScreenFeature(true, 220, Easing.EASE_OUT, ScreenStyle.SCALE);
        if (inventoryClose == null) inventoryClose = new ScreenFeature(true, 220, Easing.EASE_IN_OUT, ScreenStyle.SLIDE_UP);
        if (menuOpen == null) menuOpen = new ScreenFeature(true, 200, Easing.EASE_OUT, ScreenStyle.FADE);
        if (menuClose == null) menuClose = new ScreenFeature(true, 200, Easing.EASE_IN_OUT, ScreenStyle.FADE);
        for (Feature f : new Feature[]{hotbar, listScroll, chat, items, creativeScroll, recipeBook,
                inventoryOpen, inventoryClose, menuOpen, menuClose}) {
            if (f.easing == null) f.easing = Easing.EASE_OUT;
            f.durationMs = Math.max(20, Math.min(2000, f.durationMs));
        }
        for (ScreenFeature sf : new ScreenFeature[]{inventoryOpen, inventoryClose, menuOpen, menuClose}) {
            if (sf.style == null) sf.style = ScreenStyle.SCALE;
        }
        if (menuTransition == null) menuTransition = MenuTransition.SLIDE;
    }
}
