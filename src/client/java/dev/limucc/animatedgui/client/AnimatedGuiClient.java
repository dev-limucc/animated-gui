package dev.limucc.animatedgui.client;

import dev.limucc.animatedgui.AnimatedGui;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client entrypoint: loads the config and drives the one piece of logic that can't live purely in a render
 * mixin — finishing a deferred screen close once its exit animation has played out. Everything else hooks in
 * through the mixins. Settings are reached via ModMenu ({@link ModMenuIntegration}).
 */
public class AnimatedGuiClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AnimConfigManager.load();
        ClientTickEvents.END_CLIENT_TICK.register(ScreenAnimController::tick);
        AnimatedGui.LOGGER.info("Animated GUI loaded — smoothing out Minecraft's instant menus.");
        for (String t : new String[]{
                "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen",
                "net.minecraft.client.gui.screens.inventory.InventoryScreen",
                "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen",
                "net.minecraft.client.gui.components.AbstractSelectionList",
                "net.minecraft.client.gui.screens.recipebook.RecipeBookComponent",
                "net.minecraft.client.gui.screens.recipebook.RecipeBookPage",
                "net.minecraft.client.gui.GuiGraphicsExtractor"}) {
            try { Class.forName(t, false, getClass().getClassLoader()); AnimatedGui.LOGGER.info("[validate] {} OK", t); }
            catch (Throwable e) { AnimatedGui.LOGGER.error("[validate] {} FAILED", t, e); }
        }
    }
}
