package dev.limucc.animatedgui.client.anim;

import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
// MenuTransition is in this same package (dev.limucc.animatedgui.client.anim) — no import needed.

/**
 * Drives screen open/close animations and, crucially, the <em>deferred close</em>: when the user closes an
 * inventory or the pause menu, vanilla swaps the screen out instantly. We instead keep the outgoing screen
 * alive for a few frames so it can animate away, then perform the real close.
 *
 * <p>All state is global because Minecraft only ever has one active screen at a time.
 */
public final class ScreenAnimController {

    private ScreenAnimController() {}

    private static long openStartMs;
    private static Screen openScreen;

    private static boolean closing;
    private static Screen closingScreen;
    private static long closeStartMs;

    /** Set just before we re-issue the real {@code setScreen(...)} so the mixin lets it through. */
    private static boolean performingRealClose;
    /** The screen to switch to once the outgoing screen has finished animating away (may be null = gameplay). */
    private static Screen pendingNext;
    /** This deferred close is a menu→menu navigation (drives which transition the outgoing menu uses to exit). */
    private static boolean menuToMenu;
    /** Carried across the swap: the transition whose <em>enter</em> motion the incoming menu should play in with. */
    private static MenuTransition pendingEnterTransition;
    /** While set, this freshly-opened menu animates in with {@link #openTransition}'s enter motion. */
    private static MenuTransition openTransition;
    private static Screen openTransitionScreen;
    /**
     * True only while a screen's open/close pose transform is on the stack. The graphics mixin reads this to
     * also drag the 3D entity render (the inventory player model) along with the transform — it's a
     * picture-in-picture that otherwise ignores the 2D pose.
     */
    public static boolean transformActive;
    /**
     * Global opacity multiplier (0..1) applied to the closing/opening menu's draws while a FADE-style transition
     * is on the stack. 1.0 = fully opaque (no fade). Read by the graphics mixin to tint every fill/blit/text.
     */
    public static float fadeAlpha = 1.0f;

    /** Screens we leave alone — the chat input bar shouldn't scale/slide. */
    public static boolean animatable(Screen s) {
        return s != null && !(s instanceof ChatScreen);
    }

    /** From {@code Minecraft.setScreen}: a screen just became active — begin its open animation. */
    public static void onScreenAdded(Screen s) {
        if (performingRealClose) return;
        if (!animatable(s)) { openScreen = null; openTransition = null; openTransitionScreen = null; return; }
        openScreen = s;
        openStartMs = Util.getMillis();
        closing = false;
        closingScreen = null;
        // Menu→menu navigation: the incoming menu plays the transition's enter motion instead of its open style.
        if (pendingEnterTransition != null) {
            openTransition = pendingEnterTransition;
            openTransitionScreen = s;
            pendingEnterTransition = null;
        } else {
            openTransition = null;
            openTransitionScreen = null;
        }
    }

    /**
     * From {@code Minecraft.setScreen} HEAD. Returns true if we intercepted the switch (caller must cancel it);
     * the outgoing screen stays up to animate out, then {@link #tick} performs the real switch to {@code next}.
     * Covers both closing back to gameplay ({@code next == null}) and menu→menu navigation (e.g. a sub-screen's
     * Back button returning to the title). Loading / connection / teardown screens pass straight through.
     */
    public static boolean interceptClose(Minecraft mc, Screen current, Screen next) {
        if (performingRealClose) { performingRealClose = false; return false; }
        if (!closeConfig(current).on()) return false;
        if (closing) return false;                            // already animating one out
        if (!animatable(current) || next == current) return false;
        if (isTransient(current) || isTransient(next)) return false; // don't hold/delay system screens
        // Closing to gameplay: don't animate during death/respawn.
        if (next == null && mc.level != null && (mc.player == null || mc.player.isDeadOrDying())) return false;

        boolean menuNav = next != null && animatable(next) && !isTransient(next)
                && !(current instanceof AbstractContainerScreen) && !(next instanceof AbstractContainerScreen);
        MenuTransition tr = AnimConfigManager.get().menuTransition;
        if (menuNav && !tr.deferOld()) {
            // SWAP: don't hold the old menu — let setScreen go through, but still play the new menu's enter.
            pendingEnterTransition = tr;
            return false;
        }

        pendingNext = next;
        closing = true;
        closingScreen = current;
        closeStartMs = Util.getMillis();
        menuToMenu = menuNav;
        // The incoming menu (drawn only after the swap) will play this transition's enter motion.
        pendingEnterTransition = menuNav ? tr : null;
        return true;
    }

    /** Per client tick: when the close animation has elapsed, perform the real switch to the pending screen. */
    public static void tick(Minecraft mc) {
        if (!closing) return;
        if (mc.gui.screen() != closingScreen) { closing = false; closingScreen = null; pendingNext = null; return; }
        AnimConfig.ScreenFeature cfg = closeConfig(closingScreen);
        if (Util.getMillis() - closeStartMs >= cfg.durationMs) {
            Screen next = pendingNext;
            closing = false;
            closingScreen = null;
            pendingNext = null;
            menuToMenu = false;
            openScreen = null;
            performingRealClose = true;
            mc.gui.setScreen(next); // → onScreenAdded picks up pendingEnterTransition for the new menu's enter
        }
    }

    /** The transition the outgoing menu {@code s} should EXIT with during a menu→menu nav, else null. */
    public static MenuTransition exitTransition(Screen s) {
        return (menuToMenu && closing && s == closingScreen) ? AnimConfigManager.get().menuTransition : null;
    }

    /** The transition the freshly-opened menu {@code s} should ENTER with, else null. */
    public static MenuTransition enterTransition(Screen s) {
        return (openTransition != null && s == openTransitionScreen && s == openScreen) ? openTransition : null;
    }

    /** Loading / connection / world-teardown screens we must not hold up or render over. */
    private static boolean isTransient(Screen s) {
        if (s == null) return false;
        String n = s.getClass().getSimpleName();
        return n.contains("Connect") || n.contains("Progress") || n.contains("Receiving")
                || n.contains("Loading") || n.contains("Downloading") || n.contains("Reconnect")
                || n.contains("GenericMessage") || n.contains("Disconnect");
    }

    public static boolean isClosing(Screen s) {
        return closing && s == closingScreen;
    }

    /**
     * Raw (un-eased) progress for {@code s}: opening runs 0→1, closing runs 1→0, everything else is 1
     * (fully shown, no transform). The render mixin applies the easing curve on top of this.
     */
    public static float rawProgress(Screen s) {
        long now = Util.getMillis();
        if (isClosing(s)) {
            AnimConfig.ScreenFeature cfg = configFor(s);
            float p = 1.0f - (now - closeStartMs) / (float) Math.max(1, cfg.durationMs);
            return clamp01(p);
        }
        if (s == openScreen) {
            AnimConfig.ScreenFeature cfg = configFor(s);
            if (!cfg.on()) return 1.0f;
            float p = (now - openStartMs) / (float) Math.max(1, cfg.durationMs);
            return clamp01(p);
        }
        return 1.0f;
    }

    /**
     * Which feature config governs {@code s} right now. Container screens (inventories, chests, creative) use
     * the Inventory open/close settings; everything else (pause, options, title…) uses the Game-menu settings.
     */
    public static AnimConfig.ScreenFeature configFor(Screen s) {
        AnimConfig c = AnimConfigManager.get();
        boolean inv = s instanceof AbstractContainerScreen;
        if (isClosing(s)) return inv ? c.inventoryClose : c.menuClose;
        return inv ? c.inventoryOpen : c.menuOpen;
    }

    private static AnimConfig.ScreenFeature closeConfig(Screen s) {
        AnimConfig c = AnimConfigManager.get();
        return (s instanceof AbstractContainerScreen) ? c.inventoryClose : c.menuClose;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
