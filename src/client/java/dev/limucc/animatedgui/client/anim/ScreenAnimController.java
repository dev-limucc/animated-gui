package dev.limucc.animatedgui.client.anim;

import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

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
    /**
     * True only while a screen's open/close pose transform is on the stack. The graphics mixin reads this to
     * also drag the 3D entity render (the inventory player model) along with the transform — it's a
     * picture-in-picture that otherwise ignores the 2D pose.
     */
    public static boolean transformActive;

    /** Screens we leave alone — the chat input bar shouldn't scale/slide. */
    public static boolean animatable(Screen s) {
        return s != null && !(s instanceof ChatScreen);
    }

    /** From {@code Screen.added()}: a screen just became active — begin its open animation. */
    public static void onScreenAdded(Screen s) {
        if (performingRealClose) return;
        if (!animatable(s)) { openScreen = null; return; }
        openScreen = s;
        openStartMs = Util.getMillis();
        closing = false;
        closingScreen = null;
    }

    /**
     * From {@code Minecraft.setScreen} HEAD. Returns true if we intercepted the switch (caller must cancel it);
     * the outgoing screen stays up to animate out, then {@link #tick} performs the real switch to {@code next}.
     * Covers both closing back to gameplay ({@code next == null}) and menu→menu navigation (e.g. a sub-screen's
     * Back button returning to the title). Loading / connection / teardown screens pass straight through.
     */
    public static boolean interceptClose(Minecraft mc, Screen current, Screen next) {
        if (performingRealClose) { performingRealClose = false; return false; }
        AnimConfig.ScreenFeature cfg = AnimConfigManager.get().screenClose;
        if (!cfg.on()) return false;
        if (closing) return false;                            // already animating one out
        if (!animatable(current) || next == current) return false;
        if (isTransient(current) || isTransient(next)) return false; // don't hold/delay system screens
        // Closing to gameplay: don't animate during death/respawn.
        if (next == null && mc.level != null && (mc.player == null || mc.player.isDeadOrDying())) return false;
        pendingNext = next;
        closing = true;
        closingScreen = current;
        closeStartMs = Util.getMillis();
        return true;
    }

    /** Per client tick: when the close animation has elapsed, perform the real switch to the pending screen. */
    public static void tick(Minecraft mc) {
        if (!closing) return;
        if (mc.screen != closingScreen) { closing = false; closingScreen = null; pendingNext = null; return; }
        AnimConfig.ScreenFeature cfg = AnimConfigManager.get().screenClose;
        if (Util.getMillis() - closeStartMs >= cfg.durationMs) {
            Screen next = pendingNext;
            closing = false;
            closingScreen = null;
            pendingNext = null;
            openScreen = null;
            performingRealClose = true;
            mc.setScreen(next);
        }
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
            AnimConfig.ScreenFeature cfg = AnimConfigManager.get().screenClose;
            float p = 1.0f - (now - closeStartMs) / (float) Math.max(1, cfg.durationMs);
            return clamp01(p);
        }
        if (s == openScreen) {
            AnimConfig.ScreenFeature cfg = AnimConfigManager.get().screenOpen;
            if (!cfg.on()) return 1.0f;
            float p = (now - openStartMs) / (float) Math.max(1, cfg.durationMs);
            return clamp01(p);
        }
        return 1.0f;
    }

    /** Which feature config governs {@code s} right now (close while closing, otherwise open). */
    public static AnimConfig.ScreenFeature configFor(Screen s) {
        return isClosing(s) ? AnimConfigManager.get().screenClose : AnimConfigManager.get().screenOpen;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
