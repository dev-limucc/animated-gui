package dev.limucc.animatedgui;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. Animated GUI is a client-only mod (all of its work happens on the render thread),
 * so this does almost nothing — the real wiring lives in
 * {@link dev.limucc.animatedgui.client.AnimatedGuiClient}.
 */
public class AnimatedGui implements ModInitializer {

    public static final String MOD_ID = "animatedgui";
    public static final Logger LOGGER = LoggerFactory.getLogger("Animated GUI");

    @Override
    public void onInitialize() {
        LOGGER.info("Animated GUI loaded — smoothing out Minecraft's instant menus.");
    }
}
