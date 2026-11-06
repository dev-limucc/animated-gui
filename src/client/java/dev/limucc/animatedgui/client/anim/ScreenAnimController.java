package dev.limucc.animatedgui.client.anim;

import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;

import java.util.HashMap;
// MenuTransition is in this same package (dev.limucc.animatedgui.client.anim) — no import needed.

/**
 * Drives screen open/close animations and, crucially, the <em>deferred close</em>: when the user closes an
 * inventory or the pause menu, vanilla swaps the screen out instantly. We instead keep the outgoing screen
 * alive for a few frames so it can animate away, then perform the real close.
 *
 * <p>All state is global because Minecraft only ever has one active screen at a time. Everything here is
 * allocation-free per frame; time comes from the monotonic {@link Util#getMillis()}.
 */
public final class ScreenAnimController {

    private ScreenAnimController() {}

    /**
     * Longest slice of real time one rendered frame may advance an open animation by. A heavy first frame (the
     * creative inventory builds ~45 item models) would otherwise swallow most of the animation in a single hitch.
     */
    private static final long MAX_FRAME_STEP_MS = 66L;

    private static Screen openScreen;
    /** False until the open screen has rendered its first frame — that frame is always progress 0. */
    private static boolean openStarted;
    /** Open-animation clock: real time accumulated frame by frame, each step capped at {@link #MAX_FRAME_STEP_MS}. */
    private static long openElapsedMs;
    private static long openLastFrameMs;

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
     * also carry the 3D entity render (the inventory player model) along with the transform — it's a
     * picture-in-picture that otherwise ignores the 2D pose.
     */
    public static boolean transformActive;
    /**
     * Global opacity multiplier (0..1) applied to the closing/opening menu's draws while a FADE-style transition
     * is on the stack. 1.0 = fully opaque (no fade). Read by the graphics mixins to tint every fill / blit / text /
     * item / player model.
     */
    public static float fadeAlpha = 1.0f;

    /** Scratch matrix the render mixin builds each frame's screen transform in (no per-frame allocation). */
    public static final Matrix3x2f scratch = new Matrix3x2f();

    // ── mouse compensation: the transform currently drawn on screen {@link #compScreen} ──────────────────────
    private static Screen compScreen;
    private static float compSx = 1.0f, compSy = 1.0f, compTx, compTy;

    // ── per-screen deny list, answered per class and cached ─────────────────────────────────────────────────
    private static final HashMap<Class<?>, Boolean> deniedCache = new HashMap<>();
    private static int deniedCacheVersion = -1;

    /** True when {@code s}'s class is on the user's deny list (fully-qualified or simple name). */
    public static boolean isDenied(Screen s) {
        if (s == null) return false;
        if (AnimConfigManager.get().deniedScreens.isEmpty()) return false;
        if (deniedCacheVersion != AnimConfigManager.denyVersion()) {
            deniedCache.clear();
            deniedCacheVersion = AnimConfigManager.denyVersion();
        }
        Class<?> c = s.getClass();
        Boolean b = deniedCache.get(c);
        if (b == null) {
            b = AnimConfigManager.isDenied(c);
            deniedCache.put(c, b);
        }
        return b;
    }

    /** True when the screen that is currently up is on the deny list (used by features that have no screen handle). */
    public static boolean deniedCurrent() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && isDenied(mc.gui.screen());
    }

    /** Screens we leave alone — the chat input bar shouldn't scale/slide, and anything the user denied. */
    public static boolean animatable(Screen s) {
        return s != null && !(s instanceof ChatScreen) && !isDenied(s);
    }

    /** From {@code Gui.setScreen} TAIL: a screen just became active — begin its open animation. */
    public static void onScreenAdded(Screen s) {
        if (performingRealClose) return;
        // Idempotent: a screen that installs its replacement from inside its own init() (the survival inventory
        // handing off to the creative one) makes the outer setScreen() finish with the replacement already
        // current. Don't restart the clock of a screen that is already animating in.
        if (s != null && s == openScreen) return;
        compScreen = null;
        if (!animatable(s)) {
            openScreen = null; openTransition = null; openTransitionScreen = null;
            return;
        }
        if (!isTransient(s)) AnimConfigManager.noteScreen(s.getClass());
        openScreen = s;
        // The clock starts at the first frame this screen actually renders (see beginFrame), not here: init()
        // and the first render can hitch, and a hitch must not eat the beginning of the animation.
        openStarted = false;
        openElapsedMs = 0L;
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
     * Once per rendered frame of {@code s} (from the render mixin, before it reads any progress): advances the open
     * clock. The first call only arms the clock, so the first rendered frame is exactly progress 0 and a mispositioned
     * first frame is never visible.
     */
    public static void beginFrame(Screen s) {
        if (s != openScreen) return;
        long now = Util.getMillis();
        if (!openStarted) {
            openStarted = true;
            openElapsedMs = 0L;
        } else {
            openElapsedMs += Math.min(Math.max(0L, now - openLastFrameMs), MAX_FRAME_STEP_MS);
        }
        openLastFrameMs = now;
    }

    /**
     * From {@code Gui.setScreen} HEAD. Returns true if we intercepted the switch (caller must cancel it);
     * the outgoing screen stays up to animate out, then {@link #tick} performs the real switch to {@code next}.
     * Covers both closing back to gameplay ({@code next == null}) and menu→menu navigation (e.g. a sub-screen's
     * Back button returning to the title). Loading / connection / teardown screens pass straight through.
     */
    public static boolean interceptClose(Minecraft mc, Screen current, Screen next) {
        if (performingRealClose) { performingRealClose = false; return false; }
        // {@code current} is assigned before its init() runs, but openScreen only once the whole setScreen() has
        // finished. So a current screen that isn't openScreen is still inside its own setScreen()/init() and was
        // never shown: replacing it is a hand-off, not a close. (The survival InventoryScreen does exactly this
        // when you are in creative — it swaps itself for the CreativeModeInventoryScreen from init(). Deferring
        // that left the un-initialised survival screen drawn at (0,0) for two ticks: the "top-left pop".)
        if (current != null && current != openScreen) return false;
        if (closing) {
            // Already animating one out. A container's onClose() issues setScreen(null) twice in a row
            // (player.closeContainer() and then Screen.onClose()); that second call is the same request and must
            // not cut the exit animation short, so swallow it. A different target (death screen, disconnect, a
            // new menu) still goes straight through.
            return next == pendingNext && current == closingScreen;
        }
        if (!closeConfig(current).on()) return false;
        if (!animatable(current) || next == current) return false;
        if (isTransient(current) || isTransient(next)) return false; // don't hold/delay system screens
        // Closing to gameplay: don't animate during death/respawn.
        if (next == null && mc.level != null && (mc.player == null || mc.player.isDeadOrDying())) return false;

        boolean menuNav = next != null && animatable(next) && !isTransient(next)
                && !(current instanceof AbstractContainerScreen) && !(next instanceof AbstractContainerScreen);
        MenuTransition tr = AnimConfigManager.get().effectiveMenuTransition();
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
        if (Util.getMillis() - closeStartMs >= cfg.effMs()) {
            Screen next = pendingNext;
            closing = false;
            closingScreen = null;
            pendingNext = null;
            menuToMenu = false;
            openScreen = null;
            compScreen = null;
            performingRealClose = true;
            mc.gui.setScreen(next); // → onScreenAdded picks up pendingEnterTransition for the new menu's enter
        }
    }

    /** The transition the outgoing menu {@code s} should EXIT with during a menu→menu nav, else null. */
    public static MenuTransition exitTransition(Screen s) {
        return (menuToMenu && closing && s == closingScreen) ? AnimConfigManager.get().effectiveMenuTransition() : null;
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
        if (isClosing(s)) {
            AnimConfig.ScreenFeature cfg = configFor(s);
            float p = 1.0f - (Util.getMillis() - closeStartMs) / (float) Math.max(1, cfg.effMs());
            return clamp01(p);
        }
        if (s == openScreen) {
            AnimConfig.ScreenFeature cfg = configFor(s);
            if (!cfg.on()) return 1.0f;
            if (!openStarted) return 0.0f;
            return clamp01(openElapsedMs / (float) Math.max(1, cfg.effMs()));
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

    // ── mouse compensation ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Publish the open/close transform {@code m} that {@code s} is being drawn with this frame. Mouse coordinates
     * are mapped back through its inverse (see {@link #compX}/{@link #compY}) so hover highlights, tooltips-to-be
     * and clicks all land on what is actually on screen rather than on where the widgets would sit un-animated.
     */
    public static void publishTransform(Screen s, Matrix3x2f m) {
        compScreen = s;
        compSx = Math.max(0.01f, m.m00);
        compSy = Math.max(0.01f, m.m11);
        compTx = m.m20;
        compTy = m.m21;
    }

    /** The screen finished animating (or isn't): input is un-mapped again. */
    public static void clearTransform() {
        compScreen = null;
    }

    /** True while mouse input is being mapped through an animation transform. */
    public static boolean compensating() {
        if (compScreen == null) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.gui.screen() == compScreen;
    }

    public static double compX(double x) {
        return (x - compTx) / compSx;
    }

    public static double compY(double y) {
        return (y - compTy) / compSy;
    }

    /** Forward map of {@link #compX}: where an un-animated x coordinate is drawn right now. */
    public static double drawnX(double localX) {
        return localX * compSx + compTx;
    }

    /** Forward map of {@link #compY}. */
    public static double drawnY(double localY) {
        return localY * compSy + compTy;
    }
}
