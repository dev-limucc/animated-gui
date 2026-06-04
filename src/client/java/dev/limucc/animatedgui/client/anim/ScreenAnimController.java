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

    /** Set just before we re-issue the real {@code setScreen(null)} so the mixin lets it through. */
    private static boolean performingRealClose;

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
     * From {@code Minecraft.setScreen} HEAD. Returns true if we intercepted a close (caller must cancel the
     * vanilla switch); the outgoing screen stays up and animates out, and {@link #tick} finishes the job.
     */
    public static boolean interceptClose(Minecraft mc, Screen current, Screen next) {
        if (performingRealClose) { performingRealClose = false; return false; }
        AnimConfig.ScreenFeature cfg = AnimConfigManager.get().screenClose;
        if (!cfg.on()) return false;
        if (next != null) return false;                       // only animate closing back to gameplay
        if (!animatable(current)) return false;
        if (mc.level == null) return false;                   // going to title/disconnect — don't hold it
        if (mc.player == null || mc.player.isDeadOrDying()) return false;
        if (closing) return false;                            // already animating out
        closing = true;
        closingScreen = current;
        closeStartMs = Util.getMillis();
        return true;
    }

    /** Per client tick: when the close animation has elapsed, actually close. */
    public static void tick(Minecraft mc) {
        if (!closing) return;
        if (mc.screen != closingScreen) { closing = false; closingScreen = null; return; }
        AnimConfig.ScreenFeature cfg = AnimConfigManager.get().screenClose;
        if (Util.getMillis() - closeStartMs >= cfg.durationMs) {
            closing = false;
            closingScreen = null;
            openScreen = null;
            performingRealClose = true;
            mc.setScreen(null);
        }
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
