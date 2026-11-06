package dev.limucc.animatedgui.client.compat;

import dev.limucc.animatedgui.AnimatedGui;
import dev.limucc.animatedgui.client.config.AnimConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Detects other mods that already animate the same thing and makes the overlapping Animated GUI feature step
 * aside (never the whole mod): installing Smooth Swapping turns off our item glide, installing Smooth Scrolling
 * (either flavour) turns off our list scroll — and the creative-grid slide too for the original Smooth Scrolling,
 * which owns that grid. The settings screen shows which feature yielded to which mod. Pure runtime detection:
 * no dependency on any of them.
 */
public final class ModCompat {

    private ModCompat() {}

    /** Fabric mod ids. */
    private static final String SMOOTH_SWAPPING = "smoothswapping";
    private static final String SMOOTH_SCROLLING = "smoothscroll";
    private static final String SMOOTH_SCROLLING_REFURBISHED = "smoothscrollingrefurbished";

    public static void apply(AnimConfig cfg) {
        FabricLoader loader = FabricLoader.getInstance();
        cfg.items.yieldedTo = loader.isModLoaded(SMOOTH_SWAPPING) ? "Smooth Swapping" : null;

        String scrolling = null;
        if (loader.isModLoaded(SMOOTH_SCROLLING)) scrolling = "Smooth Scrolling";
        else if (loader.isModLoaded(SMOOTH_SCROLLING_REFURBISHED)) scrolling = "Smooth Scrolling Refurbished";
        cfg.listScroll.yieldedTo = scrolling;
        cfg.creativeScroll.yieldedTo = loader.isModLoaded(SMOOTH_SCROLLING) ? "Smooth Scrolling" : null;

        if (cfg.items.yieldedTo != null || scrolling != null) {
            AnimatedGui.LOGGER.info("[Animated GUI] Yielding to installed mods: items={}, listScroll={}, creativeScroll={}",
                    cfg.items.yieldedTo, cfg.listScroll.yieldedTo, cfg.creativeScroll.yieldedTo);
        }
    }
}
